package com.schwab.agents;

import com.schwab.agents.AgentRuntime.Agent;
import com.schwab.orchestrator.model.GateResult;
import com.schwab.orchestrator.model.StageGate;
import com.schwab.policy.PolicyEngine;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Turns agent autonomy boundaries and policy-engine rules into enforceable DAG stage gates. */
public final class PolicyGates {

  private PolicyGates() { }

  /** Entry gate: denies the stage before it runs if its declared writes fall outside the agent's role boundary or the global path policy. */
  public static StageGate boundaryAndPaths(PolicyEngine policyEngine, Agent agent, Set<String> touchedPaths) {
    return context -> {
      boolean withinAgentBoundary = touchedPaths.stream()
          .allMatch(path -> agent.allowedPaths().stream().anyMatch(path::startsWith));
      if (!withinAgentBoundary) {
        return new GateResult(false, agent.role() + " is not permitted to write to " + touchedPaths,
            Map.of("agent-boundary", "DENY"));
      }
      PolicyEngine.Result policyResult = policyEngine.evaluate(
          new PolicyEngine.Evidence(touchedPaths, "", false, true, true, true));
      Map<String, String> merged = new LinkedHashMap<>(policyResult.results());
      merged.put("agent-boundary", "PASS");
      if (!"PASS".equals(merged.get("allowed-paths")) && !"SKIPPED".equals(merged.get("allowed-paths"))) {
        return new GateResult(false, "policy denied write to " + touchedPaths, Map.copyOf(merged));
      }
      return new GateResult(true, agent.role() + " boundary and path policy satisfied", Map.copyOf(merged));
    };
  }

  /** Exit gate: scans everything produced so far for leaked secrets before the stage output is accepted. */
  public static StageGate noSecretsAnywhere(PolicyEngine policyEngine) {
    return context -> {
      String combined = String.join("\n", context.artifacts().values());
      PolicyEngine.Result result = policyEngine.evaluate(
          new PolicyEngine.Evidence(Set.of(), combined, false, true, true, true));
      if ("DENY".equals(result.results().get("no-secrets"))) {
        return new GateResult(false, "secret material detected in stage output", result.results());
      }
      return new GateResult(true, "no secrets detected in output", result.results());
    };
  }

  /** Entry gate for stages that must not start until every required artifact has actually been produced upstream. */
  public static StageGate requiresArtifacts(PolicyEngine policyEngine, Set<String> requiredArtifacts) {
    return requiresArtifacts(policyEngine, requiredArtifacts, true);
  }

  /**
   * Same as {@link #requiresArtifacts(PolicyEngine, Set)}, but lets the caller say whether this
   * change actually touches a public endpoint — scenarios that only harden existing behavior
   * (no new/changed endpoint) don't need the tests-required rule to fire.
   */
  public static StageGate requiresArtifacts(PolicyEngine policyEngine, Set<String> requiredArtifacts, boolean endpointChanged) {
    return context -> {
      boolean allPresent = requiredArtifacts.stream().allMatch(key -> context.artifact(key).isPresent());
      PolicyEngine.Result result = policyEngine.evaluate(new PolicyEngine.Evidence(
          Set.of(), "", endpointChanged, context.artifact("tests").isPresent(), context.artifact("security").isPresent(), true));
      if (!allPresent) {
        return new GateResult(false, "required upstream artifacts missing: " + requiredArtifacts, result.results());
      }
      if ("DENY".equals(result.results().get("tests-required"))) {
        return new GateResult(false, "tests-required policy denied: no tests artifact for an endpoint change", result.results());
      }
      return new GateResult(true, "required upstream artifacts present", result.results());
    };
  }
}
