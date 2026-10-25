package com.schwab.orchestrator.engine;

import com.schwab.orchestrator.model.*;
import com.schwab.orchestrator.persistence.AuditStore;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Executes a governed DAG deterministically; parallel scheduling is introduced by the runtime adapter in Phase 3. */
public final class OrchestrationEngine {
  private final AuditStore audit; private final ApprovalProvider approvals; private final KillSwitch killSwitch;
  public OrchestrationEngine(AuditStore audit, ApprovalProvider approvals, KillSwitch killSwitch) { this.audit = audit; this.approvals = approvals; this.killSwitch = killSwitch; }
  public RunSnapshot execute(String runId, WorkflowPlan plan, Map<String, String> input) {
    RunSnapshot run = new RunSnapshot(runId, plan, input); run.status(RunStatus.RUNNING); event(run, "", "orchestrator", "RUN_STARTED", "run initialized", Map.of());
    boolean progressed; do { progressed = false; for (StageDefinition stage : plan.stages().values()) { if (run.stage(stage.id()) != StageStatus.PENDING || !dependenciesComplete(stage, run)) continue; progressed = true; if (!executeStage(run, stage, plan)) return run; } } while (progressed);
    if (run.stages().values().stream().allMatch(value -> value == StageStatus.SUCCEEDED)) { run.status(RunStatus.COMPLETED); event(run, "", "orchestrator", "RUN_COMPLETED", "all DAG stages completed", Map.of()); } else { run.status(RunStatus.FAILED); event(run, "", "orchestrator", "RUN_FAILED", "DAG made no progress", Map.of()); rollback(run, plan); }
    return run;
  }
  private boolean executeStage(RunSnapshot run, StageDefinition stage, WorkflowPlan plan) {
    if (killSwitch.engaged()) { run.status(RunStatus.SAFE_STOPPED); event(run, stage.id(), "orchestrator", "SAFE_STOPPED", "global kill switch engaged", Map.of()); return false; }
    StageContext context = new StageContext(run.runId(), run.artifacts()); GateResult entry = stage.entryGate().evaluate(context);
    if (!entry.allowed()) { run.stage(stage.id(), StageStatus.FAILED); run.status(RunStatus.FAILED); event(run, stage.id(), stage.actor(), "ENTRY_GATE_REJECTED", entry.rationale(), entry.policyResults()); rollback(run, plan); return false; }
    if (stage.requiresApproval()) { run.stage(stage.id(), StageStatus.AWAITING_APPROVAL); run.status(RunStatus.AWAITING_APPROVAL); ApprovalProvider.ApprovalDecision decision = approvals.decide(run.runId(), stage); event(run, stage.id(), "human", decision.approved() ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED", decision.rationale(), Map.of()); if (!decision.approved()) { run.stage(stage.id(), StageStatus.FAILED); run.status(RunStatus.FAILED); return false; } run.status(RunStatus.RUNNING); }
    for (int attempt = 1; attempt <= stage.retryPolicy().maxAttempts(); attempt++) try {
      run.stage(stage.id(), StageStatus.RUNNING); event(run, stage.id(), stage.actor(), "STAGE_STARTED", "attempt " + attempt, Map.of()); Map<String, String> output = stage.action().execute(new StageContext(run.runId(), run.artifacts())); run.artifacts().putAll(output); GateResult exit = stage.exitGate().evaluate(new StageContext(run.runId(), run.artifacts()));
      if (!exit.allowed()) throw new IllegalStateException(exit.rationale()); run.stage(stage.id(), StageStatus.SUCCEEDED); event(run, stage.id(), stage.actor(), "STAGE_SUCCEEDED", "exit gate passed", exit.policyResults()); return true;
    } catch (Exception error) { event(run, stage.id(), stage.actor(), "STAGE_ATTEMPT_FAILED", error.getMessage(), Map.of()); if (attempt == stage.retryPolicy().maxAttempts()) { run.stage(stage.id(), StageStatus.FAILED); run.status(RunStatus.FAILED); rollback(run, plan); return false; } run.stage(stage.id(), StageStatus.RETRYING); run.status(RunStatus.RETRYING); event(run, stage.id(), "orchestrator", "RETRY_SCHEDULED", "bounded retry " + (attempt + 1), Map.of()); sleep(stage.retryPolicy().initialBackoff()); run.status(RunStatus.RUNNING); }
    return false;
  }
  private boolean dependenciesComplete(StageDefinition stage, RunSnapshot run) { return stage.dependencies().stream().allMatch(dependency -> run.stage(dependency) == StageStatus.SUCCEEDED); }
  private void rollback(RunSnapshot run, WorkflowPlan plan) { for (Map.Entry<String, StageStatus> stage : run.stages().entrySet()) if (stage.getValue() == StageStatus.SUCCEEDED) { try { plan.stages().get(stage.getKey()).rollback().rollback(new StageContext(run.runId(), run.artifacts())); } catch (Exception ignored) { event(run, stage.getKey(), "orchestrator", "ROLLBACK_FAILED", "checkpoint restore failed", Map.of()); } run.stage(stage.getKey(), StageStatus.ROLLED_BACK); event(run, stage.getKey(), "orchestrator", "ROLLED_BACK", "downstream failure requires checkpoint restore", Map.of()); } run.status(RunStatus.ROLLED_BACK); }
  private void event(RunSnapshot run, String stage, String actor, String type, String rationale, Map<String, String> policies) { audit.append(new AuditEvent(run.runId(), stage, actor, type, Instant.now(), hash(run.artifacts().toString()), hash(type + rationale), rationale == null ? "" : rationale, policies)); }
  private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
  private void sleep(Duration duration) { if (duration.isZero()) return; try { Thread.sleep(duration.toMillis()); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("retry interrupted", interrupted); } }
}
