# NexusOne Event Core (M0-09)

Transactional outbox, independent dispatcher, database idempotency, dead-letter and reconcile hooks.

Depends on merged **M0-02** (`one.nexus:isolation-kernel`) and the P0-07 Quartz/Flyway evidence. Tenant context is issued and restored only through `RequestIsolationPolicy` / `AccessContextHolder`. This module does not implement file, cache, notify, or the permission engine.

## Outbox API for M0-10

See [docs/m0-09-outbox-api.md](../docs/m0-09-outbox-api.md). Destination `notify.inbox` is reserved.

## Run tests

Zonky embedded PostgreSQL **15.18** (no Docker):

```bash
mvn -pl nexus-event-core -am test
```
