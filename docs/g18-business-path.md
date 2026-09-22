# G18 one real business path (backend wiring)

Work package: wire **one** vertical path so publish → physical ledger row → sequential approval → Outbox in-app notify → audit hash chain run in the same Spring / PostgreSQL transaction model.  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15, Flowable **7.1.0**, Flyway **13.7.0**. No Docker. Platform versions unchanged.

**This does not close product G18.** Vue ledger/form/todo now call live HTTP (`/api/v1/p3/*`; MOCK adapters removed). Permission UI, H5, and a browser end-to-end walkthrough are still open.

## Runtime gates (ledger 403 / notify 403 / empty `audit_record`)

Fixes for the live path Eric hit. Product G18 is still not closed.

| Symptom | Cause | Fix |
|---|---|---|
| `POST/GET /api/v1/p3/ledgers/{id}/records` → 403 `P2.PERM.0003` `no_grant` | `PermissionDataScopeHook` called `PermissionService.check("ledger","read")` which throws when the snapshot has no grant. Javadoc already said tenant-only fallback. `IamSubjectDirectory` userId is **personId**; assigning role `TENANT_ADMIN` to a position **code** does not match. | `decide()` + tenant-only fallback for `no_grant` (explicit deny / tenant boundary still 403). Demo seed: `nexus.permission.seed-tenant-member-grants=true` (product default) creates role `tenant_member` with ledger/form `read`+`write` ALL and `process.task` approve/reject, assigned to the current **person id**. |
| `GET /api/v1/notifications` → 403 `P1.M02.001` | `NotifyIsolationFilter` only binds demo headers (`nexus.notify.demo-context=false` in product). Login `Bearer` + `X-Nexus-Context-Id` never reached the isolation kernel. | Same binder as PR #60: `IssuedTenantIsolationFilter` (`HIGHEST_PRECEDENCE+19`) on `/api/v1/notifications` and `/api/v1/p3/*` except todos. Todos stay `FlowAuthFilter`. Tenant code: P1 `Tenant.code`, else existing `ledger.tenant_code` (G18 IT has no P1 tenant). |
| `platform.audit_record` = 0 rows | Product `nexus.audit.persistence` defaulted to **memory** (`InMemoryHashChainedAuditPort`). | Default is **jdbc**. Hash-chain still commits on its own chain lock (M0-11), not the business TX. |

Grant seed is idempotent per tenant. Turn off with `NEXUS_PERMISSION_SEED_TENANT_MEMBER_GRANTS=false` (permission-core ITs stay off so they still observe `no_grant`). Override audit with `NEXUS_AUDIT_PERSISTENCE=memory` if a host has no DataSource.

## What is proven (runnable)

Recorded on this branch at **2026-09-22T12:53:57Z** (G18 IT + filter/hook tests). Agent VM, no Docker.

| Component | Version |
|---|---|
| JDK | OpenJDK **21.0.10** |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky embedded **15.18** |
| Flowable | **7.1.0** in-process |
| Flyway | **13.7.0** through **V550** (G18 adds **V531**) |

| Suite | Result |
|---|---|
| `G18BusinessPathIT` | **2/2** (IAM ledger write/query, notify issued context, `platform.audit_record`) |
| `IssuedTenantIsolationFilterTest` | **5/5** |
| `PermissionDataScopeHookTest` | **4/4** |
| `TenantMemberGrantSeederTest` | **1/1** |
| `FormPublishStateMachineTest` | **6/6** (includes physical `LedgerRecordSink` submit) |
| `OutboxEventContractTest` | **10/10** (includes `appendJoinsSpringTransactionAndRollsBack`) |
| `QueryCompilerTest` | **4/4** (nullable JDBC binds) |

| Behavior | Evidence |
|---|---|
| After form/ledger publish, submit writes a **physical** `business_data.l_*` row | IT asserts `count(*) = 1` on `l_g18a_leave` (`JdbcSchemaService` DDL only) |
| Logged-in IAM context can **write and query** ledger records (no `P2.PERM.0003`) | `POST/GET /api/v1/p3/ledgers/{id}/records` with Bearer + `X-Nexus-Context-Id` after grant seed / tenant-only fallback |
| In-app notify accepts the same issued context (no `P1.M02.001`) | `GET /api/v1/notifications` with Bearer + `X-Nexus-Context-Id` returns 200 (dispatcher off, so the list may be empty) |
| Approval + that row share one Spring TX | `G18RollbackProbe.submitStartThenFail`: form_submission, physical row, `process_instance`, and `platform.exec_outbox` are all 0 after the forced failure |
| Approval completion enqueues `notify.inbox` via M0-09 Outbox **in the same TX** | `OutboxPort.append` through M0-10 `ApprovalNotificationPublisher` / `NotificationService`; row `destination = notify.inbox`, `status = PENDING`. Dispatcher is **off** in the IT so the row is observed, not a private thread pool |
| Repeat approve is idempotent | Second approve does not insert another Outbox row |
| Actions land on `platform.audit_record` and are queryable by trace | `select count(*) from platform.audit_record where trace_id = ?` ≥ 3; `AuditPort.findByTraceId` returns `p3.path.submitted` / `p3.approval.started` / `p3.approval.approved`. Hash-chain still uses its own chain lock (M0-11) |

Command:

```bash
mvn -pl nexus-event-core,nexus-p3-studio -am test \
    -Dtest=OutboxEventContractTest,G18BusinessPathIT,FormPublishStateMachineTest,IssuedTenantIsolationFilterTest,PermissionDataScopeHookTest \
    -Dsurefire.failIfNoSpecifiedTests=false
```

## API (IAM session + M0-02 isolation)

`FlowAuthFilter` binds M0-02 `AccessContextHolder` and the existing P2 session context. Client `X-Tenant-Id` is rejected.

| Method | Path |
|---|---|
| POST | `/api/v1/p3/path/submit-start` |
| POST | `/api/v1/p3/path/tasks/{todoId}/approve` |
| POST | `/api/v1/p3/path/tasks/{todoId}/reject` |
| POST | `/api/v1/p3/path/instances/{instanceId}/withdraw` |

`POST /api/v1/p3/ledgers/{id}/records` (IAM) writes a physical row through the same `GuardedLedgerStore`. `IssuedTenantIsolationFilter` binds the server-issued context for ledger/form/notify; `FlowAuthFilter` stays exclusive for todos / `/api/v1/p3/path`.

## How the path is wired

1. **Publish** remains M0-07 Draft→Validate→Publish (metadata). SchemaService already created the `l_{tenant}_{ledger}` table at ledger create (M0-06).
2. **Submit** maps running `ledger_code`/`field_code` bindings onto `GuardedLedgerStore.insert` (Query AST). No second DDL path.
3. **Start/complete** sequential approval (M0-08 Flowable) updates `approval_status` on that row in the same `@Transactional` method as process/todo writes. Business key = physical record UUID. Domain SQL never reads `act_*`.
4. **Notify** uses `ApprovalNotificationPublisher` → `NotificationService.enqueue` → `OutboxPort.append` destination `notify.inbox` (`OutboxAppendCommand.NOTIFY_INBOX`). `JdbcOutboxStore.append` uses `DataSourceUtils` so the Outbox INSERT joins the business TX. Listing `/api/v1/notifications` uses the same issued context via `IssuedTenantIsolationFilter` (PR #60 contract).
5. **Audit** uses existing `AuditPort.append` / `AuditCaptures` (data + approval) with `nexus.audit.persistence=jdbc` so rows land in `platform.audit_record`. Details are ids/codes only; field values are not logged. The hash-chain store still commits its own chain-meta lock (M0-11 design), so reject-path audits can survive a business rollback.

Tenant isolation for ledger/outbox/notify is M0-02 `AccessContextHolder` / `RequestIsolationPolicy`. `IssuedTenantIsolationFilter` issues that context from login; this package does not add another isolation holder. Sequential approval still reads the existing P2 session holder for person/assignee identity (M0-08).

## Flyway

- **V531** (`business_meta.form_submission.ledger_id` / `ledger_record_id`). Window **V530–V549**.
- No `business_data` / `l_*` / `a_*` tables. No new band.

## Assumptions

- One running form binds a single `ledger_code`. The G18 ledger includes optional `approval_status`.
- In-process Flowable 7.1.0 on the application `DataSource` / `PlatformTransactionManager` (already proven in M0-08).
- Demo P3 headers are test-only for ledger/form **metadata** setup; record write/query, path API, and notify use server-issued IAM context (`Bearer` + `X-Nexus-Context-Id`).
- `nexus.permission.seed-tenant-member-grants=true` seeds role `tenant_member` onto the current person id. `PermissionDataScopeHook` still falls back to tenant-only on leftover `no_grant`.
- `X-Trace-Id` must be a UUID / OTEL trace (`TraceIds.resolve`). The IT uses UUIDv7-shaped ids.
- `nexus.notify.outbox=auto` uses M0-09 `JdbcOutboxStore`. In-memory `RecordingOutboxPort` is only for `outbox=record`.
- `nexus.audit.persistence=jdbc` writes `platform.audit_record`. The chain lock is still a separate commit from the business TX.

## Still MOCK / deferred (not claimed)

| Item | Status |
|---|---|
| Vue 台账 / 表单 / 待办 | Live HTTP (`/api/v1/p3/*`); MOCK toggle retired |
| Live UI clicking this path | Not a G18 close; Vue no longer uses MOCK |
| Product-wide G18 / G7 three-business | Open |
| Outbox dispatcher P95 / 30s alarm | M0-09; IT leaves dispatcher off to assert the row |
| Audit hash-chain in the **same** JDBC TX as the business row | Existing `JdbcHashChainedAuditPort` uses a chain lock + own commit |
| Full G4/G6/G17 | Unchanged |
