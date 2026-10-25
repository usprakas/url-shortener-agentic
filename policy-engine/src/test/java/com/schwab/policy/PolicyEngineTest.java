package com.schwab.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

  @Test
  void blocksSecretsAndUntestedEndpointChanges() {
    var result = new PolicyEngine().evaluate(new PolicyEngine.Evidence(
        Set.of("shortener-service/src/main/java/A.java"), "API_KEY=abcdefghijklmnop", true, false, true, true));

    assertFalse(result.allowed());
    assertEquals("DENY", result.results().get("no-secrets"));
    assertEquals("DENY", result.results().get("tests-required"));
  }

  @Test
  void deniesWritesOutsideAllowedPathPrefixes() {
    var result = new PolicyEngine().evaluate(new PolicyEngine.Evidence(
        Set.of("policy-engine/src/main/java/Sneaky.java"), "", false, true, true, true));

    assertFalse(result.allowed());
    assertEquals("DENY", result.results().get("allowed-paths"));
  }

  @Test
  void deniesDisallowedDependencies() {
    var result = new PolicyEngine().evaluate(new PolicyEngine.Evidence(
        Set.of("shortener-service/"), "", false, true, true, true, "log4j-core:2.14.1"));

    assertFalse(result.allowed());
    assertEquals("DENY", result.results().get("disallowed-dependency"));
  }

  @Test
  void allowsCompliantChangesEndToEnd() {
    var result = new PolicyEngine().evaluate(new PolicyEngine.Evidence(
        Set.of("shortener-service/src/main/java/A.java"), "", true, true, true, true, "spring-boot-starter-web"));

    assertTrue(result.allowed());
    assertTrue(result.results().values().stream().allMatch(value -> value.equals("PASS")));
  }

  @Test
  void rulesNotListedInConfigAreSkippedRatherThanEnforced() {
    PolicyConfig onlySecretsRule = new PolicyConfig(List.of("no-secrets"), List.of("shortener-service/"), List.of());
    var result = new PolicyEngine(onlySecretsRule).evaluate(new PolicyEngine.Evidence(
        Set.of("outside-allowed-prefix/"), "", true, false, false, false));

    assertTrue(result.allowed());
    assertEquals("SKIPPED", result.results().get("allowed-paths"));
    assertEquals("SKIPPED", result.results().get("tests-required"));
  }
}
