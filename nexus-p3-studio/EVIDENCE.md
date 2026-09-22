# M0-06 SchemaService / ledger CRUD evidence

Recorded after PostgreSQL 15.x test run. **Forms / Flowable / UI were not built** in the M0-06 drop.

## Exact versions

| Item | Value |
|---|---|
| PostgreSQL `server_version` | **15.19** (Ubuntu 15.19-1.pgdg24.04+2) |
| Spring Boot | **4.1.1** |
| Flyway | **13.7.0** (window V400–V499) |
| JDK | OpenJDK **21.0.10** |
| Maven | **3.8.7** |
| Artifact | `one.nexus:nexus-p3-studio` |
| Host | local JAR/process, no Docker |

## Test matrix

Command (native PostgreSQL, dedicated `nexus_p3_test` so bootstrap `nexusone.business_data` stays empty):

```
NEXUS_DB_URL=jdbc:postgresql://127.0.0.1:5432/nexus_p3_test
NEXUS_TEST_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/nexus_p3_test
NEXUS_DB_USER=nexus
NEXUS_DB_PASSWORD=nexus
mvn -pl nexus-p3-studio test
```

| Suite | Result |
|---|---|
| `DdlOwnershipTest` | 1/1 |
| `LedgerIdentifiersTest` | 3/3 |
| `SchemaAstTest` | 2/2 |
| `FlywaySqlContractTest` | 1/1 |
| `SchemaServicePostgresIT` | 2/2 |
| `LedgerCrudPostgresIT` | 2/2 |
| **nexus-p3-studio total (M0-06 drop)** | **11/11, Failures: 0, Errors: 0** |
| `TableRegistryReplaceTest` (isolation-kernel) | 3/3 |
| `NexusOnePostgresTest` (monolith `nexusone`) | **3/3** — V400 applied; `business_data` table count **0** |

## Flyway history on `nexus_p3_test`

| version | description |
|---|---|
| 100 | p1 tenant (transitive classpath) |
| 101 | p1 platform tenant |
| 200 | iam identity (via permission-core → p2) |
| 300 | permission engine |
| **400** | **business meta registry** |

`platform.flyway_schema_history` has **0** scripts matching `create table l_%`.

`business_meta` tables created by V400: `application`, `ledger`, `ledger_field`.

## What this drop proves

- `business_meta.application` / `ledger` / `ledger_field` come from Flyway **V400**
- Physical `business_data.l_*` tables are created only by `JdbcSchemaService` (IT-created `l_alpha_orders*` / `l_alpha_ticket*`; registry `owner_module=nexus-p3-studio`; evolve bumps `schema_version` to 2)
- Illegal identifiers are rejected before DDL (`P3.SCHEMA.0001`, no `l_alpha_bad%` table)
- Schema evolution ADD COLUMN + index updates `platform.table_registry` and isolation `TableRegistry.replace`
- CRUD / list / detail / query / pagination apply `tenant_id` before rows are returned
- Dual-tenant reads do not see the other tenant's ledger or rows (`P3.LEDGER.0001`)
- List/detail/total share the M0-05 `tenant_id = ? AND (scope)` binder
- Monolith DB `nexusone` still has **0** `business_data` tables (ITs do not write there)

## Honest leftover (M0-06 drop)

- Forward-compatible Expand/Migrate/Switch/Contract for shared app tables is P0-09 / later
- Live P1 quota consume against a real tenant pack is hooked (`P1QuotaHook`) but not claimed as a frozen capacity number
- Form designer / Publish pipeline is M0-07 (below)

# M0-07 P3 form + publish evidence

Recorded on the agent VM **2026-09-22T10:58:40Z**, then rebased onto merged M0-06 (`73416a7`). Not a target Profile / Linux-ARM / offline-package acceptance. No Docker.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10 | `java -version` |
| Maven | 3.8.7 | |
| Spring Boot parent | **4.1.1** | reactor |
| Flyway | **13.7.0** | window **V530–V549**, schema `business_meta` |
| PostgreSQL | **15.18** | zonky embedded binaries, in-process (`JdbcFormPublishRollbackIT`) |
| isolation-kernel | **0.1.0-m0** | M0-02 APIs only |

Local PostgreSQL on `127.0.0.1:5432` was **not** listening in this VM, so `NexusOnePostgresTest` was not re-run against a live monolith database. Module JDBC proof is the zonky 15.18 IT.

## Command

```bash
mvn -pl nexus-p3-studio,nexus-common -am test
```

## Results (pre-rebase M0-07 drop)

`BUILD SUCCESS` at `2026-09-22T10:58:40Z`.

| Module / suite | Tests | Result |
|---|---|---|
| isolation-kernel | module suite | SUCCESS |
| nexus-common | module suite (includes `P3.FORM.0004`) | SUCCESS |
| nexus-p3-studio | **12** | Failures 0, Errors 0, Skipped 0 |
| `FormPublishStateMachineTest` | 5 | Draft→Validate→Publish→Running; fill; validate failure stays DRAFT; publish failure leaves running=1; tenant B 404 |
| `BindingValidationTest` | 4 | `column_name` / `l_*` table names → `P3.FORM.0004`; unknown ledger/field → `P3.FORM.0005` |
| `FormApiTest` | 1 | HTTP create/validate/publish/submit; physical binding 400; missing TenantContext `CORE.CTX.0001`; tenant B empty list |
| `FormFlywayContractTest` | 1 | V530 in 530–549; no `business_data` / `l_*` / `a_*` |
| `JdbcFormPublishRollbackIT` | 1 | Flyway `Migrating schema "platform" to version "530 - form metadata"` on PostgreSQL **15.18**; failed publish rolls back; `form_version` count stays 1 |

Flyway log: `Migrating schema "platform" to version "530 - form metadata"` against `PostgreSQL 15.18`.

## Honest deferred list

| Item | Why |
|---|---|
| Write to `business_data` on submit | M0-06 CRUD; submissions stay in `business_meta.form_submission` |
| Form designer UI | M0-12 sibling pages |
| Flowable | M0-08 |
| G18 closed | Ledger/todo/approval still open |
| Bootstrap Flyway history on this VM | no local PG 15 listener |
