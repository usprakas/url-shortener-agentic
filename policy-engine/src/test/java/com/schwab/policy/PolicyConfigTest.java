package com.schwab.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyConfigTest {

  @Test
  void parsesArraysFromDeclarativeJson() {
    PolicyConfig config = PolicyConfig.parse("""
        {
          "rules": ["no-secrets", "allowed-paths"],
          "allowedPathPrefixes": ["shortener-service/", "docs/"],
          "disallowedDependencies": []
        }
        """);

    assertEquals(2, config.rules().size());
    assertTrue(config.rules().contains("allowed-paths"));
    assertEquals(List.of("shortener-service/", "docs/"), config.allowedPathPrefixes());
    assertTrue(config.disallowedDependencies().isEmpty());
  }

  @Test
  void loadsTheShippedPoliciesJsonFromTheClasspath() {
    PolicyConfig config = PolicyConfig.loadFromClasspath();

    assertTrue(config.rules().contains("no-secrets"));
    assertTrue(config.allowedPathPrefixes().contains("shortener-service/"));
  }
}
