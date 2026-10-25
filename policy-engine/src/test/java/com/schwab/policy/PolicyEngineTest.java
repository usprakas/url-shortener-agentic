package com.schwab.policy;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
class PolicyEngineTest { @Test void blocksSecretsAndUntestedEndpointChanges() { var result = new PolicyEngine().evaluate(new PolicyEngine.Evidence(Set.of("shortener-service/src/main/java/A.java"), "API_KEY=abcdefghijklmnop", true, false, true, true)); assertFalse(result.allowed()); assertEquals("DENY", result.results().get("no-secrets")); assertEquals("DENY", result.results().get("tests-required")); } }
