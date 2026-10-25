# Risks and trade-offs

## Deliberate prototype-scope trade-offs

- **H2 in-memory + Flyway, not Postgres.** `LinkRepository`/`ClickEventRepository` are plain Spring
  Data interfaces, so swapping the datasource is a config change, not a code change — but that swap
  has not actually been done or tested here. A real deployment needs a durable store; H2 loses all
  data on restart.
- **In-process scheduling, not a distributed job runner.** `OrchestrationEngine` uses Java 21
  virtual threads for parallel stages within a single JVM. There is no multi-node coordination, no
  durable work queue, and a crash mid-run loses in-flight state (the audit trail up to that point is
  safe on disk; the `RunSnapshot` itself is in memory only).
- **Local in-memory rate limiter.** `RequestControls` tracks request windows per client IP in an
  unbounded `ConcurrentHashMap` with no eviction — fine for a demo, a memory leak and an
  easily-spoofed control (trusts `getRemoteAddr()`, ignores `X-Forwarded-For`) in production. A real
  deployment needs Redis-backed rate limiting and a reverse-proxy-aware client IP resolution.
- **JSONL files for the audit trail, not a database.** Simple, append-only, human-readable, and easy
  to review in this exercise. Does not support efficient querying by time range, actor, or stage at
  scale, and has no retention/rotation policy.
- **No real sandboxing of agent actions.** Agents here only ever produce text artifacts (mock hashes
  or short LLM-style strings); none of them actually touch the filesystem or run shell commands,
  even though `Agent.mayRunCommands()` exists as a declared boundary. A system where agents really
  write files or run commands needs real process isolation (containers, restricted filesystem views,
  resource limits) — this prototype only enforces the *path* boundary via policy gates, not a
  sandboxed execution environment.
- **The Anthropic completion path is implemented but unverified here.** `AgentRuntime.anthropic()`
  makes a real `HttpClient` POST to `api.anthropic.com/v1/messages` when `ANTHROPIC_API_KEY` is set.
  No key is provisioned in this environment, so that code path has not been exercised against the
  live API — only the offline mock path has been run. Treat it as implemented-but-unintegration-tested,
  not as a claim that it was tried and works.

## Failure scenarios and how the system responds

| Scenario | Response |
|---|---|
| A stage's action throws | Bounded retry with backoff (`RetryPolicy`); on exhaustion the stage and run are marked FAILED and already-succeeded sibling stages are rolled back via their `RollbackAction`. |
| A stage never returns | Per-stage timeout (`RunBudget.perStageTimeout`) cancels the attempt and counts it as a failed attempt, subject to the same retry/rollback path. |
| A run keeps retrying without bound | `RunBudget.maxTotalAttempts` caps total attempts across the whole run; exceeding it stops the run with a `BUDGET_EXCEEDED` audit event rather than retrying forever. |
| An operator needs to stop everything immediately | `KillSwitch.engage()` is checked before every stage starts; an engaged run transitions to SAFE_STOPPED without starting the next stage. |
| A human rejects an approval-gated stage | The stage fails immediately without ever calling its action; downstream stages never become eligible since their dependency never reaches SUCCEEDED. |
| An upstream artifact is revised after approval | `rePlan` recomputes the invalidated downstream subgraph from the DAG, re-gates the revised stage's approval, and re-executes only what depends on the change — see SCENARIOS.md (b). |
| A stage tries to write outside its agent's declared path boundary | The entry gate (`PolicyGates.boundaryAndPaths`) denies it before the action ever runs. |
| A stage's output contains something that looks like a secret | The exit gate (`PolicyGates.noSecretsAnywhere`) denies the stage even though its action already "succeeded". |
| The shortener's datastore is unreachable | `LinkService` operations throw and surface as a 503 via `ServiceUnavailable` → `ApiExceptionHandler`; click recording is `@Async` and swallows its own failures so a broken analytics write never blocks or fails a redirect. |
| A malformed or unexpected request hits the API | Typed `@ExceptionHandler`s return the correct 4xx; anything genuinely unexpected falls through to a generic 500 that never echoes the underlying exception message back to the caller. |

## Known limitations, stated plainly

- No multi-tenant isolation anywhere in the orchestrator.
- No persistence of `RunSnapshot` itself across a process restart — only the audit trail survives.
- The policy engine's path-prefix and secret checks are simple string/regex matching, not a real
  static-analysis or secret-scanning tool; good enough to demonstrate the gate mechanism, not a
  substitute for a real scanner (e.g. gitleaks, Semgrep) in production.
- Metrics are computed per-run from that run's own audit events; there is no cross-run aggregation
  store, trend dashboard, or alerting.
- The demo's own three scenarios never hit a retry/rollback/timeout/budget path (by design — see
  TESTING.md); those paths are proven solely by the orchestrator-core test suite.

## What I'd do with more time

- Swap H2 for Postgres and the in-memory rate limiter for Redis; add a durable work queue (Kafka or
  SQS) between click-event capture and persistence so a database outage can't drop analytics events.
- Persist `RunSnapshot` (not just the audit trail) so a crashed run can be resumed instead of lost.
- Replace the regex-based policy checks with a real static-analysis/secret-scanning integration.
- Add real sandboxing (containers or a restricted execution context) for any agent action that
  would actually touch the filesystem or run a command, and wire `Agent.mayRunCommands()` into that
  sandbox's permission model instead of leaving it a declared-but-unused flag.
- Expose `/orchestrator/runs/{id}` as an actual HTTP endpoint (currently satisfied via the
  JSON/Markdown report the spec explicitly allows as an alternative) for a UI to poll.
