package com.schwab.orchestrator.model;

import java.util.LinkedHashMap;
import java.util.Map;

@FunctionalInterface
public interface StageGate {

  GateResult evaluate(StageContext context);

  /** Runs every gate in order, merging policy evidence; stops at the first denial. */
  static StageGate allOf(StageGate... gates) {
    return context -> {
      Map<String, String> combined = new LinkedHashMap<>();
      for (StageGate gate : gates) {
        GateResult result = gate.evaluate(context);
        combined.putAll(result.policyResults());
        if (!result.allowed()) {
          return new GateResult(false, result.rationale(), Map.copyOf(combined));
        }
      }
      return new GateResult(true, "all gates passed", Map.copyOf(combined));
    };
  }
}
