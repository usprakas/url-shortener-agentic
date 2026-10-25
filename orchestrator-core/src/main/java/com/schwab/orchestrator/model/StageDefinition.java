package com.schwab.orchestrator.model;

import java.util.*;

public record StageDefinition(String id, Set<String> dependencies, String actor, boolean requiresApproval, RetryPolicy retryPolicy, StageGate entryGate, StageGate exitGate, StageAction action, RollbackAction rollback) {
  public StageDefinition {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("stage id is required");
    dependencies = Set.copyOf(dependencies); actor = actor == null ? "system" : actor;
    retryPolicy = retryPolicy == null ? RetryPolicy.none() : retryPolicy;
    entryGate = entryGate == null ? ignored -> GateResult.allow() : entryGate;
    exitGate = exitGate == null ? ignored -> GateResult.allow() : exitGate;
    rollback = rollback == null ? ignored -> { } : rollback;
  }
}
