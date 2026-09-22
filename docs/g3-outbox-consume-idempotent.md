# G3 leftover: Outbox consume-idempotent stress / duplicate delivery

S0+S1 PostgreSQL slice. **G18 stays closed.** This package does **not** rebuild Outbox / Dispatcher (M0-09 already owns that path). It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim a product-wide G3 close.

Submit-start keys + record `version` already landed on `main` ([g3-data-consistency.md](./g3-data-consistency.md)). That leftover is **consume-idempotent evidence**: at-least-once delivery plus `platform.exec_idempotency` skipping a second side effect. Keys stay in PostgreSQL, not Redis.

## What this leftover covers

| Path | Behavior |
|---|---|
| First lease | `OutboxDispatcher.dispatchOnce` delivers `notify.inbox` **at least once**. Row becomes `DISPATCHED`. Scope `outbox-dispatched`. |
| Requeue storm | Same outbox id requeued 32 times. Handler side effect stays **1**. One `exec_idempotency` row. |
| Concurrent replay | 16 threads call `dispatchOne` on the already-claimed record. Handler stays **1**. |
| Unique DB key | 32 threads `claim` the same `(scope, key)`. Exactly one `FIRST`. |
| Handler consume key | `InAppDeliveryHandler` (`scope=notify.inbox`) returns on `Claim.DUPLICATE` and does **not** `update` the inbox again. |
| Product boot | `NexusOneOutboxDispatcherTest.requeueAfterDeliveredDoesNotWriteSecondInbox`: after poller delivery, SQL requeue + dispatch does not add a second `notify.inbox` row. |

`InAppDeliveryHandler` now honors `DUPLICATE`. That is consume-idempotency on the existing M0-10 handler, not a second Outbox.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G3 close | Submit-start + versions + this consume evidence is an S0+S1 slice, not the full §12 gate. |
| Outbox / Dispatcher rebuild | Existing `OutboxDispatcher` / `JdbcOutboxStore` / lease poller stay as-is. |
| Multi-node dispatcher / Kafka / bus | In-process Zonky PG 15 only. |
| Production throughput / 30s soak | 32-replay storm and 16-way replay, not a load run. |
| Crash after handler claim, before inbox `UPDATE` | Honesty leftover is [g3-claim-crash-window.md](./g3-claim-crash-window.md). PASS/SKIP leftover beyond #139/#170 is [g3-outbox-consume-crash-honesty.md](./g3-outbox-consume-crash-honesty.md). Same claim-before-write order. A never-written inbox can stay `PENDING` if the first claim committed and the write did not. |
| Redis idempotency keys | Forbidden by ARCH-031. Not started. |
| Submit-start redo | Already on `main`. Not re-tested here. |
| G18 reopen | Closed; not walked again. |
| `AuditPort` same-TX rewrite | Untouched. |

## How to run

```bash
./scripts/g3-outbox-consume-idempotent.sh
```

Equivalent:

```bash
mvn -pl nexus-event-core -am test \
  -Dtest=G3OutboxConsumeIdempotentTest,OutboxEventContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -pl nexus-notification-core -am test \
  -Dtest=G3InboxConsumeIdempotentTest \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -pl nexus-bootstrap -am test \
  -Dtest=NexusOneOutboxDispatcherTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Zonky in-process PostgreSQL **15.x**. Java 21 / Boot **4.1.1**. No Docker.

## Evidence

Recorded after the harness run on this agent VM. See the follow-up commit on this PR for the exact suite counts. Until that run lands, treat the script as the reproduction command, not as a claimed BUILD SUCCESS.

| Suite | What it proves |
|---|---|
| `G3OutboxConsumeIdempotentTest` | At-least-once first delivery; 32-replay storm skips handler; concurrent replay skips handler; unique `exec_idempotency` insert; handler-level `notify.inbox` claim skips a second side effect |
| `OutboxEventContractTest` | Existing M0-09 single reconsume + P95 regression |
| `G3InboxConsumeIdempotentTest` | Product `InAppDeliveryHandler` skips a second inbox `update` (serial + 16-way) |
| `NexusOneOutboxDispatcherTest` | Product poller still delivers; requeue after `DELIVERED` does not add a second inbox row |

Details: `nexus-event-core/EVIDENCE.md`, `nexus-p3-studio/EVIDENCE.md` (G3 section still owns submit-start).
