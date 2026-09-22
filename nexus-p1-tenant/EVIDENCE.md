# M0-03 verification evidence (Boot 4.1.1 / PostgreSQL 15.x)

Recorded **2026-09-22** after rebase onto `main` (`9e07e71`, #46+#47). This is **PostgreSQL 15.19**, not 16.

## Exact versions

| Item | Value |
|---|---|
| PostgreSQL `server_version` | `15.19 (Ubuntu 15.19-1.pgdg24.04+2)` |
| PostgreSQL `version()` | `PostgreSQL 15.19 (Ubuntu 15.19-1.pgdg24.04+2) on x86_64-pc-linux-gnu, compiled by gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0, 64-bit` |
| Spring Boot | **4.1.1** (`spring-boot-starter-parent` via `one.nexus:nexusone`) |
| Flyway | **13.7.0** (aggregator property; Boot auto-config) |
| JDK | OpenJDK `21.0.10` (`21.0.10+7-Ubuntu-124.04`) |
| Artifact | `one.nexus:nexus-p1-tenant` library, started by `nexus-bootstrap` |
| Host | local JAR/process, `127.0.0.1:5432`, no Docker |

Databases: `nexusone` (bootstrap IT), `nexus_p1` (demo), `nexus_p1_test` (P1 Surefire IT). Credentials via `NEXUS_DB_*` only (not committed).

## `mvn clean test` / `mvn test` (reactor)

From repo root. P1 IT uses `nexus_p1_test`; bootstrap IT uses `nexusone`.

```
NexusOne P1 Tenant ................................. SUCCESS
NexusOne Bootstrap ................................. SUCCESS
BUILD SUCCESS
```

| Module | Tests |
|---|---|
| `nexus-p1-tenant` | 8 (domain + `TenantModuleIT`) |
| `nexus-bootstrap` | 8 (`FlywaySqlContractTest`, `P1AdviceOnBootstrapTest`, `TraceIdFilterTest`, `NexusOnePostgresTest`) |

Bootstrap Flyway on `nexusone`: **5 SQL** versions (V001–V003 + V100–V101) plus the schema-creation row. `NexusOnePostgresTest` asserts `applied = 5`.

`P1AdviceOnBootstrapTest` asserts platform disable through the bootstrap JAR returns **`P1.TENANT.0004`**, not `CORE.API.0099`.

## `demo-two-tenants.sh`

Same machine, database `nexus_p1`, Spring Boot **4.1.1** bootstrap JAR.

Observed:

- Two business tenants created (`alpha_*`, `beta_*`); alpha disabled then re-enabled
- Platform stop → `P1.TENANT.0004` and `p1.tenant.disable.rejected` (`outcome=REJECTED`, `platform_context=true`)
- CUSTOM_LEDGER over-limit → `P1.QUOTA.0001` and `p1.quota.rejected` (`policy=REJECT`)

## Flyway history after the bootstrap/demo run (`nexus_p1` / `nexusone`)

| installed_rank | version | description | type | success |
|---|---|---|---|---|
| 0 | | `<< Flyway Schema Creation >>` | SCHEMA | t |
| 1 | 001 | create logical schemas | SQL | t |
| 2 | 002 | create table registry | SQL | t |
| 3 | 003 | platform runtime meta | SQL | t |
| 4 | 100 | p1 tenant | SQL | t |
| 5 | 101 | p1 platform tenant | SQL | t |

History table: `platform.flyway_schema_history`. Schema is **`platform`**, not a separate `p1` schema.

P1 module IT (`nexus_p1_test`) only sees V100–V101 on its classpath, so that database’s history is the two P1 SQL versions plus schema creation.

## Remaining gaps (not claimed done)

- Local copies of `Actor` / `ErrorCode` / `AuditEvent` / context types (not drop-in compatible with `nexus-common` records)
- Demo header filter vs live M0-02 `modules/isolation-kernel` issuance
- Live Keycloak / M0-04 admin init (stub `ACCEPTED`)
- GaussDB / G14 / Profile, frozen quota maxima, Vue UI
