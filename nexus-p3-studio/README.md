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
- List / detail / count opt into M0-05 `QueryAstScopedQuery` (SELF/DEPT + single-column `IN` + SELF∨DEPT OR + FieldPolicy visibility through `QueryCompiler`). Unencodable leftovers still bind beside the AST, then execute leftover-bound `RenderedSql` through `GuardedJdbc` — raw `BoundSql` cannot bypass DataScope.
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
| POST/GET | `/api/v1/p3/forms/{id}/submissions` | POST is `form.write` via `FormSurfaceGuard` when P2 context is bound; GET is `form.read` |
| GET | `/api/v1/process/health` |
| POST | `/api/v1/process/instances` |
| POST | `/api/v1/process/tasks/{todoId}/approve` |
| POST | `/api/v1/process/tasks/{todoId}/reject` |
| POST | `/api/v1/process/instances/{instanceId}/withdraw` |
| GET | `/api/v1/todos/pending` / `/api/v1/p3/todos` |
| GET | `/api/v1/todos/done` |
| GET | `/api/v1/todos/initiated` |
| POST | `/api/v1/p3/path/submit-start` | Optional `Idempotency-Key` (or body `idempotency_key`). Repeat → same row, `idempotent_replay=true`. With `form_id`, requires `form.write` **and** `process.instance`/`start` before submit |
| PATCH | `/api/v1/p3/ledgers/{id}/records/{recordId}` | Body `{values, version}`. Stale / concurrent `version` → `409 P3.RECORD.0002` |
| POST | `/api/v1/p3/path/tasks/{todoId}/approve` |
| POST | `/api/v1/p3/path/tasks/{todoId}/reject` |
| GET | `/api/v1/p3/business-types` |
| POST | `/api/v1/p3/business-types/{code}/provision` |
| POST | `/api/v1/p3/path/instances/{instanceId}/withdraw` |
| GET | `/api/v1/p3/reports/decision` | Both `ledger/read` and `ledger.export.csv/submit` Decisions; no throw |
| POST | `/api/v1/p3/reports/query` | `ledger/read` via `ReportSurfaceGuard` **before** record list |
| POST | `/api/v1/p3/reports/export` | `ledger.export.csv/submit` via `ReportSurfaceGuard` then `AuthorizedExport`. Not a physical dump |
| GET | `/api/v1/p3/exports/wizard/decision` | Export cell only; decide, no write. G4 leftover, not a product export UI |
| POST | `/api/v1/p3/exports/wizard/begin` | `ledger.export.csv/submit` via `ReportSurfaceGuard` then `p3.export.begun` on the hash chain |
| POST | `/api/v1/p3/exports/wizard/complete` | Same cell, then `AuthorizedExport.submit` + `p3.export.completed`. Complete-deny after begin (revoke / `DENY`) writes the #136 pair and skips IsolatedExport; leftover is [g4-export-wizard-deny.md](../docs/g4-export-wizard-deny.md) |
| GET | `/api/v1/p3/dumps/decision` | Both `ledger/read` and `ledger.export.csv/submit` Decisions; no throw |
| POST | `/api/v1/p3/dumps/export` | `ledger.export.csv/submit` then `ledger/read` via `DumpSurfaceGuard` then `AuthorizedExport.writeProjected`. Not report Task submit |

Demo tenant headers (test only, `nexus.p3.demo-context=true` and/or `nexus.studio.demo-context=true`): `X-Nexus-Demo-Tenant-Id`, `X-Nexus-Demo-Tenant-Code`, `X-Nexus-Demo-Actor-Id`.

Flow mutating routes require `Authorization: Bearer` and `X-Nexus-Context-Id`. `X-Tenant-Id` is rejected (`P3.FLOW.0006`).

Form publish details: [docs/m0-07-form-publish.md](../docs/m0-07-form-publish.md). G5 form failed-path rollback (validate→DRAFT, SchemaService confirm/DDL abort and Hook publish-fail→unchanged Running/snapshot): [docs/g5-form-publish-rollback.md](../docs/g5-form-publish-rollback.md). G5 leftover mid-publish failure (CREATE abort / abort after SchemaService work; Running pointer + snapshot unchanged; not a product G5 close): [docs/g5-mid-publish-failure.md](../docs/g5-mid-publish-failure.md). G5 leftover browser failed-publish click / PublishHook honesty (live browser/IdP/JAR SKIP; not a product G5 close): [docs/g5-browser-failed-publish.md](../docs/g5-browser-failed-publish.md). Sequential approval: [docs/engineering/m0-08-sequential-approval.md](../docs/engineering/m0-08-sequential-approval.md). One real backend path (publish → physical row → sequential approval → Outbox `notify.inbox` → audit hash chain): [docs/g18-business-path.md](../docs/g18-business-path.md). Product G18 is **closed** for that one leave slice. G7 three types + withdraw catalog: [docs/g7-business-types.md](../docs/g7-business-types.md). Dual-tenant HTTP withdraw + reject walkthrough (not a new class) is in the same file. Expense/travel list/detail/todo query leftover: [docs/g7-query-expense-travel.md](../docs/g7-query-expense-travel.md). Query honesty leftover: [docs/g7-query-expense-travel-honesty.md](../docs/g7-query-expense-travel-honesty.md). G3 submit-start keys + record versions: [docs/g3-data-consistency.md](../docs/g3-data-consistency.md). G3 leftover consume-idempotent stress (not a product G3 close): [docs/g3-outbox-consume-idempotent.md](../docs/g3-outbox-consume-idempotent.md).

```bash
mvn -pl nexus-p3-studio -am test
```

Uses embedded PostgreSQL **15.18** (Zonky) for M0-08 ITs. Compatibility spike: `Flowable710Boot411CompatibilityIT`. Recorded run: `EVIDENCE.md`.
