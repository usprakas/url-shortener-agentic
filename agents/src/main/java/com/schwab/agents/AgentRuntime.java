package com.schwab.agents;

import com.schwab.orchestrator.model.RetryPolicy;
import com.schwab.orchestrator.model.StageAction;
import com.schwab.orchestrator.model.StageDefinition;
import com.schwab.orchestrator.model.StageGate;
import com.schwab.policy.PolicyEngine;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds DAG stages for the seven SDLC agent roles. Every agent declares the path prefixes it may
 * write to and whether it may run commands; {@link PolicyGates} turns those declarations into
 * enforced entry gates, so an agent cannot act outside its boundary regardless of what the
 * completion client returns.
 */
public final class AgentRuntime {

  public enum Role { REQUIREMENTS_ANALYST, ARCHITECT, IMPLEMENTER, TEST_ENGINEER, DOC_WRITER, SECURITY_REVIEWER, RELEASE_MANAGER }

  public record Agent(Role role, Set<String> allowedPaths, boolean mayRunCommands) { }

  public interface CompletionClient { String complete(String prompt); }

  private static final Map<Role, Agent> AGENTS = new EnumMap<>(Role.class);

  static {
    AGENTS.put(Role.REQUIREMENTS_ANALYST, new Agent(Role.REQUIREMENTS_ANALYST, Set.of("docs/", "scenarios/"), false));
    AGENTS.put(Role.ARCHITECT, new Agent(Role.ARCHITECT, Set.of("docs/", "scenarios/"), false));
    AGENTS.put(Role.IMPLEMENTER, new Agent(Role.IMPLEMENTER, Set.of("shortener-service/"), true));
    AGENTS.put(Role.TEST_ENGINEER, new Agent(Role.TEST_ENGINEER, Set.of("shortener-service/"), true));
    AGENTS.put(Role.DOC_WRITER, new Agent(Role.DOC_WRITER, Set.of("docs/"), false));
    AGENTS.put(Role.SECURITY_REVIEWER, new Agent(Role.SECURITY_REVIEWER, Set.of("docs/", "scenarios/"), false));
    AGENTS.put(Role.RELEASE_MANAGER, new Agent(Role.RELEASE_MANAGER, Set.of("scenarios/"), false));
  }

  public static Agent agentFor(Role role) {
    return AGENTS.get(role);
  }

  public static CompletionClient offline() {
    return prompt -> "mock-output:" + Integer.toHexString(prompt.hashCode());
  }

  /**
   * Returns the offline deterministic client unless {@code ANTHROPIC_API_KEY} is set, in which
   * case it returns a real client bound to the Anthropic Messages API. The real path is untested
   * in this offline assessment environment (no key is provisioned here) — see RISKS_AND_TRADEOFFS.md.
   */
  public static CompletionClient anthropicOrOffline() {
    String apiKey = System.getenv("ANTHROPIC_API_KEY");
    return (apiKey == null || apiKey.isBlank()) ? offline() : anthropic(apiKey);
  }

  private static CompletionClient anthropic(String apiKey) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    return prompt -> {
      String requestBody = "{\"model\":\"claude-3-5-haiku-latest\",\"max_tokens\":256,\"messages\":"
          + "[{\"role\":\"user\",\"content\":\"" + escapeJson(prompt) + "\"}]}";
      HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
          .header("x-api-key", apiKey)
          .header("anthropic-version", "2023-06-01")
          .header("content-type", "application/json")
          .timeout(Duration.ofSeconds(30))
          .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
          .build();
      try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
          throw new IllegalStateException("Anthropic API returned status " + response.statusCode());
        }
        return extractText(response.body());
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("Anthropic API call failed", e);
      }
    };
  }

  private static String extractText(String responseBody) {
    Matcher matcher = Pattern.compile("\"text\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL).matcher(responseBody);
    return matcher.find() ? matcher.group(1) : responseBody;
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  public static StageDefinition stage(String id, Role role, Set<String> dependencies, boolean requiresApproval,
      String artifact, Set<String> touchedPaths, PolicyEngine policyEngine) {
    return stage(id, role, dependencies, requiresApproval, touchedPaths, policyEngine, null,
        context -> Map.of(artifact, anthropicOrOffline().complete(id + ":" + context.artifacts())));
  }

  public static StageDefinition stage(String id, Role role, Set<String> dependencies, boolean requiresApproval,
      String artifact, Set<String> touchedPaths, PolicyEngine policyEngine, StageGate additionalEntryGate) {
    return stage(id, role, dependencies, requiresApproval, touchedPaths, policyEngine, additionalEntryGate,
        context -> Map.of(artifact, anthropicOrOffline().complete(id + ":" + context.artifacts())));
  }

  /** Full control: a custom {@link StageAction} (e.g. deterministic codebase-impact reasoning) still gets the same boundary and secret-scanning gates as every other agent stage. */
  public static StageDefinition stage(String id, Role role, Set<String> dependencies, boolean requiresApproval,
      Set<String> touchedPaths, PolicyEngine policyEngine, StageGate additionalEntryGate, StageAction action) {
    Agent agent = agentFor(role);
    StageGate boundaryGate = PolicyGates.boundaryAndPaths(policyEngine, agent, touchedPaths);
    StageGate entryGate = additionalEntryGate == null ? boundaryGate : StageGate.allOf(additionalEntryGate, boundaryGate);
    StageGate exitGate = PolicyGates.noSecretsAnywhere(policyEngine);
    return new StageDefinition(id, dependencies, role.name().toLowerCase(Locale.ROOT), requiresApproval,
        new RetryPolicy(2, Duration.ofMillis(5)), entryGate, exitGate, action, null);
  }
}
