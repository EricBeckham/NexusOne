# NexusOne P3 Studio (M0-06 + M0-07)

Application / ledger / field **metadata**, **SchemaService** (sole owner of dynamic DDL), ledger **CRUD / list / detail / query / pagination**, and form **Draft→Validate→Publish→Running**.

UI and Flowable are **not** in this module (M0-12 / M0-08).

## Rules

- Flyway window **V400–V499** writes ledger metadata in `business_meta` only. Flyway window **V530–V549** writes form metadata in `business_meta` only. Physical `business_data.l_*` / `a_*` tables are never created by Flyway.
- `SchemaService` (`JdbcSchemaService`) is the only type that executes `CREATE TABLE` / `ALTER TABLE` / `CREATE INDEX` for tenant ledgers.
- Tenant custom tables: `l_{tenant_code}_{ledger_code}`. App-shared tables: `a_{app_code}_{ledger_code}`.
- Identifiers: `^[a-z][a-z0-9_]{0,23}$` (ledgers) / `^[a-z][a-z0-9_]{0,62}$` (forms). Illegal names are refused before DDL.
- Forms bind **`ledger_code` + `field_code` only**. Physical column/table keys are rejected (`P3.FORM.0004`).
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
| POST/GET | `/api/v1/p3/forms` |
| GET/PUT | `/api/v1/p3/forms/{id}` |
| POST | `/api/v1/p3/forms/{id}/validate` |
| POST | `/api/v1/p3/forms/{id}/publish` |
| GET | `/api/v1/p3/forms/{id}/running` |
| POST/GET | `/api/v1/p3/forms/{id}/submissions` |

Demo tenant headers (test only, `nexus.p3.demo-context=true` and/or `nexus.studio.demo-context=true`): `X-Nexus-Demo-Tenant-Id`, `X-Nexus-Demo-Tenant-Code`, `X-Nexus-Demo-Actor-Id`.

Form publish details: [docs/m0-07-form-publish.md](../docs/m0-07-form-publish.md).

```bash
mvn -pl nexus-p3-studio -am test
```
