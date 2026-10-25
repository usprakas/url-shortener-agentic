package com.schwab.orchestrator.api;

import com.schwab.orchestrator.metrics.RunMetrics;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Renders the Markdown report written to artifacts/demo-report.md and printed to stdout. */
final class DemoReport {

  private DemoReport() { }

  static String render(List<ScenarioResult> results) {
    StringBuilder out = new StringBuilder("# Demo results\n\n");
    int totalStages = 0;
    int succeededStages = 0;
    int totalRetries = 0;
    int totalRollbacks = 0;
    int totalReplans = 0;
    Duration totalLatency = Duration.ZERO;

    for (ScenarioResult result : results) {
      RunMetrics metrics = result.metrics();
      out.append("## ").append(result.name()).append('\n');
      out.append("- run id: `").append(result.run().runId()).append("`\n");
      out.append("- status: ").append(result.run().status()).append('\n');
      out.append("- policy: ").append(result.anyPolicyDenial() ? "DENY (see audit trail)" : "PASS").append('\n');
      out.append("- audit events: ").append(result.events().size()).append('\n');
      out.append("- success rate: ").append(format(metrics.successRate() * 100)).append("% (")
          .append(metrics.succeededStages()).append('/').append(metrics.totalStages()).append(" stages)\n");
      out.append("- retries: ").append(metrics.retryCount()).append('\n');
      out.append("- rollbacks: ").append(metrics.rollbackCount()).append('\n');
      out.append("- re-plans: ").append(metrics.replanCount()).append('\n');
      out.append("- end-to-end latency: ").append(metrics.endToEndLatency().toMillis()).append("ms\n");
      out.append("- mean time to recovery: ").append(metrics.meanTimeToRecovery().toMillis()).append("ms\n");
      out.append("- per-stage latency (ms): ");
      out.append(renderStageLatencies(metrics.stageLatencies()));
      out.append('\n');
      for (String note : result.notes()) {
        out.append("- ").append(note).append('\n');
      }
      out.append('\n');

      totalStages += metrics.totalStages();
      succeededStages += metrics.succeededStages();
      totalRetries += metrics.retryCount();
      totalRollbacks += metrics.rollbackCount();
      totalReplans += metrics.replanCount();
      totalLatency = totalLatency.plus(metrics.endToEndLatency());
    }

    out.append("## Aggregate across all scenarios\n");
    out.append("- overall success rate: ").append(format(totalStages == 0 ? 0 : (100.0 * succeededStages / totalStages)))
        .append("% (").append(succeededStages).append('/').append(totalStages).append(" stages)\n");
    out.append("- total retries: ").append(totalRetries).append('\n');
    out.append("- total rollbacks: ").append(totalRollbacks).append('\n');
    out.append("- total re-plans: ").append(totalReplans).append('\n');
    out.append("- combined end-to-end latency: ").append(totalLatency.toMillis()).append("ms\n");
    return out.toString();
  }

  private static String renderStageLatencies(Map<String, Duration> stageLatencies) {
    if (stageLatencies.isEmpty()) {
      return "n/a";
    }
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, Duration> entry : stageLatencies.entrySet()) {
      if (!first) {
        builder.append(", ");
      }
      builder.append(entry.getKey()).append('=').append(entry.getValue().toMillis());
      first = false;
    }
    return builder.toString();
  }

  private static String format(double value) {
    return String.format("%.1f", value);
  }
}
