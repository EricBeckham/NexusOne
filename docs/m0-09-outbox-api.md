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

Lag: if the oldest `PENDING`/`FAILED` row is older than `nexus.execution.lag-warn-after` (product **30s**) the dispatcher logs `OUTBOX_LAG_ALARM`. Start-of-delivery target is P95 ≤ 5s (`DeliveryLagProbe.TARGET_P95_MS`). The probe records lease-to-created age on every poll cycle.

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

## 6. Product wiring

`EventCoreConfiguration` is Spring Boot **auto-configuration** (`after = DataSourceAutoConfiguration`), registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

A component-scanned `@Configuration` with `@ConditionalOnBean(DataSource)` is evaluated **before** the DataSource exists, so `JdbcOutboxStore` / `OutboxPort` never register and `NotificationService` fails at monolith startup. Notify-only tests use `nexus.notify.outbox=record|immediate`. Product `nexus.notify.outbox=auto` must not create an in-memory Outbox.

### Dispatcher on product boot

`nexus-bootstrap/src/main/resources/application.yaml` sets `nexus.execution.dispatcher-enabled=true`. `OutboxDispatcher` is a `SmartLifecycle` whose `isAutoStartup()` follows that flag. M0-10 `InAppDeliveryHandler` is a Spring bean collected by the dispatcher; after restore it marks `notify.inbox` `DELIVERED`.

ITs that must **observe** the Outbox row in the business TX (G18 `application-g18.yml`) set `dispatcher-enabled=false` and assert `PENDING`. That is not product boot.

| Knob | Product | Meaning |
|---|---|---|
| `nexus.execution.dispatcher-enabled` | `true` | Start the Outbox poller |
| `nexus.execution.poll-interval` | `500ms` | SKIP LOCKED lease cadence (P95 start-of-delivery ≤ 5s) |
| `nexus.execution.lag-warn-after` | `30s` | `OUTBOX_LAG_ALARM` if oldest `PENDING`/`FAILED` is older |
| `nexus.execution.batch-size` | `16` | Rows leased per cycle |
| `nexus.execution.max-attempts` | `5` | Then `DEAD` + dead-letter |
| `nexus.execution.lease-duration` | `30s` | `DISPATCHING` lease |
| `nexus.notify.outbox` | `auto` | M0-09 `JdbcOutboxStore`, not `record`/`immediate` |

Worker / Quartz knobs (`worker-enabled`, `quartz-enabled`, `quartz-auto-startup`) are the task channel, not notify retry.

### Evidence

- `EventCoreConfigurationTest.productDefaultStartsDispatcher` — default properties start the poller.
- `OutboxEventContractTest` — poller delivers `notify.inbox`; start-of-delivery P95 on 20 rows; lag alarm when oldest `PENDING` is aged 40s and not yet leasable.
- `NexusOneOutboxDispatcherTest` — product `NexusOneApplication` on Zonky PG 15; PENDING → DISPATCHED; `notify.inbox` DELIVERED and `GET /api/v1/notifications` readable.

Measurements are in-process Zonky PostgreSQL 15, not a multi-node load run. Recorded **2026-09-22T13:39:16Z**: event-core P95 **238 ms** (20 samples); product poller `dispatched_at - created_at` **25 ms**. Product poll-interval 500ms is well under the 5s P95 cap; the 30s alarm is a log (`OUTBOX_LAG_ALARM`), not a pager webhook.

G3 (S0+S1 data consistency) **reuses** this consumer-idempotency evidence. It does not rebuild Outbox / Dispatcher. Submit-start keys and record `version` locks are documented in [g3-data-consistency.md](./g3-data-consistency.md).
