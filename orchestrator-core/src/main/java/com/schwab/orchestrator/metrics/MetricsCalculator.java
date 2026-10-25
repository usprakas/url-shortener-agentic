package com.schwab.orchestrator.metrics;

import com.schwab.orchestrator.model.AuditEvent;
import com.schwab.orchestrator.model.RunSnapshot;
import com.schwab.orchestrator.model.StageStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Derives reliability metrics from a run's final state and its append-only audit trail. */
public final class MetricsCalculator {

  public RunMetrics compute(RunSnapshot run, List<AuditEvent> events) {
    int total = run.stages().size();
    int succeeded = (int) run.stages().values().stream().filter(status -> status == StageStatus.SUCCEEDED).count();
    int retries = countEvents(events, "RETRY_SCHEDULED");
    int rollbacks = countEvents(events, "ROLLED_BACK");
    int replans = countEvents(events, "REPLAN_TRIGGERED");

    Duration endToEnd = run.completedAt() == null ? Duration.ZERO : Duration.between(run.startedAt(), run.completedAt());

    Map<String, Instant> startedAt = new LinkedHashMap<>();
    Map<String, Instant> firstFailureAt = new LinkedHashMap<>();
    Map<String, Instant> succeededAt = new LinkedHashMap<>();
    for (AuditEvent event : events) {
      switch (event.eventType()) {
        case "STAGE_STARTED" -> startedAt.putIfAbsent(event.stageId(), event.timestamp());
        case "STAGE_ATTEMPT_FAILED" -> firstFailureAt.putIfAbsent(event.stageId(), event.timestamp());
        case "STAGE_SUCCEEDED" -> succeededAt.put(event.stageId(), event.timestamp());
        default -> { }
      }
    }

    Map<String, Duration> stageLatencies = new LinkedHashMap<>();
    List<Duration> recoveries = new ArrayList<>();
    for (Map.Entry<String, Instant> entry : succeededAt.entrySet()) {
      Instant start = startedAt.get(entry.getKey());
      if (start != null) {
        stageLatencies.put(entry.getKey(), Duration.between(start, entry.getValue()));
      }
      Instant firstFailure = firstFailureAt.get(entry.getKey());
      if (firstFailure != null) {
        recoveries.add(Duration.between(firstFailure, entry.getValue()));
      }
    }
    Duration mttr = recoveries.isEmpty()
        ? Duration.ZERO
        : Duration.ofMillis(recoveries.stream().mapToLong(Duration::toMillis).sum() / recoveries.size());

    return new RunMetrics(run.runId(), run.status().name(), total, succeeded, retries, rollbacks, replans,
        endToEnd, Map.copyOf(stageLatencies), mttr);
  }

  private int countEvents(List<AuditEvent> events, String type) {
    return (int) events.stream().filter(event -> event.eventType().equals(type)).count();
  }
}
