# G18 one real business path (backend wiring)

Work package: wire **one** vertical path so publish → physical ledger row → sequential approval → Outbox in-app notify → audit hash chain run in the same Spring / PostgreSQL transaction model.  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15, Flowable **7.1.0**, Flyway **13.7.0**. No Docker. Platform versions unchanged.

**This does not close product G18.** Vue ledger/form/todo shells remain MOCK (`web/src/api/mocks/`). Permission UI, H5, and a browser walkthrough through the MOCK adapter are still open.

## What is proven (runnable)

Recorded on this branch at **2026-09-22T12:32:09Z** (G18 IT) and **2026-09-22T12:32:28Z** (Outbox + QueryCompiler). Agent VM, no Docker.

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
| `G18BusinessPathIT` | **2/2** |
| `FormPublishStateMachineTest` | **6/6** (includes physical `LedgerRecordSink` submit) |
| `OutboxEventContractTest` | **10/10** (includes `appendJoinsSpringTransactionAndRollsBack`) |
| `QueryCompilerTest` | **4/4** (nullable JDBC binds) |

| Behavior | Evidence |
|---|---|
| After form/ledger publish, submit writes a **physical** `business_data.l_*` row | IT asserts `count(*) = 1` on `l_g18a_leave` (`JdbcSchemaService` DDL only) |
| Approval + that row share one Spring TX | `G18RollbackProbe.submitStartThenFail`: form_submission, physical row, `process_instance`, and `platform.exec_outbox` are all 0 after the forced failure |
| Approval completion enqueues `notify.inbox` via M0-09 Outbox **in the same TX** | `OutboxPort.append` through M0-10 `ApprovalNotificationPublisher` / `NotificationService`; row `destination = notify.inbox`, `status = PENDING`. Dispatcher is **off** in the IT so the row is observed, not a private thread pool |
| Repeat approve is idempotent | Second approve does not insert another Outbox row |
| Actions are on the M0-11 hash chain and queryable by trace | `AuditPort.findByTraceId` returns `p3.path.submitted`, `p3.approval.started`, `p3.approval.approved` for a valid UUID `X-Trace-Id` |

Command:

```bash
mvn -pl nexus-event-core,nexus-p3-studio -am test \
  -Dtest=OutboxEventContractTest,G18BusinessPathIT,FormPublishStateMachineTest \
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

`POST /api/v1/p3/forms/{id}/submissions` also writes the physical row when the M0-06 `LedgerRecordSink` bean exists (JDBC SchemaService ledgers). In-memory form tests stay metadata-only.

## How the path is wired

1. **Publish** remains M0-07 Draft→Validate→Publish (metadata). SchemaService already created the `l_{tenant}_{ledger}` table at ledger create (M0-06).
2. **Submit** maps running `ledger_code`/`field_code` bindings onto `GuardedLedgerStore.insert` (Query AST). No second DDL path.
3. **Start/complete** sequential approval (M0-08 Flowable) updates `approval_status` on that row in the same `@Transactional` method as process/todo writes. Business key = physical record UUID. Domain SQL never reads `act_*`.
4. **Notify** uses `ApprovalNotificationPublisher` → `NotificationService.enqueue` → `OutboxPort.append` destination `notify.inbox` (`OutboxAppendCommand.NOTIFY_INBOX`). `JdbcOutboxStore.append` uses `DataSourceUtils` so the Outbox INSERT joins the business TX.
5. **Audit** uses existing `AuditPort.append` / `AuditCaptures` (data + approval). Details are ids/codes only; field values are not logged. The hash-chain store still commits its own chain-meta lock (M0-11 design), so reject-path audits can survive a business rollback.

Tenant isolation for ledger/outbox is M0-02 `AccessContextHolder` / `RequestIsolationPolicy`. This package does not add another isolation holder. Sequential approval still reads the existing P2 session holder for person/assignee identity (M0-08).

## Flyway

- **V531** (`business_meta.form_submission.ledger_id` / `ledger_record_id`). Window **V530–V549**.
- No `business_data` / `l_*` / `a_*` tables. No new band.

## Assumptions

- One running form binds a single `ledger_code`. The G18 ledger includes optional `approval_status`.
- In-process Flowable 7.1.0 on the application `DataSource` / `PlatformTransactionManager` (already proven in M0-08).
- Demo P3 headers are test-only for ledger/form setup; the path API uses server-issued IAM context.
- `X-Trace-Id` must be a UUID / OTEL trace (`TraceIds.resolve`). The IT uses UUIDv7-shaped ids.
- `nexus.notify.outbox=auto` uses M0-09 `JdbcOutboxStore`. In-memory `RecordingOutboxPort` is only for `outbox=record`.

## Still MOCK / deferred (not claimed)

| Item | Status |
|---|---|
| Vue 台账 / 表单 / 待办 | MOCK adapter default (`VITE_NEXUS_P3_USE_MOCK`) |
| Live UI clicking this path | Not in this package |
| Product-wide G18 / G7 three-business | Open |
| Outbox dispatcher P95 / 30s alarm | M0-09; IT leaves dispatcher off to assert the row |
| Audit hash-chain in the **same** JDBC TX as the business row | Existing `JdbcHashChainedAuditPort` uses a chain lock + own commit |
| Full G4/G6/G17 | Unchanged |
