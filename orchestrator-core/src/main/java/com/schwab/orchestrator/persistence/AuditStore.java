package com.schwab.orchestrator.persistence;
import com.schwab.orchestrator.model.AuditEvent;
import java.util.List;
public interface AuditStore { void append(AuditEvent event); List<AuditEvent> events(String runId); }
