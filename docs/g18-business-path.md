# G18 one real business path (backend wiring)

Work package: wire **one** vertical path so publish → physical ledger row → sequential approval → Outbox in-app notify → audit hash chain run in the same Spring / PostgreSQL transaction model.  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15, Flowable **7.1.0**, Flyway **13.7.0**. No Docker. Platform versions unchanged.

**This does not close product G18.** Vue now exposes create ledger, form publish, **submit-start**, path todo actions, notifications, and audit-by-trace against live HTTP (`/api/v1/p3/*`; MOCK adapters removed). Eric’s browser walk on `ace55ff` only reached tenant/org/permission snapshot (`grant_count=0` / `no_grant`, and no create/publish/submit-start UI) and is **NOT CLOSED**. Permission matrix UI, H5, and a browser end-to-end walkthrough are still open until Eric re-runs them. **Do not claim product G18 closed.**

## Browser walkthrough on `ace55ff` (not a G18 close)

Eric’s browser path on `main` @ `ace55ff` (stub login「浏览器管理员」, tenant `ui2205`, tenant admin initialized, person ACTIVE, org Root/`ROOT` + position `TENANT_ADMIN`):

| Step reached | Result |
|---|---|
| Stub / browser login + tenant context | Issued `X-Nexus-Context-Id` |
| Tenant create / admin init | Reached |
| Org (person / Root / `TENANT_ADMIN`) | Reached |
| Permission health | `UP` |
| `GET /api/v1/permission/snapshot` | **`grant_count=0`** |
| `POST /api/v1/permission/check` `ledger`/`read` | **`allowed=false`, reason `no_grant`** |
| Ledger records / form publish / sequential approval / todo / notify / audit | **Not reached** — blocked by `no_grant` **and** missing shell buttons |

HTTP E2E / `G18BusinessPathIT` already seeded `tenant_member` because they hit `/api/v1/p3/*` (`IssuedTenantIsolationFilter`). The permission shell used by the browser (`/api/v1/permission/snapshot|check`) went through `PermissionAuthFilter`, which bound TenantContext but **did not seed**. Product G18 is **NOT CLOSED** via browser.

#66 on `main` (`7fffd39`) extends the same seed (`nexus.permission.seed-tenant-member-grants=true`) so `PermissionAuthFilter` and `FlowAuthFilter` assign role `tenant_member` to the current **person id** (not position code `TENANT_ADMIN`) after stub/browser login + issued context. `TenantMemberGrantSeedApiTest` is the IT for that browser-shaped path. It does **not** close product G18.

IT recorded **2026-09-22T14:17:57Z** (agent VM, OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=TenantMemberGrantSeedApiTest,PermissionApiTest,TenantMemberGrantSeederTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`BUILD SUCCESS` — 4 tests. Seed API: stub login + issued context → snapshot `grant_count > 0`; `ledger/read|write`, `form/read`, `process.task` approve/reject allowed (not `no_grant`); assignment is `user` + person id. `PermissionApiTest` still observes `no_grant` with the seed property unset. P3 `IssuedTenantIsolationFilterTest` **5/5** and `PermissionDataScopeHookTest` **4/4**. Product G18 is **not** closed.

## Browser shell (this PR — still not a G18 close)

Previous UI on `ace55ff` listed live P3 resources but had no create/publish/submit-start/notify/audit entry points, so a logged-in tenant user could not drive the backend path even after grants exist. This PR wires the thinnest Vue against APIs already on `main` (#66 / #62 / #60 / #64 / #65):

| Shell gap on `ace55ff` | What this PR adds |
|---|---|
| Ledger list empty, quota 0/10, no create UI | `/p3/ledgers` create application + ledger (`POST /api/v1/p3/applications`, `POST .../ledgers`) |
| Form list empty, no create / validate / publish | `/p3/forms` create DRAFT; studio Validate / Publish |
| Fill copy: form_submission only | Fill calls `POST /api/v1/p3/path/submit-start` (physical `l_*` row + Flowable) |
| Todos empty, no approve | Inbox + `POST /api/v1/p3/path/tasks/{id}/approve\|reject` |
| No sidebar for notify / audit | `/p3/notifications`, `/p3/audit` (`GET /api/v1/notifications`, `GET /api/v1/audit/traces/{traceId}`) |

Do **not** treat this as product G18 closed. Eric must re-run the browser E2E on this branch.

## Runtime gates (ledger 403 / notify 403 / empty `audit_record`)

Fixes for the live path Eric hit. Product G18 is still not closed.

| Symptom | Cause | Fix |
|---|---|---|
| Permission snapshot `grant_count=0`; `ledger/read` check → `no_grant` (browser on `ace55ff`) | Seeder ran only on P3 isolation / data-scope hooks. Browser permission page never called `TenantMemberGrantSeeder.ensure()`. `IamSubjectDirectory` userId is **personId**; assigning a role to position **code** `TENANT_ADMIN` does not match. | Product default `nexus.permission.seed-tenant-member-grants=true` creates role `tenant_member` (ledger/form `read`+`write` ALL, `process.task` approve/reject) assigned to the current **person id**. `PermissionAuthFilter` (snapshot/check), `FlowAuthFilter` (todos/path), and existing P3 hooks all call `ensure()` after TenantContext is bound. |
| `POST/GET /api/v1/p3/ledgers/{id}/records` → 403 `P2.PERM.0003` `no_grant` | `PermissionDataScopeHook` called `PermissionService.check("ledger","read")` which throws when the snapshot has no grant. Javadoc already said tenant-only fallback. | `decide()` + tenant-only fallback for leftover `no_grant` (explicit deny / tenant boundary still 403), plus the person-id seed above. |
| `GET /api/v1/notifications` → 403 `P1.M02.001` | `NotifyIsolationFilter` only binds demo headers (`nexus.notify.demo-context=false` in product). Login `Bearer` + `X-Nexus-Context-Id` never reached the isolation kernel. | Same binder as PR #60: `IssuedTenantIsolationFilter` (`HIGHEST_PRECEDENCE+19`) on `/api/v1/notifications` and `/api/v1/p3/*` except todos. Todos stay `FlowAuthFilter`. Tenant code: P1 `Tenant.code`, else existing `ledger.tenant_code` (G18 IT has no P1 tenant). |
| `platform.audit_record` = 0 rows | Product `nexus.audit.persistence` defaulted to **memory** (`InMemoryHashChainedAuditPort`). | Default is **jdbc**. `AuditPort.append` joins the ambient Spring TX (`DataSourceUtils`); `appendNewTransaction` still commits privately for guard-reject. |

Grant seed is idempotent per tenant. Turn off with `NEXUS_PERMISSION_SEED_TENANT_MEMBER_GRANTS=false` (permission-core ITs stay off so they still observe `no_grant`). Override audit with `NEXUS_AUDIT_PERSISTENCE=memory` if a host has no DataSource.

## Product boot: `OutboxPort` for `NotificationService`

After #62, `nexus.notify.outbox=auto` no longer registers in-memory `RecordingOutboxPort`. Product `NexusOneApplication` must get M0-09 `JdbcOutboxStore` as `OutboxPort`.

`EventCoreConfiguration` used to be a component-scanned `@Configuration` with class-level `@ConditionalOnBean(DataSource)`. Spring Boot evaluates that **before** `DataSourceAutoConfiguration`, so the class was skipped on the monolith: Flyway reached V550, then startup failed because `NotificationService` had no `OutboxPort`.

`G18BusinessPathIT` still passed because `G18PathApplication` **declared** `JdbcOutboxStore` / `OutboxPort` itself. That is not product wiring.

**Fix:** `EventCoreConfiguration` is now `@AutoConfiguration(after = DataSourceAutoConfiguration)` listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. `@ConditionalOnBean(DataSource)` runs after the DataSource exists. Outbox beans take `Clock` via `ObjectProvider` because IAM `clock` and P3 `studioClock` can both exist. The G18 host no longer declares Outbox beans; it uses the same auto-config as the product.

This unblocks empty-DB product boot / E2E. It does **not** close product G18.


## What is proven (runnable)

Recorded on this branch at **2026-09-22T13:39:16Z** (dispatcher SLO) after #64 same-TX audit (**2026-09-22T13:36:12Z**). Agent VM, no Docker. Live `127.0.0.1:5432` was not listening (`NexusOnePostgresTest` / `JdbcAuditChainTest` skipped). G18 IT still leaves the poller **off**. Product G18 is **not** closed.

| Component | Version |
|---|---|
| JDK | OpenJDK **21.0.10** |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky embedded **15.18** |
| Flowable | **7.1.0** in-process |
| Flyway | **13.7.0** through **V550** (incl. **V531**) |

| Suite | Result |
|---|---|
| `JdbcAuditChainTransactionTest` | **4/4** — `append` joins Spring TX; rollback clears record + `next_index`; happy commit intact; `appendNewTransaction` survives |
| `nexus-audit-core` | **11** run, **0** fail, **1** skip (`JdbcAuditChainTest`, no live PG) |
| `EventCoreConfigurationTest` | **5/5** (DS → `JdbcOutboxStore`; no DS → no Outbox; two `Clock` beans still register Outbox; **default starts dispatcher**) |
| `OutboxEventContractTest` | **13/13** (poller delivers; lag alarm; start-of-delivery P95 **238 ms** / 20 samples) |
| `NexusOneOutboxDispatcherTest` | **1/1** — product `NexusOneApplication`, dispatcher **on**, PENDING → DISPATCHED, `notify.inbox` DELIVERED, list readable; `dispatched_at - created_at` **25 ms** |
| `NexusOneProductBootTest` | **1/1** — product `NexusOneApplication` on empty Zonky PG 15 (dispatcher **off** in that wiring check) |
| `G18BusinessPathIT` | **2/2** (dispatcher still off; happy path + rollback includes `audit_record` / chain head) |
| `NotificationApiTest` | **1/1** (`outbox=record` still works without DataSource) |

| Behavior | Evidence |
|---|---|
| After form/ledger publish, submit writes a **physical** `business_data.l_*` row | IT asserts `count(*) = 1` on `l_g18a_leave` (`JdbcSchemaService` DDL only) |
| Logged-in IAM context can **write and query** ledger records (no `P2.PERM.0003`) | `POST/GET /api/v1/p3/ledgers/{id}/records` with Bearer + `X-Nexus-Context-Id` after grant seed / tenant-only fallback |
| In-app notify accepts the same issued context (no `P1.M02.001`) | `GET /api/v1/notifications` with Bearer + `X-Nexus-Context-Id` returns 200 (dispatcher off, so the list may be empty) |
| Approval + that row share one Spring TX | `G18RollbackProbe.submitStartThenFail`: form_submission, physical row, `process_instance`, and `platform.exec_outbox` are all 0 after the forced failure |
| Approval completion enqueues `notify.inbox` via M0-09 Outbox **in the same TX** | `OutboxPort.append` through M0-10 `ApprovalNotificationPublisher` / `NotificationService`; row `destination = notify.inbox`, `status = PENDING`. Dispatcher is **off** in this IT so the row is observed, not a private thread pool. Product boot leaves dispatcher **on** (`NexusOneOutboxDispatcherTest`) |
| Repeat approve is idempotent | Second approve does not insert another Outbox row |
| Actions land on `platform.audit_record` and are queryable by trace | `select count(*) from platform.audit_record where trace_id = ?` ≥ 3; `AuditPort.findByTraceId` returns `p3.path.submitted` / `p3.approval.started` / `p3.approval.approved`; `verifyChain()` intact |
| Forced submit failure rolls back audit + chain head with the business row | `G18RollbackProbe.submitStartThenFail`: form_submission, physical row, process_instance, `exec_outbox`, and `platform.audit_record` for that trace are 0; `audit_chain_meta.next_index` unchanged |

Command:

```bash
mvn -pl nexus-audit-core -am test
mvn -pl nexus-event-core,nexus-notification-core,nexus-p3-studio,nexus-bootstrap -am test \
    -Dtest=EventCoreConfigurationTest,OutboxEventContractTest,NotificationApiTest,G18BusinessPathIT,NexusOneOutboxDispatcherTest,NexusOneProductBootTest,NexusOnePostgresTest \
    -Dsurefire.failIfNoSpecifiedTests=false
```

`BUILD SUCCESS` for the suites listed above (audit same-TX **2026-09-22T13:36:12Z**, event-core **2026-09-22T13:37:34Z**, product dispatcher **2026-09-22T13:39:16Z**, G18 **2026-09-22T13:38:46Z**). Re-run after rebase onto #64 `f1dc329` at **2026-09-22T14:00:18Z**: EventCore **5/5**, OutboxEvent **13/13** (P95 **221 ms**), product dispatcher **1/1** (39 ms), G18 **2/2**. `NexusOnePostgresTest` and `JdbcAuditChainTest` skipped (no local PG listener).

## API (IAM session + M0-02 isolation)

`FlowAuthFilter` binds M0-02 `AccessContextHolder` and the existing P2 session context. Client `X-Tenant-Id` is rejected.

| Method | Path |
|---|---|
| POST | `/api/v1/p3/path/submit-start` |
| POST | `/api/v1/p3/path/tasks/{todoId}/approve` |
| POST | `/api/v1/p3/path/tasks/{todoId}/reject` |
| POST | `/api/v1/p3/path/instances/{instanceId}/withdraw` |
| GET | `/api/v1/notifications` |
| GET | `/api/v1/audit/traces/{traceId}` |

`POST /api/v1/p3/ledgers/{id}/records` (IAM) writes a physical row through the same `GuardedLedgerStore`. `IssuedTenantIsolationFilter` binds the server-issued context for ledger/form/notify; `FlowAuthFilter` stays exclusive for todos / `/api/v1/p3/path`.

## How the path is wired

1. **Publish** remains M0-07 Draft→Validate→Publish (metadata). SchemaService already created the `l_{tenant}_{ledger}` table at ledger create (M0-06).
2. **Submit** maps running `ledger_code`/`field_code` bindings onto `GuardedLedgerStore.insert` (Query AST). No second DDL path.
3. **Start/complete** sequential approval (M0-08 Flowable) updates `approval_status` on that row in the same `@Transactional` method as process/todo writes. Business key = physical record UUID. Domain SQL never reads `act_*`.
4. **Notify** uses `ApprovalNotificationPublisher` → `NotificationService.enqueue` → `OutboxPort.append` destination `notify.inbox` (`OutboxAppendCommand.NOTIFY_INBOX`). `JdbcOutboxStore.append` uses `DataSourceUtils` so the Outbox INSERT joins the business TX. Listing `/api/v1/notifications` uses the same issued context via `IssuedTenantIsolationFilter` (PR #60 contract).
5. **Audit** uses existing `AuditPort.append` / `AuditCaptures` (data + approval) with `nexus.audit.persistence=jdbc` so rows land in `platform.audit_record`. Details are ids/codes only; field values are not logged. `JdbcHashChainedAuditPort.append` obtains the Spring-bound connection via `DataSourceUtils` and does **not** commit privately: the chain-head `SELECT ... FOR UPDATE`, the record INSERT, and the `audit_chain_meta` update join the G18 `@Transactional` method. Guard-reject still uses `appendNewTransaction` (private commit) so it can survive a business rollback. Do not call `appendNewTransaction` from a TX that already holds the chain lock via `append` (two connections on the same `FOR UPDATE` deadlock). Tenant isolation remains M0-02 only.

Tenant isolation for ledger/outbox/notify is M0-02 `AccessContextHolder` / `RequestIsolationPolicy`. `IssuedTenantIsolationFilter` issues that context from login; this package does not add another isolation holder. Sequential approval still reads the existing P2 session holder for person/assignee identity (M0-08).

## Flyway

- **V531** (`business_meta.form_submission.ledger_id` / `ledger_record_id`). Window **V530–V549**.
- No `business_data` / `l_*` / `a_*` tables. No new band.

## Assumptions

- One running form binds a single `ledger_code`. The G18 ledger includes optional `approval_status`.
- In-process Flowable 7.1.0 on the application `DataSource` / `PlatformTransactionManager` (already proven in M0-08).
- Demo P3 headers are test-only for ledger/form **metadata** setup; record write/query, path API, and notify use server-issued IAM context (`Bearer` + `X-Nexus-Context-Id`).
- `nexus.permission.seed-tenant-member-grants=true` seeds role `tenant_member` onto the current **person id** (not position code). Binders: `PermissionAuthFilter`, `FlowAuthFilter`, `IssuedTenantIsolationFilter`, `PermissionDataScopeHook`. `PermissionDataScopeHook` still falls back to tenant-only on leftover `no_grant`.
- `X-Trace-Id` must be a UUID / OTEL trace (`TraceIds.resolve`). The IT uses UUIDv7-shaped ids.
- `nexus.notify.outbox=auto` uses M0-09 `JdbcOutboxStore`. In-memory `RecordingOutboxPort` is only for `outbox=record`.
- `nexus.audit.persistence=jdbc` writes `platform.audit_record`. `append` joins the ambient Spring TX; the chain lock is held until that TX ends (concurrent appends serialize on one meta row). `appendNewTransaction` remains a private commit.

## Still MOCK / deferred (not claimed)

| Item | Status |
|---|---|
| Vue 台账 / 表单 / 待办 | Live HTTP (`/api/v1/p3/*`); create / publish / submit-start / path approve in this PR; MOCK toggle retired |
| Live UI clicking this path | **NOT CLOSED.** On `ace55ff` the browser stopped at permission snapshot (`grant_count=0` / `no_grant`) **and** had no create/publish/submit-start UI. #66 seeds the browser subject; this PR adds the buttons. Eric must re-walk. Still not a tenant→…→audit walkthrough |
| Product-wide G18 / G7 three-business | Open — **do not claim closed** |
| Outbox dispatcher P95 / 30s alarm | Product dispatcher is **on** (`dispatcher-enabled=true`). Delivery + lag alarm: `docs/m0-09-outbox-api.md`, `OutboxEventContractTest`, `NexusOneOutboxDispatcherTest`. G18 IT still leaves dispatcher off to assert PENDING. **Not** a product G18 close |
| Concurrent G18 submits holding the chain lock for the whole business TX | Accepted trade-off vs private commit (keeps gapless hash indexes + rollback atomicity) |
| `appendNewTransaction` from a TX that already called `append` | Deadlock on `audit_chain_meta`; G18 path does not do this |
| Full G4/G6/G17 | Unchanged |
