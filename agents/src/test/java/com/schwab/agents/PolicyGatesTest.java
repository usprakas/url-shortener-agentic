package com.schwab.agents;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schwab.orchestrator.model.GateResult;
import com.schwab.orchestrator.model.StageContext;
import com.schwab.policy.PolicyEngine;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyGatesTest {

  private final PolicyEngine policyEngine = new PolicyEngine();

  @Test
  void deniesAnAgentWritingOutsideItsDeclaredRoleBoundary() {
    AgentRuntime.Agent implementer = AgentRuntime.agentFor(AgentRuntime.Role.IMPLEMENTER);

    GateResult result = PolicyGates.boundaryAndPaths(policyEngine, implementer, Set.of("policy-engine/src/main/"))
        .evaluate(new StageContext("run-1", Map.of()));

    assertFalse(result.allowed());
    assertTrue(result.rationale().contains("not permitted"));
  }

  @Test
  void allowsAnAgentWritingWithinItsDeclaredRoleBoundary() {
    AgentRuntime.Agent implementer = AgentRuntime.agentFor(AgentRuntime.Role.IMPLEMENTER);

    GateResult result = PolicyGates.boundaryAndPaths(policyEngine, implementer, Set.of("shortener-service/src/main/"))
        .evaluate(new StageContext("run-1", Map.of()));

    assertTrue(result.allowed());
  }

  @Test
  void deniesStageExitWhenOutputContainsASecret() {
    GateResult result = PolicyGates.noSecretsAnywhere(policyEngine)
        .evaluate(new StageContext("run-1", Map.of("implementation", "api_key: abcdefghijklmnop")));

    assertFalse(result.allowed());
  }

  @Test
  void requiresArtifactsGateDeniesWhenAnUpstreamArtifactIsMissing() {
    GateResult result = PolicyGates.requiresArtifacts(policyEngine, Set.of("tests", "docs"))
        .evaluate(new StageContext("run-1", Map.of("docs", "done")));

    assertFalse(result.allowed());
    assertTrue(result.rationale().contains("tests"));
  }

  @Test
  void requiresArtifactsGateAllowsWhenEveryUpstreamArtifactIsPresent() {
    GateResult result = PolicyGates.requiresArtifacts(policyEngine, Set.of("tests", "docs"))
        .evaluate(new StageContext("run-1", Map.of("tests", "done", "docs", "done")));

    assertTrue(result.allowed());
  }
}
