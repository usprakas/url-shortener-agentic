package com.schwab.orchestrator.model;
import java.util.Map;
public record GateResult(boolean allowed, String rationale, Map<String, String> policyResults) { public static GateResult allow() { return new GateResult(true, "gate passed", Map.of()); } public static GateResult deny(String rationale) { return new GateResult(false, rationale, Map.of()); } }
