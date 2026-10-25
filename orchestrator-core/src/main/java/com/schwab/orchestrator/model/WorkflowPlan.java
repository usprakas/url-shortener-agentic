package com.schwab.orchestrator.model;

import java.util.*;

public final class WorkflowPlan {
  private final Map<String, StageDefinition> stages;
  public WorkflowPlan(Collection<StageDefinition> definitions) { Map<String, StageDefinition> result = new LinkedHashMap<>(); for (StageDefinition stage : definitions) if (result.put(stage.id(), stage) != null) throw new IllegalArgumentException("duplicate stage: " + stage.id()); for (StageDefinition stage : result.values()) for (String dependency : stage.dependencies()) if (!result.containsKey(dependency)) throw new IllegalArgumentException("unknown dependency " + dependency); this.stages = Map.copyOf(result); verifyAcyclic(); }
  public Map<String, StageDefinition> stages() { return stages; }
  private void verifyAcyclic() { Set<String> visiting = new HashSet<>(), visited = new HashSet<>(); for (String id : stages.keySet()) visit(id, visiting, visited); }
  private void visit(String id, Set<String> visiting, Set<String> visited) { if (visited.contains(id)) return; if (!visiting.add(id)) throw new IllegalArgumentException("workflow contains a cycle at " + id); for (String dependency : stages.get(id).dependencies()) visit(dependency, visiting, visited); visiting.remove(id); visited.add(id); }
}
