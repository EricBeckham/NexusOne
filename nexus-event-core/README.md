# NexusOne Event Core (M0-09)

Transactional outbox, independent dispatcher, database idempotency, dead-letter and reconcile hooks.

Depends on merged **M0-02** (`one.nexus:isolation-kernel`) and the P0-07 Quartz/Flyway evidence. Tenant context is issued and restored only through `RequestIsolationPolicy` / `AccessContextHolder`. This module does not implement file, cache, notify, or the permission engine.

## Outbox API for M0-10

See [docs/m0-09-outbox-api.md](../docs/m0-09-outbox-api.md). Destination `notify.inbox` is reserved.

Product boot (`nexus-bootstrap` `application.yaml`) sets `nexus.execution.dispatcher-enabled=true`. G18 ITs that must observe PENDING turn it off. Start-of-delivery P95 ≤ 5s; `OUTBOX_LAG_ALARM` if the oldest PENDING/FAILED row is older than 30s.

G3 leftover consume-idempotent stress (duplicate delivery + DB keys skip a second side effect; not a product G3 close): [docs/g3-outbox-consume-idempotent.md](../docs/g3-outbox-consume-idempotent.md).

## Run tests

Zonky embedded PostgreSQL **15.18** (no Docker):

```bash
mvn -pl nexus-event-core -am test
```
