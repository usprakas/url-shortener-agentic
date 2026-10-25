package com.schwab.orchestrator.engine;

import com.schwab.orchestrator.model.StageDefinition;

@FunctionalInterface public interface ApprovalProvider { ApprovalDecision decide(String runId, StageDefinition stage); record ApprovalDecision(boolean approved, String rationale) { public static ApprovalDecision approve(String rationale) { return new ApprovalDecision(true, rationale); } public static ApprovalDecision reject(String rationale) { return new ApprovalDecision(false, rationale); } } }
