package com.schwab.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Evaluates declarative policy-as-code rules against evidence collected for a stage. Which rules
 * are enforced, which path prefixes are writable, and which dependencies are banned all come from
 * {@code policies.json} rather than being hardcoded here.
 */
public final class PolicyEngine {

  private static final Pattern SECRET_PATTERN = Pattern.compile(
      "(?s).*api[_-]?key\\s*[:=]\\s*\\S{12,}.*", Pattern.CASE_INSENSITIVE);

  public record Evidence(
      Set<String> changedPaths,
      String content,
      boolean endpointChanged,
      boolean testsPresent,
      boolean securityReviewed,
      boolean highRiskApproved,
      String addedDependency) {

    public Evidence(Set<String> changedPaths, String content, boolean endpointChanged, boolean testsPresent,
        boolean securityReviewed, boolean highRiskApproved) {
      this(changedPaths, content, endpointChanged, testsPresent, securityReviewed, highRiskApproved, null);
    }
  }

  public record Result(boolean allowed, Map<String, String> results) { }

  private final Set<String> enforcedRules;
  private final List<String> allowedPathPrefixes;
  private final Set<String> disallowedDependencies;

  public PolicyEngine() {
    this(PolicyConfig.loadFromClasspath());
  }

  public PolicyEngine(PolicyConfig config) {
    this.enforcedRules = Set.copyOf(config.rules());
    this.allowedPathPrefixes = List.copyOf(config.allowedPathPrefixes());
    this.disallowedDependencies = Set.copyOf(config.disallowedDependencies());
  }

  public Result evaluate(Evidence evidence) {
    Map<String, String> results = new LinkedHashMap<>();
    results.put("no-secrets", ruleResult("no-secrets", !containsSecret(evidence.content())));
    results.put("allowed-paths", ruleResult("allowed-paths", withinAllowedPaths(evidence.changedPaths())));
    results.put("tests-required", ruleResult("tests-required", !evidence.endpointChanged() || evidence.testsPresent()));
    results.put("security-review-required", ruleResult("security-review-required", evidence.securityReviewed()));
    results.put("high-risk-approval", ruleResult("high-risk-approval", evidence.highRiskApproved()));
    results.put("disallowed-dependency", ruleResult("disallowed-dependency", !isDisallowedDependency(evidence.addedDependency())));
    boolean allowed = results.values().stream().allMatch(value -> value.equals("PASS") || value.equals("SKIPPED"));
    return new Result(allowed, Map.copyOf(results));
  }

  private String ruleResult(String rule, boolean satisfied) {
    if (!enforcedRules.contains(rule)) {
      return "SKIPPED";
    }
    return satisfied ? "PASS" : "DENY";
  }

  private boolean containsSecret(String content) {
    return content != null && SECRET_PATTERN.matcher(content).matches();
  }

  private boolean withinAllowedPaths(Set<String> changedPaths) {
    return changedPaths.stream().allMatch(path -> allowedPathPrefixes.stream().anyMatch(path::startsWith));
  }

  private boolean isDisallowedDependency(String addedDependency) {
    return addedDependency != null && disallowedDependencies.contains(addedDependency);
  }
}
