# G3 数据一致性（S0+S1）

Gate from 需求规格 V1.1 §12: database idempotency table, 防重复提交, optimistic lock, Outbox and consume-idempotent.

This drop **only** adds submit-start idempotency keys and record versions. It does **not** rebuild Outbox / Dispatcher (M0-09 already closed that path).

Baseline unchanged: Java 21, Spring Boot **4.1.1**, PostgreSQL 15, Flyway **13.7.0**. No Docker. G18 stays closed.

## What landed

### 1. `POST /api/v1/p3/path/submit-start` idempotency

| Item | Rule |
|---|---|
| Key | Header `Idempotency-Key` (preferred) or body `idempotency_key` |
| Store | Existing `platform.exec_idempotency` (`scope = p3.path.submit-start`) |
| Stored key | `{tenant_id}:{client_key}` so tenants cannot collide |
| First request | Claim in the **same Spring TX** as the physical row / form_submission / process / outbox / audit, then `result_ref = record_id` |
| Repeat | `Claim.DUPLICATE` → replay the original `PathView`. **No second physical row** |
| Missing key | Previous G18 behavior (new row each time). Vue always sends a key |
| Failed TX | `claimInTransaction` + `remember` join the business TX, so a rollback (including `G18RollbackProbe`) drops the claim |

Replay JSON includes `idempotent_replay=true` and the original `record_id` / `submission_id`. Outbox is not appended again.

### 2. Record `version` optimistic lock

Every physical ledger table already has `version` (tech framework §19.2). `GuardedLedgerStore.update` now:

1. Reads the current row
2. If the client sent `version` and it does not match → `409 P3.RECORD.0002`
3. `UPDATE ... WHERE id = ? AND version = ?` then `version = version + 1`
4. Zero rows + row still exists → `409 P3.RECORD.0002` (`expected` / `actual` in `details`)
5. Zero rows + row gone → `404 P3.RECORD.0001`

`PATCH /api/v1/p3/ledgers/{id}/records/{recordId}` body is `{ "values": {...}, "version": N }`. System writes (approval_status) still omit a client version but lock on the version they just read, so two concurrent edits of the same row cannot both commit.

### 3. Outbox consumer idempotency (reused, not rebuilt)

Do not add a second Outbox. G3 consumes the M0-09 / G18 measurements already on `main`:

| Evidence | Where |
|---|---|
| Consumer key on `platform.exec_idempotency` | `IdempotencyStore` / `JdbcIdempotencyStore` |
| Dispatcher skip-if-exists | `OutboxDispatcher.dispatchOne` (`scope=outbox-dispatched`) |
| `notify.inbox` handler claim | `InAppDeliveryHandler` (`scope=notify.inbox`) |
| Contract + P95 | [m0-09-outbox-api.md](./m0-09-outbox-api.md) — event-core P95 **238 ms** / 20 samples; product poller **25 ms**; lag alarm **30s** |
| Product delivery | `NexusOneOutboxDispatcherTest` PENDING → DISPATCHED, inbox `DELIVERED` |

At-least-once delivery + database keys. Keys are **not** stored in Redis.

## Evidence

Zonky PostgreSQL **15.18**, Boot **4.1.1**, Java 21. Recorded **2026-09-22T15:31:06Z**: `G3IdempotencyKeyTest` **2/2**, `G18BusinessPathIT` **4/4**. Outbox reuse **2026-09-22T15:31:42Z**: `OutboxEventContractTest` **13/13**, `EventCoreConfigurationTest` **5/5**. See `nexus-p3-studio/EVIDENCE.md` (G3 section).

| Case | Test |
|---|---|
| Same `Idempotency-Key` twice → one physical row, one submission, one process, one `exec_idempotency` row | `G18BusinessPathIT.repeatedSubmitStartWithSameKeyDoesNotCreateSecondRow` |
| Forced submit failure rolls back the claim | `G18BusinessPathIT.approvalAndLedgerRowRollBackTogether` (`g18-rollback-key`) |
| Stale `version` → `P3.RECORD.0002` | `G18BusinessPathIT.concurrentRecordEditConflictsOnVersion` and `LedgerCrudPostgresIT` |
| Two PATCHes with the same version → one `200`, one `409` | `G18BusinessPathIT.concurrentRecordEditConflictsOnVersion` |

```bash
mvn -pl nexus-p3-studio -am test -Dtest=G3IdempotencyKeyTest,G18BusinessPathIT
```

`LedgerCrudPostgresIT` also asserts `P3.RECORD.0002` when a native PostgreSQL 15 listener is present.

## Out of scope (parallel agents)

G1 isolation surfaces, G2 permission matrix, G4 audit expansion, G7 three business types, and P0/M0 rebuilds are not in this PR.
