# M0-03 verification evidence (Boot 4.1.1 / PostgreSQL 15.x)

Recorded **2026-09-22**. This is **PostgreSQL 15.19**, not 16.

## Exact versions

| Item | Value |
|---|---|
| PostgreSQL `server_version` | `15.19 (Ubuntu 15.19-1.pgdg24.04+2)` |
| PostgreSQL `version()` | `PostgreSQL 15.19 (Ubuntu 15.19-1.pgdg24.04+2) on x86_64-pc-linux-gnu, compiled by gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0, 64-bit` |
| Spring Boot | **4.1.1** (`spring-boot-starter-parent`, banner `:: Spring Boot :: (v4.1.1)`) |
| Flyway | **13.7.0** (aggregator property; Boot auto-config, no custom `FlywayConfig`) |
| JDK | OpenJDK `21.0.10` (`21.0.10+7-Ubuntu-124.04`) |
| Artifact | `one.nexus:nexus-p1-tenant:0.1.0-SNAPSHOT` parent `one.nexus:nexusone:0.1.0-SNAPSHOT` |
| Host | local JAR/process, `127.0.0.1:5432`, no Docker |

Databases: `nexus_p1` (demo), `nexus_p1_test` (Surefire IT). Credentials via `NEXUS_DB_*` only (not committed).

## `mvn clean test`

From repo root against `nexus_p1_test`.

```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Class | Tests |
|---|---|
| `TenantLifecycleDomainTest` | 2 |
| `EntitlementProjectionContractTest` | 1 |
| `TenantCodeTest` | 3 |
| `QuotaPolicyTest` | 1 |
| `TenantModuleIT` | 1 (create/stop/platform reject/quota reject on PG 15.19) |

Flyway during IT:

```
org.flywaydb.core.FlywayExecutor : Database: ******** (PostgreSQL 15.19)
Migrating schema "platform" to version "100 - p1 tenant"
Migrating schema "platform" to version "101 - p1 platform tenant"
Successfully applied 2 migrations to schema "platform", now at version v101
```

## `demo-two-tenants.sh`

Same machine, database `nexus_p1`, Spring Boot **4.1.1** JAR.

Observed:

- Two business tenants created (`alpha_*`, `beta_*`); alpha disabled then re-enabled
- Platform stop → `P1.TENANT.0004` and `p1.tenant.disable.rejected` (`outcome=REJECTED`, `platform_context=true`)
- CUSTOM_LEDGER over-limit → `P1.QUOTA.0001` and `p1.quota.rejected` (`policy=REJECT`)

## Flyway history after the run

Both `nexus_p1` and `nexus_p1_test`:

| installed_rank | version | description | type | success |
|---|---|---|---|---|
| 0 | | `<< Flyway Schema Creation >>` | SCHEMA | t |
| 1 | 100 | p1 tenant | SQL | t |
| 2 | 101 | p1 platform tenant | SQL | t |

History table: `platform.flyway_schema_history`.

## Remaining gaps (not claimed done)

- Thin aggregator POM vs M0-01’s full `<modules>` list (merge conflict expected with PR #46)
- `P1TenantApplication` still in this module
- Local copies of `Actor` / `ErrorCode` / context types until `nexus-common` is on `main`
- Live Keycloak / M0-04 admin init (stub `ACCEPTED`)
- Isolation kernel (demo headers only)
- GaussDB / G14 / Profile, frozen quota maxima, Vue UI
