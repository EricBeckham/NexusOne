# M0-09 task-core evidence

Recorded on the agent VM **2026-09-22T10:26:17Z**. Not a target Profile / Linux-ARM / offline-package acceptance. No Docker.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10 | |
| Maven | 3.8.7 | |
| Spring Boot parent | **4.1.1** | |
| Quartz | **2.5.2** | `JobStoreTX` + `PostgreSQLDelegate` + clustered |
| c3p0 | 0.10.1 | Quartz JDBC pool (same as P0-07) |
| PostgreSQL | **15.18** | zonky embedded, in-process |
| Flyway | **13.7.0** | V500 quartz schema + `platform.exec_task` |

## Results

Same reactor run as event-core: `mvn -pl nexus-event-core,nexus-task-core -am test` → **BUILD SUCCESS**.

| Test | Count | What it proved |
|---|---|---|
| `TaskQueueContractTest` | 5 | submit requires M0-02 context; worker restores tenant; cross-tenant reject before handler; export `P1.M02.019` reject-before-handler + guard record; retry → DEAD + DLQ |
| `QuartzJobStoreIT` | 3 | misfire `FireNow` once with restored tenant; clustered JobStore check-in + 4 unique fires; shared isolation kernel |

Quartz log excerpts:

```
Quartz scheduler version: 2.5.2
Using job-store 'org.quartz.impl.jdbcjobstore.JobStoreTX' - which supports persistence. and is clustered.
Handling 1 trigger(s) that missed their scheduled fire-time.
```

Dual-process failover remains P0-07 evidence (`poc/p0-07/TEST-RESULTS.md`); this module proves the product JobStore wiring and tenant restore on PG 15.18 / Java 21.
