# Scenarios

Run with `./run-demo.sh`. Each run writes `artifacts/audit/<runId>.events.jsonl` (the append-only
audit trail, with SHA-256 input/output hashes and per-gate policy results), 
`artifacts/<runId>.artifacts.json` (the full, un-hashed artifact content each stage actually
produced), and a combined `artifacts/demo-report.md`. The numbers and quoted text below are copied
verbatim from one such run (`artifacts/demo-report.md` and the matching `*.artifacts.json` files);
nothing here is hand-written output — see TESTING.md for how to regenerate it yourself.

## a) Greenfield — build the core shorten/redirect service

DAG: `requirements -> design (approval) -> {implementation, tests, docs} -> security -> release (approval)`.

The three implementation/test/docs stages are siblings with no edge between them, so the engine
runs them concurrently once `design` succeeds, then `security` joins on all three before it starts.

Actual result:
```
Status: COMPLETED; policy: PASS; audit events: 18
success rate: 100.0% (7/7 stages); retries: 0; rollbacks: 0; re-plans: 0
end-to-end latency: 58ms
per-stage latency (ms): design=0, tests=0, implementation=0, docs=1, security=0, requirements=4, release=0
```

## b) Brownfield — add link expiry to the existing service

DAG: `requirements -> impact-analysis -> design (approval) -> {implementation, tests, docs} -> security -> release (approval)`.

The orchestrator analyzes the existing codebase *before* design starts. The `impact-analysis` stage
is not a generic LLM call — its action is a deterministic reasoning step that names the concrete
impacted code:

> "Impacted modules for link expiry: LinkService.create/validateUrl (add expiry validation already
> partially present via Link.expired()), Link entity + V1__create_links.sql (expiresAt column
> already nullable, no migration needed), LinkController.CreateLinkRequest (expiresAt field already
> accepted), LinkServiceTest/LinkControllerTest (add expiry-rejection and expired-link-404 cases).
> No breaking API change; existing POST /api/v1/links and GET /{code} contracts are additive. Risk: low."

### Dynamic re-planning (demonstrated here)

After the run completes once (design approved, everything downstream SUCCEEDED), `DemoMain` calls
`engine.rePlan(run, plan, "design", <revised design text>)` to simulate the architect discovering,
after reviewing the impact analysis, that expiry also has to be enforced on **read**, not just on
create:

> "Design revised after initial approval: the expiry check must also run inside
> LinkService.redirect(), not only on create(); an already-expired link must 404 on read just like
> a deleted one. This invalidates implementation, tests, docs, security and release, which must be
> redone against the corrected design; requirements and impact-analysis are unaffected and keep
> their prior results."

The engine:
1. Diffs the new value against the stored one (a no-op if nothing actually changed — see
   `rePlanIsANoOpWhenTheArtifactDidNotActuallyChange` in `OrchestrationEngineTest`).
2. Walks the dependency DAG from `design` to compute the downstream subgraph:
   `[design, implementation, tests, docs, security, release]`.
3. Accepts the revised value for `design` directly (it does not re-run design's own action — the
   revision *is* the new output) and re-requests `design`'s approval gate, since it is an
   approval-gated stage.
4. Resets the five true downstream stages to PENDING and re-executes them against the revised design.
5. Leaves `requirements` and `impact-analysis` untouched — they are outside the invalidated
   subgraph, so their SUCCEEDED status and original artifacts are preserved (cross-stage context
   preservation).

Actual result (one run, including the re-plan):
```
Status: COMPLETED; policy: PASS; audit events: 34
success rate: 100.0% (8/8 stages); retries: 0; rollbacks: 0; re-plans: 1
end-to-end latency: 16ms
per-stage latency (ms): requirements=0, design=0, docs=8, release=8, security=7, impact-analysis=0, implementation=8, tests=7
```
The matching `artifacts.json` file shows `"design"` holding the exact revised text quoted above —
proof the revision survived and was not clobbered by a re-execution.

## c) Ambiguous — "make links safer"

DAG: `requirements -> clarification -> design (approval) -> {implementation, tests, docs} -> security -> release (approval)`.

The input requirement is left deliberately vague (`"Make links safer."`) — nothing is pre-baked
into it. Surfacing the ambiguity and recording assumptions is the job of the `clarification` stage,
whose action produced this real artifact in the run:

> "Ambiguities identified in 'Make links safer': (1) 'safer' could mean SSRF/malicious-destination
> defense, link-rot/typosquatting detection, or click-fraud mitigation — no threat model was given.
> (2) No compliance or business scope was specified. Assumptions recorded for this run (would
> normally be confirmed with the requester before implementation): scope is limited to
> destination-safety hardening — enforce an HTTP(S)-only scheme allow-list, deny
> private/loopback/link-local/multicast destinations, cap URL length, and require a mandatory
> security review gate before release. Business-analytics-trust scope is explicitly deferred as out
> of scope."

Because this scenario hardens existing behavior rather than changing a public endpoint, its
`release` gate evaluates the `tests-required` policy with `endpointChanged=false` — a deliberate
difference from greenfield/brownfield's gates, so the same rule behaves differently depending on
real evidence instead of being a blanket check.

Actual result:
```
Status: COMPLETED; policy: PASS; audit events: 20
success rate: 100.0% (8/8 stages); retries: 0; rollbacks: 0; re-plans: 0
end-to-end latency: 13ms
per-stage latency (ms): requirements=0, design=0, docs=2, release=1, clarification=1, security=0, implementation=0, tests=2
```

## Aggregate across all three scenarios (one `./run-demo.sh` invocation)

```
overall success rate: 100.0% (23/23 stages)
total retries: 0; total rollbacks: 0; total re-plans: 1
combined end-to-end latency: 88ms
```

Retries/rollbacks are exercised separately in `OrchestrationEngineTest`
(`retriesThenRollsBackAfterRetryExhaustion`, `stageTimesOutAndRetriesThenFails`,
`exceedingTotalAttemptBudgetStopsTheRunEarly`, `stopsBeforeStartingStageWhenKillSwitchIsEngaged`,
`rejectedApprovalPreventsProtectedStage`, `rePlanStopsTheRunWhenTheRevisedStagesApprovalIsRejected`)
rather than in the demo, so the demo's own numbers stay at zero — the demo is meant to show the
happy path end to end; the failure paths are proven by dedicated tests instead.
