package com.schwab.orchestrator.api;

import com.schwab.orchestrator.metrics.MetricsCalculator;
import com.schwab.orchestrator.metrics.RunMetrics;
import com.schwab.orchestrator.model.AuditEvent;
import com.schwab.orchestrator.model.RunSnapshot;
import java.util.List;

record ScenarioResult(String name, RunSnapshot run, List<AuditEvent> events, List<String> notes, RunMetrics metrics) {

  static ScenarioResult of(String name, RunSnapshot run, List<AuditEvent> events, List<String> notes) {
    return new ScenarioResult(name, run, events, notes, new MetricsCalculator().compute(run, events));
  }

  boolean anyPolicyDenial() {
    return events.stream().anyMatch(event -> event.policyResults().containsValue("DENY"));
  }
}
