# Testing

`./mvnw clean verify` builds all five modules and runs every test. Current counts (re-verify with
the command above; these are not hardcoded anywhere, just the result of the last run):

| Module | Test class | Tests |
|---|---|---|
| orchestrator-core | `OrchestrationEngineTest` | 11 |
| policy-engine | `PolicyEngineTest` | 5 |
| policy-engine | `PolicyConfigTest` | 2 |
| agents | `PolicyGatesTest` | 5 |
| shortener-service | `LinkControllerTest` | 2 |
| shortener-service | `LinkServiceTest` | 1 |
| shortener-service | `ApiExceptionHandlerTest` | 4 |

**30 tests total.**

## What each layer tests

- **shortener-service** — `LinkControllerTest` is a full `@SpringBootTest` + `MockMvc` integration
  test exercising create → metadata → redirect → delete → 404-after-delete, plus idempotency and
  SSRF/private-IP rejection. `LinkServiceTest` unit-tests URL validation. `ApiExceptionHandlerTest`
  unit-tests the typed exception mapping, including that an unexpected `RuntimeException` never
  leaks its message into the 500 response body.
- **orchestrator-core** — `OrchestrationEngineTest` covers the DAG engine's failure paths directly,
  since those are the hardest to get right and the easiest to silently fabricate:
  - `executesDependenciesAndPersistsAuditTrail` / `executesIndependentBranchesInParallelBeforeTheirJoin`
    — correctness of sequencing and the parallel join barrier (asserts peak concurrency == 2).
  - `retriesThenRollsBackAfterRetryExhaustion` — bounded retries, then rollback of already-succeeded
    sibling stages when a dependent stage exhausts its retry budget.
  - `stageTimesOutAndRetriesThenFails` / `exceedingTotalAttemptBudgetStopsTheRunEarly` — the
    per-stage timeout and the total-attempt budget actually stop a run, not just document a limit.
  - `stopsBeforeStartingStageWhenKillSwitchIsEngaged` — the kill switch.
  - `rejectedApprovalPreventsProtectedStage` — a rejected human approval fails the stage without
    ever executing its action.
  - `rePlanInvalidatesOnlyTheDownstreamSubgraphAndReRequiresApproval` /
    `rePlanIsANoOpWhenTheArtifactDidNotActuallyChange` /
    `rePlanStopsTheRunWhenTheRevisedStagesApprovalIsRejected` — dynamic re-planning: correct blast
    radius, a no-op when nothing changed, and a rejected re-approval halting the run.
  - `stageGateAllOfShortCircuitsOnFirstDenialAndMergesPolicyResults` — the gate combinator used to
    layer policy checks on top of agent-boundary checks.
- **policy-engine** — `PolicyEngineTest` covers secret-scanning, path-prefix denial, disallowed
  dependencies, a fully-compliant pass, and that a rule *not* listed in `policies.json` is skipped
  rather than silently enforced. `PolicyConfigTest` covers the declarative JSON loader itself,
  including that the shipped `policies.json` actually parses.
- **agents** — `PolicyGatesTest` covers that an agent's declared role boundary is actually enforced
  (an IMPLEMENTER cannot write to `policy-engine/`), that output secret-scanning denies a stage exit,
  and that the "required upstream artifact" gate denies when something is missing.

## What is not tested here

- The Anthropic completion path in `AgentRuntime` is implemented (a real HTTP call to
  `api.anthropic.com`) but is not exercised by any test or by the default demo, since no
  `ANTHROPIC_API_KEY` is provisioned in this environment. See RISKS_AND_TRADEOFFS.md.
- There is no load/concurrency test beyond the two-way parallel-join assertion in
  `OrchestrationEngineTest`.
- The demo script itself is not run inside a test; it is verified manually (`./run-demo.sh`) and
  its output is what SCENARIOS.md quotes from.
