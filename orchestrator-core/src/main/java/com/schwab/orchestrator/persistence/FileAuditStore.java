package com.schwab.orchestrator.persistence;

import com.schwab.orchestrator.model.AuditEvent;
import java.io.*; import java.nio.charset.StandardCharsets; import java.nio.file.*; import java.util.*;

/** JSONL audit sink intentionally keeps individual events immutable and independently inspectable. */
public final class FileAuditStore implements AuditStore {
  private final Path directory; private final Map<String, List<AuditEvent>> memory = new HashMap<>();
  public FileAuditStore(Path directory) { this.directory = directory; }
  public synchronized void append(AuditEvent event) { memory.computeIfAbsent(event.runId(), key -> new ArrayList<>()).add(event); try { Files.createDirectories(directory); Files.writeString(directory.resolve(event.runId() + ".events.jsonl"), json(event) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (IOException error) { throw new IllegalStateException("Cannot persist audit event", error); } }
  public synchronized List<AuditEvent> events(String runId) { return List.copyOf(memory.getOrDefault(runId, List.of())); }
  private String json(AuditEvent event) { return "{\"runId\":\"" + escape(event.runId()) + "\",\"stageId\":\"" + escape(event.stageId()) + "\",\"actor\":\"" + escape(event.actor()) + "\",\"eventType\":\"" + escape(event.eventType()) + "\",\"timestamp\":\"" + event.timestamp() + "\",\"inputHash\":\"" + escape(event.inputHash()) + "\",\"outputHash\":\"" + escape(event.outputHash()) + "\",\"rationale\":\"" + escape(event.rationale()) + "\"}"; }
  private String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
