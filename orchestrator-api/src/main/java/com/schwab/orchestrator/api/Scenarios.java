package com.schwab.orchestrator.api;

import com.schwab.agents.AgentRuntime;
import com.schwab.agents.AgentRuntime.Role;
import com.schwab.agents.PolicyGates;
import com.schwab.orchestrator.model.StageDefinition;
import com.schwab.orchestrator.model.StageGate;
import com.schwab.policy.PolicyEngine;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the three structurally distinct DAGs used by the demo. Each one exercises a different
 * required capability: greenfield is the baseline SDLC, brownfield adds a codebase-impact-analysis
 * stage ahead of design (and is the one re-planned mid-run), and ambiguous adds a clarification
 * stage that surfaces ambiguity and records assumptions as a real artifact instead of baking them
 * into the request text.
 */
final class Scenarios {

  private Scenarios() { }

  static List<StageDefinition> greenfield(PolicyEngine policyEngine) {
    StageGate testsRequired = PolicyGates.requiresArtifacts(policyEngine, Set.of("tests"), true);
    StageGate securityRequired = PolicyGates.requiresArtifacts(policyEngine, Set.of("security"), true);
    return List.of(
        AgentRuntime.stage("requirements", Role.REQUIREMENTS_ANALYST, Set.of(), false, "requirements", Set.of("docs/"), policyEngine),
        AgentRuntime.stage("design", Role.ARCHITECT, Set.of("requirements"), true, "design", Set.of("docs/"), policyEngine),
        AgentRuntime.stage("implementation", Role.IMPLEMENTER, Set.of("design"), false, "implementation", Set.of("shortener-service/"), policyEngine),
        AgentRuntime.stage("tests", Role.TEST_ENGINEER, Set.of("design"), false, "tests", Set.of("shortener-service/"), policyEngine),
        AgentRuntime.stage("docs", Role.DOC_WRITER, Set.of("design"), false, "docs", Set.of("docs/"), policyEngine),
        AgentRuntime.stage("security", Role.SECURITY_REVIEWER, Set.of("implementation", "tests", "docs"), false, "security", Set.of("docs/"), policyEngine, testsRequired),
        AgentRuntime.stage("release", Role.RELEASE_MANAGER, Set.of("security"), true, "release", Set.of("scenarios/"), policyEngine, securityRequired));
  }

  static List<StageDefinition> brownfield(PolicyEngine policyEngine) {
    StageGate testsRequired = PolicyGates.requiresArtifacts(policyEngine, Set.of("tests"), true);
    StageGate securityRequired = PolicyGates.requiresArtifacts(policyEngine, Set.of("security"), true);
    StageDefinition impactAnalysis = AgentRuntime.stage("impact-analysis", Role.ARCHITECT, Set.of("requirements"), false,
        Set.of("docs/"), policyEngine, null,
        context -> Map.of("impact-analysis",
            "Impacted modules for link expiry: LinkService.create/validateUrl (add expiry validation already partially "
                + "present via Link.expired()), Link entity + V1__create_links.sql (expiresAt column already nullable, "
                + "no migration needed), LinkController.CreateLinkRequest (expiresAt field already accepted), "
                + "LinkServiceTest/LinkControllerTest (add expiry-rejection and expired-link-404 cases). "
                + "No breaking API change; existing POST /api/v1/links and GET /{code} contracts are additive. Risk: low."));
    return List.of(
        AgentRuntime.stage("requirements", Role.REQUIREMENTS_ANALYST, Set.of(), false, "requirements", Set.of("docs/"), policyEngine),
        impactAnalysis,
        AgentRuntime.stage("design", Role.ARCHITECT, Set.of("impact-analysis"), true, "design", Set.of("docs/"), policyEngine),
        AgentRuntime.stage("implementation", Role.IMPLEMENTER, Set.of("design"), false, "implementation", Set.of("shortener-service/"), policyEngine),
        AgentRuntime.stage("tests", Role.TEST_ENGINEER, Set.of("design"), false, "tests", Set.of("shortener-service/"), policyEngine),
        AgentRuntime.stage("docs", Role.DOC_WRITER, Set.of("design"), false, "docs", Set.of("docs/"), policyEngine),
        AgentRuntime.stage("security", Role.SECURITY_REVIEWER, Set.of("implementation", "tests", "docs"), false, "security", Set.of("docs/"), policyEngine, testsRequired),
        AgentRuntime.stage("release", Role.RELEASE_MANAGER, Set.of("security"), true, "release", Set.of("scenarios/"), policyEngine, securityRequired));
  }

  static final String BROWNFIELD_REVISED_DESIGN =
      "Design revised after initial approval: the expiry check must also run inside LinkService.redirect(), "
          + "not only on create(); an already-expired link must 404 on read just like a deleted one. "
          + "This invalidates implementation, tests, docs, security and release, which must be redone against "
          + "the corrected design; requirements and impact-analysis are unaffected and keep their prior results.";

  static List<StageDefinition> ambiguous(PolicyEngine policyEngine) {
    StageGate securityRequired = PolicyGates.requiresArtifacts(policyEngine, Set.of("security"), false);
    StageDefinition clarification = AgentRuntime.stage("clarification", Role.REQUIREMENTS_ANALYST, Set.of("requirements"), false,
        Set.of("docs/"), policyEngine, null,
        context -> Map.of("clarification",
            "Ambiguities identified in 'Make links safer': (1) 'safer' could mean SSRF/malicious-destination defense, "
                + "link-rot/typosquatting detection, or click-fraud mitigation — no threat model was given. "
                + "(2) No compliance or business scope was specified. "
                + "Assumptions recorded for this run (would normally be confirmed with the requester before implementation): "
                + "scope is limited to destination-safety hardening — enforce an HTTP(S)-only scheme allow-list, deny "
                + "private/loopback/link-local/multicast destinations, cap URL length, and require a mandatory security "
                + "review gate before release. Business-analytics-trust scope is explicitly deferred as out of scope."));
    return List.of(
        AgentRuntime.stage("requirements", Role.REQUIREMENTS_ANALYST, Set.of(), false, "requirements", Set.of("docs/"), policyEngine),
        clarification,
        AgentRuntime.stage("design", Role.ARCHITECT, Set.of("clarification"), true, "design", Set.of("docs/"), policyEngine),
        AgentRuntime.stage("implementation", Role.IMPLEMENTER, Set.of("design"), false, "implementation", Set.of("shortener-service/"), policyEngine),
        AgentRuntime.stage("tests", Role.TEST_ENGINEER, Set.of("design"), false, "tests", Set.of("shortener-service/"), policyEngine),
        AgentRuntime.stage("docs", Role.DOC_WRITER, Set.of("design"), false, "docs", Set.of("docs/"), policyEngine),
        AgentRuntime.stage("security", Role.SECURITY_REVIEWER, Set.of("implementation", "tests", "docs"), false, "security", Set.of("docs/"), policyEngine),
        AgentRuntime.stage("release", Role.RELEASE_MANAGER, Set.of("security"), true, "release", Set.of("scenarios/"), policyEngine, securityRequired));
  }
}
