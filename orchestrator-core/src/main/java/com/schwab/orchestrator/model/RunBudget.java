package com.schwab.orchestrator.model;

import java.time.Duration;

/** Caps total stage attempts across a run and the wall-clock time any single stage attempt may take. */
public record RunBudget(int maxTotalAttempts, Duration perStageTimeout) {

  public RunBudget {
    if (maxTotalAttempts < 1) {
      throw new IllegalArgumentException("maxTotalAttempts must be positive");
    }
    if (perStageTimeout.isNegative() || perStageTimeout.isZero()) {
      throw new IllegalArgumentException("perStageTimeout must be positive");
    }
  }

  public static RunBudget defaults() {
    return new RunBudget(50, Duration.ofSeconds(30));
  }
}
