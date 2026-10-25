# Final engineering summary

## What was built

A Java 21 / Spring Boot 3 URL shortener (`shortener-service`) plus a governed SDLC orchestration
layer (`orchestrator-core`, `policy-engine`, `agents`, `orchestrator-api`) that drives requirements
→ design → implementation/tests/docs → security → release through an explicit DAG with entry/exit
gates, human approval checkpoints, bounded retries, per-stage timeouts, a total-attempt budget,
rollback, a kill switch, declarative policy-as-code, and dynamic re-planning that recomputes the
invalidated downstream subgraph from the dependency graph itself.

## Plan and rationale

The DAG, state machine, agent roles, and policy rules are the pieces the assignment explicitly
weights as the differentiator, so that is where the implementation effort went. The shortener
service is deliberately conventional (standard layered Spring Boot service, JPA, Flyway) so its
footprint stays small and the orchestrator's behavior — not the shortener's business logic — is
what a reviewer spends their hour on. See ARCHITECTURE.md for the specific design decisions and why
each one was made the way it was.

## Artifacts

- Running the demo (`./run-demo.sh`) produces, per scenario: a JSONL audit trail under
  `artifacts/audit/`, a full un-hashed artifact dump (`artifacts/<runId>.artifacts.json`), and a
  combined Markdown report (`artifacts/demo-report.md`) with real success-rate/retry/rollback/
  re-plan/latency/MTTR numbers computed from that run, not pre-written.
- `shortener-service/openapi.yaml` is a real OpenAPI document fetched from the running service
  (`/v3/api-docs.yaml`), not hand-authored.
- SCENARIOS.md quotes the literal text each scenario's stages actually produced in one real run.

## Risks, trade-offs, and validation

See RISKS_AND_TRADEOFFS.md for the full list (datastore/rate-limiter/audit-storage trade-offs, lack
of real sandboxing, the untested Anthropic path) and TESTING.md for what the 30 automated tests
across 5 modules actually cover, including the orchestrator's failure paths (retry exhaustion,
timeout, budget exhaustion, kill switch, rejected approval, rejected re-plan approval).

## Assumptions

- "Dynamic re-planning... demonstrate this in at least one scenario" is satisfied in the brownfield
  scenario specifically, by revising the already-approved design artifact mid-run.
- Scenario (c)'s requirement text is left genuinely vague on purpose, with assumption-recording
  delegated to a dedicated `clarification` DAG stage rather than pre-resolved in the input — this
  was judged truer to "surface ambiguities... then proceed" than hand-writing the resolution into
  the request.
- "Expose via CLI report and a simple `/orchestrator/runs/{id}` endpoint **or** JSON/Markdown
  report" — the spec's own wording allows either; the Markdown/JSON report path was chosen to avoid
  adding a second Spring Boot application just to serve one endpoint.
- The `shortener-service/pom.xml` repository interfaces are the intended Postgres/Redis swap point,
  but that swap itself was out of scope to actually perform and verify here.

## Limitations

Everything in RISKS_AND_TRADEOFFS.md's "known limitations" and "deliberate prototype-scope
trade-offs" sections applies; it is not repeated here. The short version: this is a reviewable
prototype of the orchestration *mechanism*, not a production-hardened system — no durable run
state across restarts, no real agent sandboxing, no distributed execution, and the real-LLM code
path is implemented but has not been run against the live Anthropic API in this environment.
