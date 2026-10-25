package com.schwab.policy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Declarative policy configuration loaded from {@code policies.json}. The file lists which rules
 * are active and the data each rule checks against, so adding or disabling a rule is a config
 * change, not a code change.
 */
public record PolicyConfig(List<String> rules, List<String> allowedPathPrefixes, List<String> disallowedDependencies) {

  public static PolicyConfig loadFromClasspath() {
    try (InputStream stream = PolicyConfig.class.getResourceAsStream("/policies.json")) {
      if (stream == null) {
        throw new IllegalStateException("policies.json not found on classpath");
      }
      return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static PolicyConfig parse(String json) {
    return new PolicyConfig(extractArray(json, "rules"), extractArray(json, "allowedPathPrefixes"),
        extractArray(json, "disallowedDependencies"));
  }

  private static List<String> extractArray(String json, String field) {
    Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
    if (!matcher.find()) {
      return List.of();
    }
    String body = matcher.group(1).trim();
    if (body.isEmpty()) {
      return List.of();
    }
    return Arrays.stream(body.split(","))
        .map(value -> value.trim().replaceAll("^\"|\"$", ""))
        .filter(value -> !value.isEmpty())
        .toList();
  }
}
