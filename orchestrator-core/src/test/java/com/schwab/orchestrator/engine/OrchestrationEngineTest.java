package com.schwab.orchestrator.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.schwab.orchestrator.model.*;
import com.schwab.orchestrator.persistence.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OrchestrationEngineTest {
  @Test void executesDependenciesAndPersistsAuditTrail() throws Exception {
    Path auditDirectory = Files.createTempDirectory("audit"); FileAuditStore audit = new FileAuditStore(auditDirectory);
    StageDefinition requirements = stage("requirements", Set.of(), context -> Map.of("requirement", "hash-a"));
    StageDefinition design = stage("design", Set.of("requirements"), context -> Map.of("design", "hash-b"));
    RunSnapshot run = engine(audit, (id, stage) -> ApprovalProvider.ApprovalDecision.approve("approved")).execute("greenfield-1", new WorkflowPlan(List.of(requirements, design)), Map.of());
    assertEquals(RunStatus.COMPLETED, run.status()); assertEquals(StageStatus.SUCCEEDED, run.stage("design"));
    assertTrue(Files.readString(auditDirectory.resolve("greenfield-1.events.jsonl")).contains("RUN_COMPLETED"));
  }
  @Test void retriesThenRollsBackAfterRetryExhaustion() {
    InMemoryAuditStore audit = new InMemoryAuditStore(); AtomicInteger attempts = new AtomicInteger(); AtomicInteger rollbacks = new AtomicInteger();
    StageDefinition completed = stage("design", Set.of(), context -> Map.of("design", "ok"));
    StageDefinition failing = new StageDefinition("implementation", Set.of("design"), "implementer", false, new RetryPolicy(2, Duration.ZERO), null, null, context -> { attempts.incrementAndGet(); throw new IllegalStateException("compiler failure"); }, context -> { });
    StageDefinition rollbackAware = new StageDefinition("design", Set.of(), "architect", false, RetryPolicy.none(), null, null, context -> Map.of("design", "ok"), context -> rollbacks.incrementAndGet());
    RunSnapshot run = engine(audit, (id, stage) -> ApprovalProvider.ApprovalDecision.approve("n/a")).execute("failure-1", new WorkflowPlan(List.of(rollbackAware, failing)), Map.of());
    assertEquals(2, attempts.get()); assertEquals(1, rollbacks.get()); assertEquals(RunStatus.ROLLED_BACK, run.status()); assertTrue(audit.events("failure-1").stream().anyMatch(event -> event.eventType().equals("RETRY_SCHEDULED")));
  }
  @Test void stopsBeforeStartingStageWhenKillSwitchIsEngaged() {
    InMemoryAuditStore audit = new InMemoryAuditStore(); KillSwitch killSwitch = new KillSwitch(); killSwitch.engage();
    OrchestrationEngine engine = new OrchestrationEngine(audit, (id, stage) -> ApprovalProvider.ApprovalDecision.approve("n/a"), killSwitch);
    RunSnapshot run = engine.execute("stopped-1", new WorkflowPlan(List.of(stage("requirements", Set.of(), context -> Map.of()))), Map.of());
    assertEquals(RunStatus.SAFE_STOPPED, run.status()); assertEquals(StageStatus.PENDING, run.stage("requirements"));
  }
  @Test void rejectedApprovalPreventsProtectedStage() {
    InMemoryAuditStore audit = new InMemoryAuditStore();
    StageDefinition release = new StageDefinition("release", Set.of(), "release-manager", true, RetryPolicy.none(), null, null, context -> Map.of(), null);
    RunSnapshot run = engine(audit, (id, stage) -> ApprovalProvider.ApprovalDecision.reject("release window closed")).execute("approval-1", new WorkflowPlan(List.of(release)), Map.of());
    assertEquals(RunStatus.FAILED, run.status()); assertEquals(StageStatus.FAILED, run.stage("release")); assertTrue(audit.events("approval-1").stream().anyMatch(event -> event.eventType().equals("APPROVAL_REJECTED")));
  }
  private OrchestrationEngine engine(AuditStore audit, ApprovalProvider approvals) { return new OrchestrationEngine(audit, approvals, new KillSwitch()); }
  private StageDefinition stage(String id, Set<String> dependencies, StageAction action) { return new StageDefinition(id, dependencies, "agent", false, RetryPolicy.none(), null, null, action, null); }
  private static final class InMemoryAuditStore implements AuditStore { private final Map<String, List<AuditEvent>> values = new HashMap<>(); public void append(AuditEvent event) { values.computeIfAbsent(event.runId(), ignored -> new ArrayList<>()).add(event); } public List<AuditEvent> events(String runId) { return values.getOrDefault(runId, List.of()); } }
}
