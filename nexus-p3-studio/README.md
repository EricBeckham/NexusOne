# NexusOne P3 Studio (M0-06 + M0-07 + M0-08)

Application / ledger / field **metadata**, **SchemaService** (sole owner of dynamic DDL), ledger **CRUD / list / detail / query / pagination**, form **Draft→Validate→Publish→Running**, and **sequential approval / todos**.

UI is **not** in this module (M0-12).

## Rules

- Flyway window **V400–V499** writes ledger metadata in `business_meta` only. Flyway window **V530–V549** writes form metadata in `business_meta` only. Flyway window **V550–V599** writes process metadata in `business_meta` only. Physical `business_data.l_*` / `a_*` tables are never created by Flyway.
- `SchemaService` (`JdbcSchemaService`) is the only type that executes `CREATE TABLE` / `ALTER TABLE` / `CREATE INDEX` for tenant ledgers.
- Tenant custom tables: `l_{tenant_code}_{ledger_code}`. App-shared tables: `a_{app_code}_{ledger_code}`.
- Identifiers: `^[a-z][a-z0-9_]{0,23}$` (ledgers) / `^[a-z][a-z0-9_]{0,62}$` (forms). Illegal names are refused before DDL.
- Forms bind **`ledger_code` + `field_code` only**. Physical column/table keys are rejected (`P3.FORM.0004`).
- Writes go through isolation-kernel Query AST + `GuardedJdbc` (mandatory `tenant_id`).
- List / detail / total / pagination share one `tenant_id = ? AND (DataScope)` WHERE from M0-05 `PermissionQueryBinder`.
- P1 `QuotaService` is used when the bean exists (`APP` / `CUSTOM_LEDGER`).
- Flowable 7.1.0 runs **in-process** on Boot 4.1.1 via `SpringProcessEngineConfiguration`. Engine tables live in schema `flowable` (engine-owned). Domain lists never read `act_*`.

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
| GET | `/api/v1/process/health` |
| POST | `/api/v1/process/instances` |
| POST | `/api/v1/process/tasks/{todoId}/approve` |
| POST | `/api/v1/process/tasks/{todoId}/reject` |
| POST | `/api/v1/process/instances/{instanceId}/withdraw` |
| GET | `/api/v1/todos/pending` / `/api/v1/p3/todos` |
| GET | `/api/v1/todos/done` |
| GET | `/api/v1/todos/initiated` |
| POST | `/api/v1/p3/path/submit-start` | Optional `Idempotency-Key` (or body `idempotency_key`). Repeat → same row, `idempotent_replay=true` |
| PATCH | `/api/v1/p3/ledgers/{id}/records/{recordId}` | Body `{values, version}`. Stale / concurrent `version` → `409 P3.RECORD.0002` |
| POST | `/api/v1/p3/path/tasks/{todoId}/approve` |
| POST | `/api/v1/p3/path/tasks/{todoId}/reject` |
| POST | `/api/v1/p3/path/instances/{instanceId}/withdraw` |

Demo tenant headers (test only, `nexus.p3.demo-context=true` and/or `nexus.studio.demo-context=true`): `X-Nexus-Demo-Tenant-Id`, `X-Nexus-Demo-Tenant-Code`, `X-Nexus-Demo-Actor-Id`.

Flow mutating routes require `Authorization: Bearer` and `X-Nexus-Context-Id`. `X-Tenant-Id` is rejected (`P3.FLOW.0006`).

Form publish details: [docs/m0-07-form-publish.md](../docs/m0-07-form-publish.md). G5 form failed-path rollback (validate→DRAFT, publish-fail→unchanged Running/snapshot): [docs/g5-form-publish-rollback.md](../docs/g5-form-publish-rollback.md). Sequential approval: [docs/engineering/m0-08-sequential-approval.md](../docs/engineering/m0-08-sequential-approval.md). One real backend path (publish → physical row → sequential approval → Outbox `notify.inbox` → audit hash chain): [docs/g18-business-path.md](../docs/g18-business-path.md). Product G18 is **closed** for that one slice; the browser record is [docs/g18-business-path.md](../docs/g18-business-path.md). G3 submit-start keys + record versions: [docs/g3-data-consistency.md](../docs/g3-data-consistency.md).

```bash
mvn -pl nexus-p3-studio -am test
```

Uses embedded PostgreSQL **15.18** (Zonky) for M0-08 ITs. Compatibility spike: `Flowable710Boot411CompatibilityIT`. Recorded run: `EVIDENCE.md`.
