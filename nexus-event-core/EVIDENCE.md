# M0-09 event-core evidence

Recorded on the agent VM **2026-09-22T13:39:16Z**. Not a target Profile / Linux-ARM / offline-package acceptance. No Docker. Dispatcher timing cited by the G18 close in [docs/g18-business-path.md](../docs/g18-business-path.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10 | `java -version` |
| Maven | 3.8.7 | |
| Spring Boot parent | **4.1.1** | reactor |
| Flyway | **13.7.0** | window V500+, schema `platform` |
| PostgreSQL | **15.18** | zonky embedded binaries, in-process |
| isolation-kernel | **0.1.0-m0** | M0-02 APIs only |

## Commands

```bash
mvn -pl nexus-event-core -am test
mvn -pl nexus-bootstrap -am test -Dtest=NexusOneOutboxDispatcherTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl nexus-p3-studio -am test -Dtest=G18BusinessPathIT -Dsurefire.failIfNoSpecifiedTests=false
```

## Results

`BUILD SUCCESS` (event-core suite **2026-09-22T13:37:34Z**; product dispatcher + P95 log **2026-09-22T13:39:16Z**; **re-run after rebase onto #64 `f1dc329` 2026-09-22T14:00:18Z** — EventCore **5/5**, OutboxEvent **13/13** P95 **221 ms**, product dispatcher **1/1** start-of-delivery **39 ms**, G18 **2/2**).

| Module | Tests | Result |
|---|---|---|
| isolation-kernel | (module suite) | SUCCESS |
| nexus-common | (module suite) | SUCCESS |
| nexus-event-core | **20** | Failures 0, Errors 0, Skipped 0 |
| `ExecutionIsolationTest` | 2 | `RequestIsolationPolicy.bindTenant` + client-hint `P1.M02.009` |
| `OutboxEventContractTest` | 13 | missing context, restore-and-deliver, poller delivers `notify.inbox`, cross-tenant reject, foreign DomainEvent, retry/DLQ/replay, idempotent reconsume, lag probe, lag alarm via `dispatchOnce` on aged PENDING, start-of-delivery P95, lease reclaim |
| `EventCoreConfigurationTest` | 5 | DS → `JdbcOutboxStore`; default `dispatcher-enabled` **starts** the poller; `false` does not auto-start |

Flyway log: `Migrating schema "platform" to version "500 - execution channel"` against `PostgreSQL 15.18`.

## Start-of-delivery / lag alarm (honest, env-limited)

In-process Zonky PostgreSQL **15.18** on this agent VM. Not a multi-node load run, not production hardware, not a 30s-soak of FAILED backlog.

| Measurement | Value | Cap |
|---|---|---|
| Event-core P95 start-of-delivery (`dispatchOnce` on 20 `notify.inbox` rows, batch 8) | **238 ms** (`OUTBOX_START_OF_DELIVERY_P95_MS=238 samples=20`) | 5000 ms |
| Product poller (`NexusOneApplication`, `poll-interval=500ms`) `dispatched_at - created_at` | **25 ms** (`notify.inbox delivered startOfDeliveryMs=25`) | 5000 ms |
| Lag alarm | `dispatchOnce` logs `OUTBOX_LAG_ALARM` when oldest PENDING/FAILED `created_at` is > 30s (row aged 40s, `available_at` in the future so it is not leased) | 30 s |

Product `nexus.execution.dispatcher-enabled=true`. G18 IT still sets it **false** so the business-TX PENDING row can be observed (`G18BusinessPathIT` **2/2** at **2026-09-22T13:38:46Z**).
