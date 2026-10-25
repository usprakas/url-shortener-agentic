package com.schwab.policy;

import java.util.*;
/** Small declarative policy adapter; rules are listed in policies.json and evaluated against change evidence. */
public final class PolicyEngine {
  public record Evidence(Set<String> changedPaths, String content, boolean endpointChanged, boolean testsPresent, boolean securityReviewed, boolean highRiskApproved) { }
  public record Result(boolean allowed, Map<String,String> results) { }
  public Result evaluate(Evidence evidence) { Map<String,String> r = new LinkedHashMap<>(); r.put("no-secrets", containsSecret(evidence.content()) ? "DENY" : "PASS"); r.put("allowed-paths", evidence.changedPaths().stream().allMatch(p -> p.startsWith("shortener-service/") || p.startsWith("docs/") || p.startsWith("scenarios/")) ? "PASS" : "DENY"); r.put("tests-required", !evidence.endpointChanged() || evidence.testsPresent() ? "PASS" : "DENY"); r.put("security-review-required", evidence.securityReviewed() ? "PASS" : "DENY"); r.put("high-risk-approval", evidence.highRiskApproved() ? "PASS" : "DENY"); return new Result(r.values().stream().allMatch("PASS"::equals), Map.copyOf(r)); }
  private boolean containsSecret(String content) { String lower = content == null ? "" : content.toLowerCase(Locale.ROOT); return lower.matches("(?s).*api[_-]?key\\s*[:=]\\s*[^\\s]{12,}.*"); }
}
