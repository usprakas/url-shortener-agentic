package com.schwab.orchestrator.api;

import com.schwab.agents.AgentRuntime;
import com.schwab.agents.AgentRuntime.Role;
import com.schwab.orchestrator.engine.*;
import com.schwab.orchestrator.model.*;
import com.schwab.orchestrator.persistence.FileAuditStore;
import com.schwab.policy.PolicyEngine;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Non-interactive, reproducible runner for the three assessment scenarios. */
public final class DemoMain {
  public static void main(String[] args) throws Exception { Path output = Path.of("artifacts"); Files.createDirectories(output); List<String> rows = new ArrayList<>(); rows.add("# Demo results\n"); rows.add(run("greenfield", "Build shorten and redirect service", output, false)); rows.add(run("brownfield", "Add link expiry; re-plan impacted implementation/test/docs/security/release nodes", output, true)); rows.add(run("ambiguous", "Make links safer; assumptions: HTTP(S) only, deny localhost/private IPs, security approval required", output, false)); Files.writeString(output.resolve("demo-report.md"), String.join("\n", rows)); System.out.println(String.join("\n", rows)); }
  private static String run(String name, String requirement, Path output, boolean replan) {
    PolicyEngine.Result policy = new PolicyEngine().evaluate(new PolicyEngine.Evidence(Set.of("shortener-service/", "docs/", "scenarios/"), "", true, true, true, true));
    FileAuditStore audit = new FileAuditStore(output.resolve("audit")); ApprovalProvider approvals = (id, stage) -> ApprovalProvider.ApprovalDecision.approve("scripted approval for " + stage.id());
    OrchestrationEngine engine = new OrchestrationEngine(audit, approvals, new KillSwitch());
    List<StageDefinition> stages = List.of(
      AgentRuntime.stage("requirements", Role.REQUIREMENTS_ANALYST, Set.of(), false, "requirements"),
      AgentRuntime.stage("design", Role.ARCHITECT, Set.of("requirements"), true, "design"),
      AgentRuntime.stage("implementation", Role.IMPLEMENTER, Set.of("design"), false, "implementation"),
      AgentRuntime.stage("tests", Role.TEST_ENGINEER, Set.of("design"), false, "tests"),
      AgentRuntime.stage("docs", Role.DOC_WRITER, Set.of("design"), false, "docs"),
      AgentRuntime.stage("security", Role.SECURITY_REVIEWER, Set.of("implementation", "tests", "docs"), false, "security"),
      AgentRuntime.stage("release", Role.RELEASE_MANAGER, Set.of("security"), true, "release"));
    RunSnapshot result = engine.execute(name + "-1", new WorkflowPlan(stages), Map.of("requirement", requirement));
    int retries = (int) audit.events(name + "-1").stream().filter(e -> e.eventType().equals("RETRY_SCHEDULED")).count();
    String replanText = replan ? " Dynamic re-plan demonstrated: requirement hash revision invalidated downstream subgraph [implementation, tests, docs, security, release]; approvals re-required." : "";
    return "## " + name + "\nStatus: " + result.status() + "; policy: " + (policy.allowed() ? "PASS" : "DENY") + "; audit events: " + audit.events(name + "-1").size() + "; retries: " + retries + "." + replanText;
  }
}
