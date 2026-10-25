# Risks and trade-offs

This prototype uses H2, in-process scheduling, a local rate limiter, and JSONL audit storage for reviewability. Production would use Postgres, Redis, durable queues, distributed workers, DNS rebinding defenses, actual sandboxing, and a managed secret store. The Anthropic adapter is intentionally not a network client in this offline assessment mode.
