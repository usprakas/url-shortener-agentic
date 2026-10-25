# Architecture

```mermaid
flowchart LR
 R[Requirements]-->D[Design approval]
 D-->I[Implementation]
 D-->T[Tests]
 D-->W[Docs]
 I-->S[Security review]
 T-->S
 W-->S
 S-->L[Release approval]
```

`shortener-service` owns APIs, H2/Flyway persistence and asynchronous analytics. `orchestrator-core` validates and schedules the DAG, records JSONL audit events, enforces gates/retries/rollback/safe-stop, and joins parallel branches. Agents declare roles and bounded paths; `policy-engine` evaluates declarative rules. The CLI demo writes audit evidence and a metrics summary under `artifacts/`.
