package com.schwab.orchestrator.model;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RunSnapshot {
  private final String runId; private RunStatus status = RunStatus.PENDING; private final Map<String, String> artifacts = new ConcurrentHashMap<>(); private final Instant startedAt = Instant.now(); private Instant completedAt;
  public RunSnapshot(String runId, WorkflowPlan plan, Map<String, String> initialArtifacts) { this.runId = runId; plan.stages().keySet().forEach(id -> stagesPut(id, StageStatus.PENDING)); artifacts.putAll(initialArtifacts); }
  private final Map<String, StageStatus> stageStates = new ConcurrentHashMap<>(); private void stagesPut(String id, StageStatus state) { stageStates.put(id, state); } public String runId() { return runId; } public synchronized RunStatus status() { return status; } public synchronized void status(RunStatus value) { status = value; if (value == RunStatus.COMPLETED || value == RunStatus.FAILED || value == RunStatus.SAFE_STOPPED || value == RunStatus.ROLLED_BACK) completedAt = Instant.now(); } public Map<String, StageStatus> stages() { return Map.copyOf(stageStates); } public StageStatus stage(String id) { return stageStates.get(id); } public void stage(String id, StageStatus state) { stageStates.put(id, state); } public Map<String, String> artifacts() { return artifacts; } public Instant startedAt() { return startedAt; } public synchronized Instant completedAt() { return completedAt; }
}
