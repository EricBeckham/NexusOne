# M0-09 Outbox API (plug-in contract for M0-10)

M0-10 in-app notifications must not start a thread pool or Timer. They append to the transactional outbox and register a dispatcher handler.

Tenant context is **only** the M0-02 isolation kernel (`RequestIsolationPolicy`, `AccessContextHolder`, `IsolatedEnvelope`, `IsolatedExecutor`, `ContextSnapshot`). Do not introduce another holder.

## 1. Append in the business transaction

```java
@Inject OutboxPort outbox;

// RequestIsolationPolicy already bound the current tenant (HTTP / job / task).
UUID outboxId = outbox.append(OutboxAppendCommand.notifyInbox(
        "notify.approval.pending",
        """
        {"template":"approval.pending","recipientSubjectId":"...","title":"..."}
        """));
```

`OutboxPort.append` / `EventPort.publish`:

- rejects missing context (`P1.M02.001`) **before** INSERT
- copies `tenant_id` + `trace_id` from the bound `TenantContext`
- rejects a `DomainEvent.tenantId` that does not match the bound tenant (`P1.M02.016`)

Destination reserved for M0-10: **`notify.inbox`** (`OutboxAppendCommand.NOTIFY_INBOX`).

Same-transaction guarantee: the store uses the application `DataSource`. Call `append` inside the local Spring transaction that writes the business row.

## 2. Register a handler (M0-10)

```java
@Component
public final class InboxNotificationHandler implements OutboxHandler {
    private final IdempotencyStore idempotency;
    private final InboxWriter inbox; // M0-10

    @Override
    public boolean supports(String destination) {
        return OutboxAppendCommand.NOTIFY_INBOX.equals(destination);
    }

    @Override
    public void onDispatch(IsolatedEnvelope envelope) {
        // Context is already restored via RequestIsolationPolicy.bindTenant.
        // Dispatcher also claims a default key; still claim a business key:
        idempotency.claim(
                "notify.inbox",
                envelope.traceId() + ":" + envelope.payloadType(),
                envelope.tenantId(),
                envelope.traceId());
        inbox.write(envelope);
    }
}
```

Rules:

- Handler runs only after `IsolatedEnvelope.dispatch` accepts the restored tenant.
- Cross-tenant envelopes never reach the handler (`P1.M02.016`).
- Delivery is **at-least-once**. Persist side effects behind `IdempotencyStore` (table `platform.exec_idempotency`). Do **not** store this key in Redis.
- Unknown destinations stay in the outbox and retry; they are not silently dropped.

## 3. Dispatcher behavior

`OutboxDispatcher` polls with `FOR UPDATE SKIP LOCKED`, leases a batch, restores membership through `RequestIsolationPolicy.bindTenant`, then `IsolatedEnvelope.dispatch`.

| Outcome | Status | Next |
|---|---|---|
| handler returns | `DISPATCHED` | done |
| handler throws | `FAILED` + backoff | retry |
| `attempt >= max_attempts` | `DEAD` + `platform.exec_dead_letter` | `ReconciliationPort.replayDead` |

Lag: if the oldest `PENDING`/`FAILED` row is older than 30s the dispatcher logs `OUTBOX_LAG_ALARM` (target start-of-delivery P95 ≤ 5s).

## 4. What M0-10 must not do

- File service, Redis `CachePort`, or permission engine
- A private executor / Timer for notification retry
- Client-supplied tenant ids as the source of truth

## 5. Related types

| Type | Module |
|---|---|
| `OutboxPort` / `OutboxAppendCommand` / `OutboxHandler` | `nexus-event-core` |
| `EventPort` + `DomainEvent` | `nexus-event-core` / `nexus-common` |
| `IdempotencyStore` | `nexus-event-core` |
| `ReconciliationPort` | `nexus-event-core` |
| `IsolatedEnvelope` / `RequestIsolationPolicy` | `modules/isolation-kernel` |
