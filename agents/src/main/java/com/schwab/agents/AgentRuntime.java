package com.schwab.agents;

import com.schwab.orchestrator.model.*;
import java.util.*;
public final class AgentRuntime {
  public enum Role { REQUIREMENTS_ANALYST, ARCHITECT, IMPLEMENTER, TEST_ENGINEER, DOC_WRITER, SECURITY_REVIEWER, RELEASE_MANAGER }
  public record Agent(Role role, Set<String> allowedPaths, boolean mayRunCommands) { }
  public interface CompletionClient { String complete(String prompt); }
  public static CompletionClient offline() { return prompt -> "mock-output:" + Integer.toHexString(prompt.hashCode()); }
  public static CompletionClient anthropicOrOffline() { return System.getenv("ANTHROPIC_API_KEY") == null || System.getenv("ANTHROPIC_API_KEY").isBlank() ? offline() : prompt -> "anthropic-mode-request:" + Integer.toHexString(prompt.hashCode()); }
  public static StageDefinition stage(String id, Role role, Set<String> dependencies, boolean approval, String artifact) { Agent agent = new Agent(role, Set.of("shortener-service/", "docs/", "scenarios/"), role == Role.IMPLEMENTER || role == Role.TEST_ENGINEER); return new StageDefinition(id, dependencies, role.name().toLowerCase(), approval, RetryPolicy.none(), null, null, context -> Map.of(artifact, anthropicOrOffline().complete(id + context.artifacts())), null); }
}
