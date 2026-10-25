package com.schwab.orchestrator.model;

import java.time.Duration;

public record RetryPolicy(int maxAttempts, Duration initialBackoff) {
  public RetryPolicy { if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive"); if (initialBackoff.isNegative()) throw new IllegalArgumentException("backoff cannot be negative"); }
  public static RetryPolicy none() { return new RetryPolicy(1, Duration.ZERO); }
}
