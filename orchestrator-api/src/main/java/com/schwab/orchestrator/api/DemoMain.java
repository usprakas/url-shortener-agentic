package com.schwab.orchestrator.api;

import com.schwab.orchestrator.engine.ApprovalProvider;
import com.schwab.orchestrator.engine.KillSwitch;
import com.schwab.orchestrator.engine.OrchestrationEngine;
import com.schwab.orchestrator.model.RunSnapshot;
import com.schwab.orchestrator.model.WorkflowPlan;
import com.schwab.orchestrator.persistence.FileAuditStore;
import com.schwab.policy.PolicyEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Non-interactive, reproducible runner for the three assessment scenarios. Each run id is
 * timestamped so repeated invocations never append to a stale audit file from an earlier run.
 */
public final class DemoMain {

  public static void main(String[] args) throws Exception {
    Path output = Path.of("artifacts");
    Files.createDirectories(output);
    PolicyEngine policyEngine = new PolicyEngine();
    FileAuditStore audit = new FileAuditStore(output.resolve("audit"));

    List<ScenarioResult> results = new ArrayList<>();
    results.add(runGreenfield(policyEngine, audit));
    results.add(runBrownfieldWithReplan(policyEngine, audit));
    results.add(runAmbiguous(policyEngine, audit));

    for (ScenarioResult result : results) {
      Files.writeString(output.resolve(result.run().runId() + ".artifacts.json"), renderArtifacts(result));
    }

    String report = DemoReport.render(results);
    Files.writeString(output.resolve("demo-report.md"), report);
    System.out.println(report);
  }

  /**
   * Writes every artifact a scenario actually produced, in full (not just a hash), so a reviewer
   * can see exactly what each agent stage generated instead of taking the audit trail's word for it.
   */
  private static String renderArtifacts(ScenarioResult result) {
    StringBuilder json = new StringBuilder("{\n");
    boolean first = true;
    for (Map.Entry<String, String> entry : new TreeMap<>(result.run().artifacts()).entrySet()) {
      if (!first) {
        json.append(",\n");
      }
      json.append("  \"").append(escapeJson(entry.getKey())).append("\": \"")
          .append(escapeJson(entry.getValue())).append('"');
      first = false;
    }
    json.append("\n}\n");
    return json.toString();
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  private static ScenarioResult runGreenfield(PolicyEngine policyEngine, FileAuditStore audit) {
    String runId = "greenfield-" + Instant.now().toEpochMilli();
    OrchestrationEngine engine = new OrchestrationEngine(audit, scriptedApproval(), new KillSwitch());
    WorkflowPlan plan = new WorkflowPlan(Scenarios.greenfield(policyEngine));
    RunSnapshot run = engine.execute(runId, plan, Map.of("requirement", "Build the core shorten/redirect service."));
    return ScenarioResult.of("greenfield", run, audit.events(runId), List.of());
  }

  private static ScenarioResult runBrownfieldWithReplan(PolicyEngine policyEngine, FileAuditStore audit) {
    String runId = "brownfield-" + Instant.now().toEpochMilli();
    OrchestrationEngine engine = new OrchestrationEngine(audit, scriptedApproval(), new KillSwitch());
    WorkflowPlan plan = new WorkflowPlan(Scenarios.brownfield(policyEngine));
    RunSnapshot run = engine.execute(runId, plan, Map.of("requirement", "Add link expiry to the existing service."));

    RunSnapshot replanned = engine.rePlan(run, plan, "design", Scenarios.BROWNFIELD_REVISED_DESIGN);

    List<String> notes = List.of(
        "Dynamic re-plan demonstrated: after design was approved once, the design artifact was revised "
            + "(expiry must also be enforced on redirect, not just create). The engine computed the invalidated "
            + "downstream subgraph from the dependency DAG -> [design, implementation, tests, docs, security, release], "
            + "reset exactly those stages to PENDING, re-ran design's approval gate, and re-executed the rest — "
            + "while 'requirements' and 'impact-analysis' kept their original SUCCEEDED results and artifacts.");
    return ScenarioResult.of("brownfield", replanned, audit.events(runId), notes);
  }

  private static ScenarioResult runAmbiguous(PolicyEngine policyEngine, FileAuditStore audit) {
    String runId = "ambiguous-" + Instant.now().toEpochMilli();
    OrchestrationEngine engine = new OrchestrationEngine(audit, scriptedApproval(), new KillSwitch());
    WorkflowPlan plan = new WorkflowPlan(Scenarios.ambiguous(policyEngine));
    RunSnapshot run = engine.execute(runId, plan, Map.of("requirement", "Make links safer."));
    List<String> notes = List.of(
        "The vague input requirement was deliberately left as-is; the 'clarification' stage is what surfaces "
            + "ambiguity and records assumptions as a real artifact — see the 'clarification' entry in "
            + "artifacts/audit/" + runId + ".events.jsonl for the exact text produced.");
    return ScenarioResult.of("ambiguous", run, audit.events(runId), notes);
  }

  private static ApprovalProvider scriptedApproval() {
    return (runId, stage) -> ApprovalProvider.ApprovalDecision.approve("scripted approval for " + stage.id());
  }
}
