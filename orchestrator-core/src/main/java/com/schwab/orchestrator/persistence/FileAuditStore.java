package com.schwab.orchestrator.persistence;

import com.schwab.orchestrator.model.AuditEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JSONL audit sink; each run's events live in their own file, written as immutable, independently inspectable lines. */
public final class FileAuditStore implements AuditStore {

  private final Path directory;
  private final Map<String, List<AuditEvent>> memory = new HashMap<>();

  public FileAuditStore(Path directory) {
    this.directory = directory;
  }

  @Override
  public synchronized void append(AuditEvent event) {
    memory.computeIfAbsent(event.runId(), key -> new ArrayList<>()).add(event);
    try {
      Files.createDirectories(directory);
      Files.writeString(directory.resolve(event.runId() + ".events.jsonl"), json(event) + System.lineSeparator(),
          StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException error) {
      throw new IllegalStateException("Cannot persist audit event", error);
    }
  }

  @Override
  public synchronized List<AuditEvent> events(String runId) {
    return List.copyOf(memory.getOrDefault(runId, List.of()));
  }

  private String json(AuditEvent event) {
    StringBuilder policies = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String> entry : event.policyResults().entrySet()) {
      if (!first) {
        policies.append(",");
      }
      policies.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
      first = false;
    }
    policies.append("}");
    return "{\"runId\":\"" + escape(event.runId())
        + "\",\"stageId\":\"" + escape(event.stageId())
        + "\",\"actor\":\"" + escape(event.actor())
        + "\",\"eventType\":\"" + escape(event.eventType())
        + "\",\"timestamp\":\"" + event.timestamp()
        + "\",\"inputHash\":\"" + escape(event.inputHash())
        + "\",\"outputHash\":\"" + escape(event.outputHash())
        + "\",\"rationale\":\"" + escape(event.rationale())
        + "\",\"policyResults\":" + policies + "}";
  }

  private String escape(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}
