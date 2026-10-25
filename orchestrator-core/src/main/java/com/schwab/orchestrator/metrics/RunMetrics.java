package com.schwab.orchestrator.metrics;

import java.time.Duration;
import java.util.Map;

public record RunMetrics(
    String runId,
    String finalStatus,
    int totalStages,
    int succeededStages,
    int retryCount,
    int rollbackCount,
    int replanCount,
    Duration endToEndLatency,
    Map<String, Duration> stageLatencies,
    Duration meanTimeToRecovery) {

  public double successRate() {
    return totalStages == 0 ? 0.0 : (double) succeededStages / totalStages;
  }
}
