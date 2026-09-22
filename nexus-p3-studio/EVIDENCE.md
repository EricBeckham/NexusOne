# M0-06 SchemaService / ledger CRUD evidence

Recorded after implementation. **Forms / Flowable / UI were not built.**

## Exact versions (filled after the PostgreSQL 15 test run)

| Item | Value |
|---|---|
| PostgreSQL `server_version` | *(pending test run)* |
| Spring Boot | **4.1.1** |
| Flyway | **13.7.0** (window V400–V499) |
| JDK | OpenJDK 21 |
| Artifact | `one.nexus:nexus-p3-studio` |
| Host | local JAR/process, no Docker |

## What this drop proves

- `business_meta.application` / `ledger` / `ledger_field` come from Flyway **V400**
- Physical `business_data.l_*` tables are created only by `JdbcSchemaService`
- Illegal identifiers are rejected before DDL
- Schema evolution ADD COLUMN + index updates `platform.table_registry` and isolation `TableRegistry`
- CRUD / list / detail / query / pagination apply `tenant_id` before rows are returned
- Dual-tenant reads do not see the other tenant's ledger or rows
- List/detail/total share the M0-05 `tenant_id = ? AND (scope)` binder

## Honest leftover

- Forward-compatible Expand/Migrate/Switch/Contract for shared app tables is P0-09 / later
- Live P1 quota consume against a real tenant pack is hooked (`P1QuotaHook`) but not claimed as a frozen capacity number
- Form designer / Publish pipeline is M0-07
