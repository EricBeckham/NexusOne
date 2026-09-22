# M0-09 event-core evidence

Recorded on the agent VM **2026-09-22T10:26:17Z**. Not a target Profile / Linux-ARM / offline-package acceptance. No Docker.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10 | `java -version` |
| Maven | 3.8.7 | |
| Spring Boot parent | **4.1.1** | reactor |
| Flyway | **13.7.0** | window V500+, schema `platform` |
| PostgreSQL | **15.18** | zonky embedded binaries, in-process |
| isolation-kernel | **0.1.0-m0** | M0-02 APIs only |

## Command

```bash
mvn -pl nexus-event-core,nexus-task-core -am test
```

## Results

`BUILD SUCCESS` at `2026-09-22T10:26:17Z`.

| Module | Tests | Result |
|---|---|---|
| isolation-kernel | (module suite) | SUCCESS 1.632 s |
| nexus-common | (module suite) | SUCCESS |
| nexus-event-core | **11** | Failures 0, Errors 0, Skipped 0 |
| `ExecutionIsolationTest` | 2 | `RequestIsolationPolicy.bindTenant` + client-hint `P1.M02.009` |
| `OutboxEventContractTest` | 9 | missing context, restore-and-deliver, cross-tenant reject, foreign DomainEvent, retry/DLQ/replay, idempotent reconsume, lag probe, lease reclaim |

Flyway log: `Migrating schema "platform" to version "500 - execution channel"` against `PostgreSQL 15.18`.
