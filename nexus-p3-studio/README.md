# NexusOne P3 Studio (M0-06)

Application / ledger / field **metadata**, **SchemaService** (sole owner of dynamic DDL), and ledger **CRUD / list / detail / query / pagination**.

Forms, Draft→Validate→Publish→Running, and UI are **not** in this module (M0-07 / M0-12).

## Rules

- Flyway window **V400–V499** writes `business_meta` only. Physical `business_data.l_*` / `a_*` tables are never created by Flyway.
- `SchemaService` (`JdbcSchemaService`) is the only type that executes `CREATE TABLE` / `ALTER TABLE` / `CREATE INDEX` for tenant ledgers.
- Tenant custom tables: `l_{tenant_code}_{ledger_code}`. App-shared tables: `a_{app_code}_{ledger_code}`.
- Identifiers: `^[a-z][a-z0-9_]{0,23}$`. Illegal names are refused before DDL.
- Writes go through isolation-kernel Query AST + `GuardedJdbc` (mandatory `tenant_id`).
- List / detail / total / pagination share one `tenant_id = ? AND (DataScope)` WHERE from M0-05 `PermissionQueryBinder`.
- P1 `QuotaService` is used when the bean exists (`APP` / `CUSTOM_LEDGER`).

## API

| Method | Path |
|---|---|
| POST/GET | `/api/v1/p3/applications` |
| GET | `/api/v1/p3/applications/{id}` |
| POST | `/api/v1/p3/applications/{id}/ledgers` |
| GET | `/api/v1/p3/ledgers` / `/api/v1/p3/ledgers/{id}` |
| POST | `/api/v1/p3/ledgers/{id}/fields` |
| POST/GET | `/api/v1/p3/ledgers/{id}/records` |
| GET/PATCH/DELETE | `/api/v1/p3/ledgers/{id}/records/{recordId}` |

Demo tenant headers (test only, `nexus.p3.demo-context=true`): `X-Nexus-Demo-Tenant-Id`, `X-Nexus-Demo-Tenant-Code`, `X-Nexus-Demo-Actor-Id`.
