# M0-06 SchemaService / ledger CRUD evidence

Recorded after PostgreSQL 15.x test run. **Forms / Flowable / UI were not built.**

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
| **nexus-p3-studio total** | **11/11, Failures: 0, Errors: 0** |
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

## Honest leftover

- Forward-compatible Expand/Migrate/Switch/Contract for shared app tables is P0-09 / later
- Live P1 quota consume against a real tenant pack is hooked (`P1QuotaHook`) but not claimed as a frozen capacity number
- Form designer / Publish pipeline is M0-07
