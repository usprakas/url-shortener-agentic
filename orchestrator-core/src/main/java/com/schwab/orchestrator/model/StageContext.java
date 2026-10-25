package com.schwab.orchestrator.model;

import java.util.*;

public final class StageContext {
  private final String runId; private final Map<String, String> artifacts;
  public StageContext(String runId, Map<String, String> artifacts) { this.runId = runId; this.artifacts = artifacts; }
  public String runId() { return runId; }
  public Optional<String> artifact(String key) { return Optional.ofNullable(artifacts.get(key)); }
  public Map<String, String> artifacts() { return Map.copyOf(artifacts); }
}
