package com.schwab.orchestrator.engine;

import com.schwab.orchestrator.model.AuditEvent;
import com.schwab.orchestrator.model.GateResult;
import com.schwab.orchestrator.model.RunBudget;
import com.schwab.orchestrator.model.RunSnapshot;
import com.schwab.orchestrator.model.RunStatus;
import com.schwab.orchestrator.model.StageContext;
import com.schwab.orchestrator.model.StageDefinition;
import com.schwab.orchestrator.model.StageStatus;
import com.schwab.orchestrator.model.WorkflowPlan;
import com.schwab.orchestrator.persistence.AuditStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes a governed DAG: independent ready nodes run in parallel, dependent nodes join on the
 * next scheduling round. Supports bounded retries, rollback, a global kill switch, per-stage
 * timeouts, a total-attempts budget, and resuming a run after a targeted re-plan.
 */
public final class OrchestrationEngine {

  private final AuditStore audit;
  private final ApprovalProvider approvals;
  private final KillSwitch killSwitch;

  public OrchestrationEngine(AuditStore audit, ApprovalProvider approvals, KillSwitch killSwitch) {
    this.audit = audit;
    this.approvals = approvals;
    this.killSwitch = killSwitch;
  }

  public RunSnapshot execute(String runId, WorkflowPlan plan, Map<String, String> input) {
    return execute(runId, plan, input, RunBudget.defaults());
  }

  public RunSnapshot execute(String runId, WorkflowPlan plan, Map<String, String> input, RunBudget budget) {
    RunSnapshot run = new RunSnapshot(runId, plan, input);
    run.status(RunStatus.RUNNING);
    event(run, "", "orchestrator", "RUN_STARTED", "run initialized", Map.of());
    return schedule(run, plan, budget, new AtomicInteger());
  }

  /**
   * Accepts a revised value for an artifact a stage already produced — the artifact is not
   * regenerated, it is supplied, exactly as a human architect would hand the orchestrator a
   * revised design document. Every stage that transitively depends on {@code changedStageId} is
   * reset to PENDING and re-executed against the new value; stages outside that downstream
   * subgraph keep their SUCCEEDED state and artifacts, preserving cross-stage context for the
   * parts of the plan the revision did not touch. If the changed stage itself requires approval,
   * that approval is re-requested before downstream work resumes.
   */
  public RunSnapshot rePlan(RunSnapshot run, WorkflowPlan plan, String changedStageId, String revisedArtifactValue) {
    return rePlan(run, plan, changedStageId, revisedArtifactValue, RunBudget.defaults());
  }

  public RunSnapshot rePlan(RunSnapshot run, WorkflowPlan plan, String changedStageId, String revisedArtifactValue,
      RunBudget budget) {
    String previousValue = run.artifacts().get(changedStageId);
    if (Objects.equals(previousValue, revisedArtifactValue)) {
      event(run, changedStageId, "orchestrator", "REPLAN_SKIPPED", "artifact unchanged; no re-plan needed", Map.of());
      return run;
    }
    Set<String> downstream = invalidatedSubgraph(plan, changedStageId);
    downstream.remove(changedStageId);
    run.artifacts().put(changedStageId, revisedArtifactValue);
    for (String stageId : downstream) {
      run.stage(stageId, StageStatus.PENDING);
    }
    event(run, changedStageId, "orchestrator", "REPLAN_TRIGGERED",
        "artifact '" + changedStageId + "' revised; invalidated downstream subgraph " + downstream, Map.of());

    StageDefinition changedStage = plan.stages().get(changedStageId);
    if (changedStage.requiresApproval()) {
      run.stage(changedStageId, StageStatus.AWAITING_APPROVAL);
      run.status(RunStatus.AWAITING_APPROVAL);
      ApprovalProvider.ApprovalDecision decision = approvals.decide(run.runId(), changedStage);
      event(run, changedStageId, "human", decision.approved() ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED",
          decision.rationale(), Map.of());
      if (!decision.approved()) {
        run.stage(changedStageId, StageStatus.FAILED);
        run.status(RunStatus.FAILED);
        rollback(run, plan);
        return run;
      }
    }
    run.stage(changedStageId, StageStatus.SUCCEEDED);
    run.status(RunStatus.RUNNING);
    return schedule(run, plan, budget, new AtomicInteger());
  }

  private Set<String> invalidatedSubgraph(WorkflowPlan plan, String originStageId) {
    Set<String> invalidated = new LinkedHashSet<>();
    invalidated.add(originStageId);
    boolean changed;
    do {
      changed = false;
      for (StageDefinition stage : plan.stages().values()) {
        if (!invalidated.contains(stage.id()) && stage.dependencies().stream().anyMatch(invalidated::contains)) {
          invalidated.add(stage.id());
          changed = true;
        }
      }
    } while (changed);
    return invalidated;
  }

  private RunSnapshot schedule(RunSnapshot run, WorkflowPlan plan, RunBudget budget, AtomicInteger totalAttempts) {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      boolean progressed;
      do {
        var ready = plan.stages().values().stream()
            .filter(stage -> run.stage(stage.id()) == StageStatus.PENDING && dependenciesComplete(stage, run))
            .toList();
        progressed = !ready.isEmpty();
        if (!progressed) {
          break;
        }
        var outcomes = executor.invokeAll(ready.stream()
            .<Callable<Boolean>>map(stage -> () -> executeStage(run, stage, plan, budget, totalAttempts))
            .toList());
        for (Future<Boolean> outcome : outcomes) {
          if (!outcome.get()) {
            return run;
          }
        }
      } while (progressed);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      run.status(RunStatus.SAFE_STOPPED);
      event(run, "", "orchestrator", "SAFE_STOPPED", "scheduler interrupted", Map.of());
      return run;
    } catch (ExecutionException execution) {
      run.status(RunStatus.FAILED);
      event(run, "", "orchestrator", "RUN_FAILED", execution.getCause().getMessage(), Map.of());
      rollback(run, plan);
      return run;
    }
    if (run.stages().values().stream().allMatch(status -> status == StageStatus.SUCCEEDED)) {
      run.status(RunStatus.COMPLETED);
      event(run, "", "orchestrator", "RUN_COMPLETED", "all DAG stages completed", Map.of());
    } else {
      run.status(RunStatus.FAILED);
      event(run, "", "orchestrator", "RUN_FAILED", "DAG made no progress", Map.of());
      rollback(run, plan);
    }
    return run;
  }

  private boolean executeStage(RunSnapshot run, StageDefinition stage, WorkflowPlan plan, RunBudget budget,
      AtomicInteger totalAttempts) {
    if (killSwitch.engaged()) {
      run.status(RunStatus.SAFE_STOPPED);
      event(run, stage.id(), "orchestrator", "SAFE_STOPPED", "global kill switch engaged", Map.of());
      return false;
    }

    StageContext entryContext = new StageContext(run.runId(), run.artifacts());
    GateResult entry = stage.entryGate().evaluate(entryContext);
    if (!entry.allowed()) {
      run.stage(stage.id(), StageStatus.FAILED);
      run.status(RunStatus.FAILED);
      event(run, stage.id(), stage.actor(), "ENTRY_GATE_REJECTED", entry.rationale(), entry.policyResults());
      rollback(run, plan);
      return false;
    }

    if (stage.requiresApproval()) {
      run.stage(stage.id(), StageStatus.AWAITING_APPROVAL);
      run.status(RunStatus.AWAITING_APPROVAL);
      ApprovalProvider.ApprovalDecision decision = approvals.decide(run.runId(), stage);
      event(run, stage.id(), "human", decision.approved() ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED",
          decision.rationale(), Map.of());
      if (!decision.approved()) {
        run.stage(stage.id(), StageStatus.FAILED);
        run.status(RunStatus.FAILED);
        return false;
      }
      run.status(RunStatus.RUNNING);
    }

    for (int attempt = 1; attempt <= stage.retryPolicy().maxAttempts(); attempt++) {
      if (totalAttempts.incrementAndGet() > budget.maxTotalAttempts()) {
        run.stage(stage.id(), StageStatus.FAILED);
        run.status(RunStatus.FAILED);
        event(run, stage.id(), "orchestrator", "BUDGET_EXCEEDED",
            "run exceeded max total attempts (" + budget.maxTotalAttempts() + ")", Map.of());
        rollback(run, plan);
        return false;
      }
      try {
        run.stage(stage.id(), StageStatus.RUNNING);
        event(run, stage.id(), stage.actor(), "STAGE_STARTED", "attempt " + attempt, Map.of());
        Map<String, String> output = runWithTimeout(stage, run, budget.perStageTimeout());
        run.artifacts().putAll(output);
        GateResult exit = stage.exitGate().evaluate(new StageContext(run.runId(), run.artifacts()));
        if (!exit.allowed()) {
          throw new IllegalStateException(exit.rationale());
        }
        run.stage(stage.id(), StageStatus.SUCCEEDED);
        event(run, stage.id(), stage.actor(), "STAGE_SUCCEEDED", "exit gate passed", exit.policyResults());
        return true;
      } catch (Exception error) {
        event(run, stage.id(), stage.actor(), "STAGE_ATTEMPT_FAILED", describe(error), Map.of());
        if (attempt == stage.retryPolicy().maxAttempts()) {
          run.stage(stage.id(), StageStatus.FAILED);
          run.status(RunStatus.FAILED);
          rollback(run, plan);
          return false;
        }
        run.stage(stage.id(), StageStatus.RETRYING);
        run.status(RunStatus.RETRYING);
        event(run, stage.id(), "orchestrator", "RETRY_SCHEDULED", "bounded retry " + (attempt + 1), Map.of());
        sleep(stage.retryPolicy().initialBackoff());
        run.status(RunStatus.RUNNING);
      }
    }
    return false;
  }

  private Map<String, String> runWithTimeout(StageDefinition stage, RunSnapshot run, Duration timeout)
      throws Exception {
    try (ExecutorService attemptExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Map<String, String>> future = attemptExecutor.submit(
          () -> stage.action().execute(new StageContext(run.runId(), run.artifacts())));
      try {
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException timedOut) {
        future.cancel(true);
        throw new IllegalStateException("stage timed out after " + timeout);
      } catch (ExecutionException wrapped) {
        if (wrapped.getCause() instanceof Exception cause) {
          throw cause;
        }
        throw wrapped;
      }
    }
  }

  private String describe(Exception error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  private boolean dependenciesComplete(StageDefinition stage, RunSnapshot run) {
    return stage.dependencies().stream().allMatch(dependency -> run.stage(dependency) == StageStatus.SUCCEEDED);
  }

  private void rollback(RunSnapshot run, WorkflowPlan plan) {
    for (Map.Entry<String, StageStatus> stage : run.stages().entrySet()) {
      if (stage.getValue() != StageStatus.SUCCEEDED) {
        continue;
      }
      try {
        plan.stages().get(stage.getKey()).rollback().rollback(new StageContext(run.runId(), run.artifacts()));
      } catch (Exception ignored) {
        event(run, stage.getKey(), "orchestrator", "ROLLBACK_FAILED", "checkpoint restore failed", Map.of());
      }
      run.stage(stage.getKey(), StageStatus.ROLLED_BACK);
      event(run, stage.getKey(), "orchestrator", "ROLLED_BACK", "downstream failure requires checkpoint restore",
          Map.of());
    }
    run.status(RunStatus.ROLLED_BACK);
  }

  private void event(RunSnapshot run, String stage, String actor, String type, String rationale,
      Map<String, String> policies) {
    audit.append(new AuditEvent(run.runId(), stage, actor, type, Instant.now(), hash(run.artifacts().toString()),
        hash(type + rationale), rationale == null ? "" : rationale, policies));
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private void sleep(Duration duration) {
    if (duration.isZero()) {
      return;
    }
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("retry interrupted", interrupted);
    }
  }
}
