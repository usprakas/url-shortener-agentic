package com.schwab.orchestrator.model;

import java.time.Instant;
import java.util.Map;

/** Immutable, append-only evidence for a material orchestration decision. */
public record AuditEvent(String runId, String stageId, String actor, String eventType, Instant timestamp, String inputHash, String outputHash, String rationale, Map<String, String> policyResults) { }
