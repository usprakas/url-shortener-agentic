package com.schwab.orchestrator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schwab.orchestrator.model.AuditEvent;
import com.schwab.orchestrator.model.GateResult;
import com.schwab.orchestrator.model.RetryPolicy;
import com.schwab.orchestrator.model.RunBudget;
import com.schwab.orchestrator.model.RunSnapshot;
import com.schwab.orchestrator.model.RunStatus;
import com.schwab.orchestrator.model.StageAction;
import com.schwab.orchestrator.model.StageDefinition;
import com.schwab.orchestrator.model.StageGate;
import com.schwab.orchestrator.model.StageStatus;
import com.schwab.orchestrator.model.WorkflowPlan;
import com.schwab.orchestrator.persistence.AuditStore;
import com.schwab.orchestrator.persistence.FileAuditStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OrchestrationEngineTest {

  @Test
  void executesDependenciesAndPersistsAuditTrail() throws Exception {
    Path auditDirectory = Files.createTempDirectory("audit");
    FileAuditStore audit = new FileAuditStore(auditDirectory);
    StageDefinition requirements = stage("requirements", Set.of(), context -> Map.of("requirement", "hash-a"));
    StageDefinition design = stage("design", Set.of("requirements"), context -> Map.of("design", "hash-b"));

    RunSnapshot run = engine(audit, approveAll()).execute("greenfield-1", new WorkflowPlan(List.of(requirements, design)), Map.of());

    assertEquals(RunStatus.COMPLETED, run.status());
    assertEquals(StageStatus.SUCCEEDED, run.stage("design"));
    assertTrue(Files.readString(auditDirectory.resolve("greenfield-1.events.jsonl")).contains("RUN_COMPLETED"));
  }

  @Test
  void executesIndependentBranchesInParallelBeforeTheirJoin() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    AtomicInteger active = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();
    StageAction branchAction = context -> {
      int now = active.incrementAndGet();
      peak.accumulateAndGet(now, Math::max);
      Thread.sleep(40);
      active.decrementAndGet();
      return Map.of(context.runId() + UUID.randomUUID(), "done");
    };
    StageDefinition implementation = stage("implementation", Set.of(), branchAction);
    StageDefinition tests = stage("tests", Set.of(), branchAction);
    StageDefinition join = stage("release", Set.of("implementation", "tests"), context -> Map.of("release", "ready"));

    RunSnapshot run = engine(audit, approveAll()).execute("parallel-1", new WorkflowPlan(List.of(implementation, tests, join)), Map.of());

    assertEquals(RunStatus.COMPLETED, run.status());
    assertEquals(2, peak.get());
    assertEquals(StageStatus.SUCCEEDED, run.stage("release"));
  }

  @Test
  void retriesThenRollsBackAfterRetryExhaustion() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    AtomicInteger attempts = new AtomicInteger();
    AtomicInteger rollbacks = new AtomicInteger();
    StageDefinition failing = new StageDefinition("implementation", Set.of("design"), "implementer", false,
        new RetryPolicy(2, Duration.ZERO), null, null,
        context -> { attempts.incrementAndGet(); throw new IllegalStateException("compiler failure"); }, context -> { });
    StageDefinition rollbackAware = new StageDefinition("design", Set.of(), "architect", false, RetryPolicy.none(),
        null, null, context -> Map.of("design", "ok"), context -> rollbacks.incrementAndGet());

    RunSnapshot run = engine(audit, approveAll()).execute("failure-1", new WorkflowPlan(List.of(rollbackAware, failing)), Map.of());

    assertEquals(2, attempts.get());
    assertEquals(1, rollbacks.get());
    assertEquals(RunStatus.ROLLED_BACK, run.status());
    assertTrue(audit.events("failure-1").stream().anyMatch(event -> event.eventType().equals("RETRY_SCHEDULED")));
  }

  @Test
  void stopsBeforeStartingStageWhenKillSwitchIsEngaged() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    KillSwitch killSwitch = new KillSwitch();
    killSwitch.engage();
    OrchestrationEngine engine = new OrchestrationEngine(audit, approveAll(), killSwitch);

    RunSnapshot run = engine.execute("stopped-1", new WorkflowPlan(List.of(stage("requirements", Set.of(), context -> Map.of()))), Map.of());

    assertEquals(RunStatus.SAFE_STOPPED, run.status());
    assertEquals(StageStatus.PENDING, run.stage("requirements"));
  }

  @Test
  void rejectedApprovalPreventsProtectedStage() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    StageDefinition release = new StageDefinition("release", Set.of(), "release-manager", true, RetryPolicy.none(),
        null, null, context -> Map.of(), null);

    RunSnapshot run = engine(audit, (id, requested) -> ApprovalProvider.ApprovalDecision.reject("release window closed"))
        .execute("approval-1", new WorkflowPlan(List.of(release)), Map.of());

    assertEquals(RunStatus.FAILED, run.status());
    assertEquals(StageStatus.FAILED, run.stage("release"));
    assertTrue(audit.events("approval-1").stream().anyMatch(event -> event.eventType().equals("APPROVAL_REJECTED")));
  }

  @Test
  void stageTimesOutAndRetriesThenFails() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    StageDefinition slow = new StageDefinition("implementation", Set.of(), "implementer", false,
        new RetryPolicy(2, Duration.ZERO), null, null, context -> { Thread.sleep(500); return Map.of(); }, null);

    RunSnapshot run = engine(audit, approveAll()).execute("timeout-1", new WorkflowPlan(List.of(slow)), Map.of(),
        new RunBudget(10, Duration.ofMillis(20)));

    assertEquals(RunStatus.ROLLED_BACK, run.status());
    assertTrue(audit.events("timeout-1").stream().anyMatch(event ->
        event.eventType().equals("STAGE_ATTEMPT_FAILED") && event.rationale().contains("timed out")));
  }

  @Test
  void exceedingTotalAttemptBudgetStopsTheRunEarly() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    StageDefinition first = new StageDefinition("requirements", Set.of(), "agent", false,
        new RetryPolicy(3, Duration.ZERO), null, null, context -> { throw new IllegalStateException("boom"); }, null);
    StageDefinition second = new StageDefinition("design", Set.of(), "agent", false,
        new RetryPolicy(3, Duration.ZERO), null, null, context -> { throw new IllegalStateException("boom"); }, null);

    RunSnapshot run = engine(audit, approveAll()).execute("budget-1", new WorkflowPlan(List.of(first, second)), Map.of(),
        new RunBudget(1, Duration.ofSeconds(5)));

    assertEquals(RunStatus.ROLLED_BACK, run.status());
    assertTrue(audit.events("budget-1").stream().anyMatch(event -> event.eventType().equals("BUDGET_EXCEEDED")));
  }

  @Test
  void rePlanInvalidatesOnlyTheDownstreamSubgraphAndReRequiresApproval() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    AtomicInteger designRuns = new AtomicInteger();
    AtomicInteger approvalCalls = new AtomicInteger();
    StageDefinition requirements = stage("requirements", Set.of(), context -> Map.of("requirements", "v1"));
    StageDefinition design = new StageDefinition("design", Set.of("requirements"), "architect", true, RetryPolicy.none(),
        null, null, context -> { designRuns.incrementAndGet(); return Map.of("design", "v" + designRuns.get()); }, null);
    StageDefinition implementation = stage("implementation", Set.of("design"), context -> Map.of("implementation", "built"));
    ApprovalProvider approvals = (id, requested) -> { approvalCalls.incrementAndGet(); return ApprovalProvider.ApprovalDecision.approve("ok"); };
    WorkflowPlan plan = new WorkflowPlan(List.of(requirements, design, implementation));
    OrchestrationEngine engine = engine(audit, approvals);

    RunSnapshot run = engine.execute("replan-1", plan, Map.of());
    assertEquals(RunStatus.COMPLETED, run.status());
    assertEquals(1, approvalCalls.get());

    RunSnapshot replanned = engine.rePlan(run, plan, "design", "v2-revised");

    assertEquals(RunStatus.COMPLETED, replanned.status());
    assertEquals(1, designRuns.get(), "the revised value is supplied, not regenerated by re-running design's action");
    assertEquals(2, approvalCalls.get(), "the revision must be re-approved even though design did not re-execute");
    assertEquals("v2-revised", replanned.artifacts().get("design"));
    assertEquals(StageStatus.SUCCEEDED, replanned.stage("requirements"));
    assertTrue(audit.events("replan-1").stream().anyMatch(event -> event.eventType().equals("REPLAN_TRIGGERED")));
  }

  @Test
  void rePlanStopsTheRunWhenTheRevisedStagesApprovalIsRejected() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    StageDefinition design = new StageDefinition("design", Set.of(), "architect", true, RetryPolicy.none(),
        null, null, context -> Map.of("design", "v1"), null);
    WorkflowPlan plan = new WorkflowPlan(List.of(design));
    List<Boolean> approvalOutcomes = List.of(true, false);
    AtomicInteger approvalCall = new AtomicInteger();
    ApprovalProvider approvals = (id, requested) -> approvalOutcomes.get(approvalCall.getAndIncrement())
        ? ApprovalProvider.ApprovalDecision.approve("ok")
        : ApprovalProvider.ApprovalDecision.reject("revised design rejected");
    OrchestrationEngine engine = engine(audit, approvals);

    RunSnapshot run = engine.execute("replan-3", plan, Map.of());
    RunSnapshot replanned = engine.rePlan(run, plan, "design", "v2-revised");

    assertEquals(RunStatus.ROLLED_BACK, replanned.status());
    assertTrue(audit.events("replan-3").stream().anyMatch(event -> event.eventType().equals("APPROVAL_REJECTED")));
  }

  @Test
  void rePlanIsANoOpWhenTheArtifactDidNotActuallyChange() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    StageDefinition design = stage("design", Set.of(), context -> Map.of("design", "v1"));
    WorkflowPlan plan = new WorkflowPlan(List.of(design));
    OrchestrationEngine engine = engine(audit, approveAll());

    RunSnapshot run = engine.execute("replan-2", plan, Map.of());
    RunSnapshot replanned = engine.rePlan(run, plan, "design", "v1");

    assertTrue(audit.events("replan-2").stream().anyMatch(event -> event.eventType().equals("REPLAN_SKIPPED")));
    assertEquals(StageStatus.SUCCEEDED, replanned.stage("design"));
  }

  @Test
  void stageGateAllOfShortCircuitsOnFirstDenialAndMergesPolicyResults() {
    StageGate first = context -> new GateResult(true, "ok", Map.of("rule-a", "PASS"));
    StageGate second = context -> new GateResult(false, "blocked", Map.of("rule-b", "DENY"));
    StageGate third = context -> { throw new AssertionError("must not be evaluated after a denial"); };

    GateResult result = StageGate.allOf(first, second, third).evaluate(new com.schwab.orchestrator.model.StageContext("run", Map.of()));

    assertFalse(result.allowed());
    assertEquals("PASS", result.policyResults().get("rule-a"));
    assertEquals("DENY", result.policyResults().get("rule-b"));
  }

  private OrchestrationEngine engine(AuditStore audit, ApprovalProvider approvals) {
    return new OrchestrationEngine(audit, approvals, new KillSwitch());
  }

  private ApprovalProvider approveAll() {
    return (id, requested) -> ApprovalProvider.ApprovalDecision.approve("approved");
  }

  private StageDefinition stage(String id, Set<String> dependencies, StageAction action) {
    return new StageDefinition(id, dependencies, "agent", false, RetryPolicy.none(), null, null, action, null);
  }

  private static final class InMemoryAuditStore implements AuditStore {
    private final Map<String, List<AuditEvent>> values = new HashMap<>();

    @Override
    public void append(AuditEvent event) {
      values.computeIfAbsent(event.runId(), ignored -> new ArrayList<>()).add(event);
    }

    @Override
    public List<AuditEvent> events(String runId) {
      return values.getOrDefault(runId, List.of());
    }
  }
}
