# M0-06 SchemaService / ledger CRUD evidence

Recorded after PostgreSQL 15.x test run. **Forms / Flowable / UI were not built** in the M0-06 drop.

G2 list/detail/flow/form-studio/form-fill/submit-start/export/Job/Task agreement (`G2SurfaceAgreementTest`, `PermissionDataScopeHook`, `FormSurfaceGuard`, `FormSubmissionWriteApiTest`, `G2SubmitStartFormWriteIT`) is recorded in [docs/g2-permission.md](../docs/g2-permission.md), [docs/g2-form-submission-write.md](../docs/g2-form-submission-write.md), and [docs/g2-submit-start-form-write.md](../docs/g2-submit-start-form-write.md). H5 `/m/*` list/detail/form/todo is [docs/g2-h5-permission.md](../docs/g2-h5-permission.md). Report query/export is [docs/g2-report-surface-guard.md](../docs/g2-report-surface-guard.md). Physical dump / raw export is [docs/g2-physical-dump-surface.md](../docs/g2-physical-dump-surface.md). Report / dump SurfaceGuard honesty leftover is [docs/g2-report-physical-dump-honesty.md](../docs/g2-report-physical-dump-honesty.md). Todo list HTTP is [docs/g2-todo-http-surface.md](../docs/g2-todo-http-surface.md). Form.write / submit-start dual-check honesty leftover is [docs/g2-form-write-honesty.md](../docs/g2-form-write-honesty.md). This file stays the M0-06 ledger evidence. Not product-wide G2.

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

## Post-rebase onto M0-06 (`73416a7`, 2026-09-22T11:38:03Z)

```bash
mvn -pl nexus-p3-studio -am install -DskipTests
mvn -pl nexus-p3-studio test
```

(`-am test` would also run `nexus-p1-tenant` `TenantModuleIT`, which errors without `NEXUS_DB_PASSWORD` on this VM. That is unrelated to M0-07.)

`BUILD SUCCESS` at `2026-09-22T11:38:03Z`. `nexus-p3-studio`: **Tests run: 25, Failures: 0, Errors: 0, Skipped: 4**.

| Suite | Result |
|---|---|
| `FormApiTest` | 1/1 |
| `FormFlywayContractTest` | 1/1 |
| `JdbcLedgerMetadataPortTest` | 2/2 — READY ledger/field codes, no physical names; DRAFT not usable |
| `FormPublishStateMachineTest` | 5/5 |
| `JdbcFormPublishRollbackIT` | 1/1 — Flyway V100→V101→V200→V300→**V400**→**V530** on zonky PostgreSQL **15.18** |
| `BindingValidationTest` | 4/4 |
| `DdlOwnershipTest` | 1/1 (M0-06) |
| `LedgerIdentifiersTest` | 3/3 (M0-06) |
| `SchemaAstTest` | 2/2 (M0-06) |
| `FlywaySqlContractTest` | 1/1 — V400–V499 or V530–V549; no `l_*` / `a_*` |
| `SchemaServicePostgresIT` | 2 skipped (no local PG listener) |
| `LedgerCrudPostgresIT` | 2 skipped (no local PG listener) |

## Honest deferred list

| Item | Why |
|---|---|
| Write to `business_data` on submit | M0-06 CRUD; submissions stay in `business_meta.form_submission` |
| Form designer UI | M0-12 sibling pages |
| Flowable | M0-08 (below) |
| G18 closed | Ledger/todo/approval still open |
| Bootstrap Flyway history on this VM | no local PG 15 listener |

# M0-08 sequential approval evidence

Recorded on the agent VM **2026-09-22T11:13:08Z**, then rebased onto merged M0-06/M0-07 (`ff12b4d`, #58 then #57). Not a target Profile / Linux-ARM / offline-package acceptance. No Docker. No live PostgreSQL 15.19; tests use Zonky embedded **PostgreSQL 15.18**.

## Compatibility spike (hard gate)

| Item | Result |
|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) |
| Maven | **3.9.9** |
| Spring Boot parent | **4.1.1** (Spring Framework 7) |
| Flowable | **7.1.0** (`ProcessEngine.VERSION` = `7.1.0.2`) |
| Same JVM | Yes. `SpringProcessEngineConfiguration` is a Spring bean on the business `DataSource` / `PlatformTransactionManager` |
| PostgreSQL | **15.18** (Zonky `embedded-postgres-binaries-bom`) |
| Flyway | **13.7.0**, window **V550–V599**, history `platform.flyway_schema_history` |
| Engine schema | `flowable.act_*` created by Flowable DDL, not Flyway |
| Business metadata | `business_meta.process_instance` / `process_todo` / `process_action` (V550) |
| Shared TX | Business insert + `runtimeService.startProcessInstance*` roll back together |

**Official starter:** `flowable-spring-boot-starter-process` **7.1.0** is test-scoped only. It still `@AutoConfigureAfter` Boot 3 `org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration`, which is absent in Boot 4.1.1 (`DataSourceAutoConfiguration` moved to `org.springframework.boot.jdbc.autoconfigure`). Product wiring is `flowable-engine` + `flowable-spring`.

**Recommended later pin** if a work package needs the Boot starter: Flowable **8.0.x** (first OSS line declared on Spring Boot 4 / Jackson 3). **Do not replace 7.1.0 in M0** — P0 froze 7.1.0 and the in-process engine path is proven.

## Command

```bash
mvn -pl nexus-p3-studio -am test
```

## Results (pre-rebase M0-08 drop)

`BUILD SUCCESS` at `2026-09-22T11:13:08Z`. Total time 12.905 s.

| Module | Tests | Result |
|---|---|---|
| isolation-kernel | 17 | Failures 0, Errors 0, Skipped 0 |
| nexus-common | 14 | Failures 0, Errors 0, Skipped 0 |
| nexus-p2-iam | 16 | Failures 0, Errors 0, Skipped 2 (live Keycloak / live PG) |
| nexus-permission-core | (module suite) | SUCCESS |
| **nexus-p3-studio** | **11** | Failures 0, Errors 0, Skipped 0 |

`nexus-p3-studio` breakdown (M0-08-only module at that time):

| Class | Tests | Covers |
|---|---|---|
| `Flowable710Boot411CompatibilityIT` | 5 | pins, starter vs Boot 4 auto-config, `flowable.act_*`, sequential user task, joint rollback |
| `SequentialApprovalIT` | 4 | health / reject `X-Tenant-Id`; start→approve/reject/withdraw + 待办/已办/我发起 + dual-tenant; PERSON/POSITION/ROLE; start+engine rollback |
| `NoFlowableInternalTableScanTest` | 2 | no `act_*` SQL in product code; Flyway files only V550–V599 |

Flyway log (SequentialApprovalIT): `Migrating schema "platform" to version "550 - process metadata"` against `PostgreSQL 15.18`. Engine log: `performing create on engine with resource org/flowable/db/create/flowable.postgres.create.engine.sql` into schema `flowable`.

`NexusOnePostgresTest` expects V550 when a live PG is attached; this VM has no `nexusone` database, so that bootstrap test is not counted here.

## Boundaries kept after rebase onto #57/#58

- SchemaService / ledger physical tables remain M0-06 (`JdbcSchemaService`).
- Form Draft→Validate→Publish→Running remains M0-07.
- Domain lists read `business_meta.process_*` only; never `act_*`.
- Tenant from M0-04 issued context; M0-02 `IsolationKernel.tenants().issue` on every mutating/list call; `X-Tenant-Id` → `P3.FLOW.0006`.
- Shared BPMN (`fallbackToDefaultTenant`); instances are not started with Flowable `tenantId`. Isolation is `business_meta.tenant_id`.

## Post-rebase onto #58+#57 (`ff12b4d`, 2026-09-22T11:48:46Z)

```bash
mvn -pl nexus-p3-studio -am install -DskipTests
mvn -pl nexus-p3-studio test
```

`BUILD SUCCESS` at `2026-09-22T11:48:46Z`. `nexus-p3-studio`: **Tests run: 36, Failures: 0, Errors: 0, Skipped: 4**.

| Suite | Result |
|---|---|
| `FormApiTest` | 1/1 — Flowable Boot starter excludes on TestPropertySource |
| `FormFlywayContractTest` | 1/1 |
| `JdbcLedgerMetadataPortTest` | 2/2 |
| `FormPublishStateMachineTest` | 5/5 |
| `JdbcFormPublishRollbackIT` | 1/1 — Flyway V100→V101→V200→V300→**V400**→**V530**→**V550** on zonky PostgreSQL **15.18** |
| `BindingValidationTest` | 4/4 |
| `DdlOwnershipTest` | 1/1 |
| `NoFlowableInternalTableScanTest` | 2/2 — no `act_*` SQL; Flyway windows V400/V530/V550 |
| `Flowable710Boot411CompatibilityIT` | 5/5 — engine create `flowable.postgres.create.engine.sql` |
| `SequentialApprovalIT` | 4/4 — V550 + sequential approve/reject/withdraw + todos |
| `LedgerIdentifiersTest` | 3/3 |
| `SchemaAstTest` | 2/2 |
| `FlywaySqlContractTest` | 1/1 — V400–V499 or V530–V549 or V550–V599 |
| `SchemaServicePostgresIT` | 2 skipped (no local PG listener) |
| `LedgerCrudPostgresIT` | 2 skipped (no local PG listener) |

Coordinator merges when GitHub is CLEAN/MERGEABLE; this drop does not self-merge.

# G5 form publish rollback (forms only)

Failed paths were not in Eric’s G18 browser walk (success-only Validate/Publish). This section is the G5 **forms** evidence. Not a full G5 product close. G18 stays closed. See [docs/g5-form-publish-rollback.md](../docs/g5-form-publish-rollback.md).

Agent VM **2026-09-22T15:27:34Z**. OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**, Zonky PostgreSQL **15.18**. No Docker.

```bash
./scripts/g5-form-publish-rollback.sh
```

`BUILD SUCCESS`. **Tests run: 12, Failures: 0, Errors: 0, Skipped: 0**.

| Suite | Result |
|---|---|
| `JdbcFormPublishRollbackIT` | **3/3** — validate-fail stays DRAFT (no `form_version`); validate-fail after Running leaves pointer + `snapshot_json` unchanged; mid-publish fail leaves RUNNING row + snapshot + `running_version=1`; log `ROLLED_BACK`. Flyway through **V530** on PG 15.18 |
| `FormPublishFailureApiTest` | **2/2** — HTTP `P3.FORM.0005` → DRAFT; HTTP `P3.FORM.0006` → Running v1 snapshot unchanged + `ROLLED_BACK` |
| `FormPublishStateMachineTest` | **7/7** |

`FormApiTest` **1/1** at **2026-09-22T15:27:49Z** (happy path plus HTTP validate-fail).

# G5 tighten — SchemaService / DDL abort (forms only)

Extends #70. Product publish now defaults to `SchemaServicePublishHook` → `SchemaService.confirmPhysicalTable` when SchemaService is on the classpath (not a second DDL path). Mid-publish evidence adds:

- Zonky PG 15: drop physical table after Running v1 → confirm miss → pointer/snapshot unchanged
- Zonky PG 15: `JdbcSchemaService.evolvePhysicalTable` hits PostgreSQL `42701` (pre-created `drift_col`) → pointer/snapshot unchanged
- HTTP / memory: same `SchemaServicePublishHook` + evolve-failed shape

Recorded **2026-09-22T15:42:29Z** (17 tests). Superseded for the product gate by the G5 remainder below.

# G5 remainder — SchemaService evolve/CREATE on publish (forms only)

Extends #70 / #85. Product `SchemaServicePublishHook` now calls `SchemaService.ensurePhysicalTable` inside the publish TX:

- Missing physical table → `createPhysicalTable`
- Declared field not on the physical table → `evolvePhysicalTable`
- No drift → `confirmPhysicalTable` (G18 leave publish stays on this path)

Mid-evolve failure still rolls back the Running pointer / snapshot and writes the existing `ROLLED_BACK` log. Abort evidence is a required new column on a table that already has a row (PostgreSQL `23502`), not a `ConfirmThenAbortEvolve` wrapper.

Agent VM **2026-09-22T16:44:54Z**. OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**, Zonky PostgreSQL **15.18**. No Docker.

```bash
./scripts/g5-form-publish-rollback.sh
```

`BUILD SUCCESS`. **Tests run: 18, Failures: 0, Errors: 0, Skipped: 0**.

| Suite | Result |
|---|---|
| `JdbcFormPublishRollbackIT` | **6/6** — #70 cases plus product CREATE (drop `l_g5a_leave_request`), evolve `remark` success (`schema_version=2`), evolve abort (required `remark` + row) leaves pointer + `snapshot_json` + no `remark` column + `ROLLED_BACK` |
| `FormPublishFailureApiTest` | **2/2** — HTTP via `SchemaServicePublishHook` |
| `FormPublishStateMachineTest` | **8/8** |
| `SchemaServicePublishHookTest` | **2/2** |

`FormApiTest` **1/1**. G18 leave publish `publishSubmitApprovalOutboxAndAuditShareOnePath` **1/1** at **2026-09-22T16:45:39Z**. Not a full G5 close. See [docs/g5-form-publish-rollback.md](../docs/g5-form-publish-rollback.md).

# G5 leftover — mid-publish failure beyond evolve DDL abort (forms only)

Extends #70 / #85 / #94. Does **not** claim a product G5 close. `AuditPort` untouched. G18 stays closed.

Adds mid-publish cases that keep Running v1 + `snapshot_json` after a failed v2:

- Zonky PG 15: `DROP` physical table + same-name `SEQUENCE` → product `createPhysicalTable` `42P07` → pointer/snapshot unchanged
- Zonky PG 15: optional `remark` evolve **succeeds** in the publish TX, then hook abort → column rolled back + pointer/snapshot unchanged
- HTTP / memory: `SchemaServicePublishHook` finishes `ensurePhysicalTable`, then aborts

See [docs/g5-mid-publish-failure.md](../docs/g5-mid-publish-failure.md).

Browser failed-publish click / PublishHook honesty leftover (live browser/IdP/JAR SKIP; not a product G5 close; not a #94/#145 redo): [docs/g5-browser-failed-publish.md](../docs/g5-browser-failed-publish.md).

Mid-publish / browser failed-publish honesty leftover (memory harness PASS; live browser/IdP/JAR SKIP; not a product G5 close; not a #94/#145/#171 redo): [docs/g5-mid-publish-failure-honesty.md](../docs/g5-mid-publish-failure-honesty.md).

PublishHook / real SchemaService DDL honesty leftover (memory harness is not real DDL; live PG 15 / PublishHook SKIP; not a product G5 close; not a #94/#145/#171/#196 redo): [docs/g5-publishhook-ddl-honesty.md](../docs/g5-publishhook-ddl-honesty.md).

Recorded **pending-this-run**. `./scripts/g5-form-publish-rollback.sh` → **pending**. No Docker.

# G18 one real business path (backend)

See `docs/g18-business-path.md`. Product G18 is **closed** by the 2026-09-22 browser walk on `main` `38f1a73`. The paragraphs below are the earlier VM and `ace55ff` record.

Eric’s browser on `ace55ff`: stub login「浏览器管理员」, tenant `ui2205`, tenant/org/permission snapshot only. Health UP, **`grant_count=0`**, `ledger/read` **`no_grant`**. Further G18 steps were not reached on that commit. Superseded by the `38f1a73` walk.

Wire: form submit → SchemaService physical `l_*` row → sequential approval in the same Spring TX → `OutboxPort.append` destination `notify.inbox` → M0-11 `AuditPort.findByTraceId`.

Flyway **V531** (window V530–V549) adds `form_submission.ledger_id` / `ledger_record_id`. No `business_data` / `l_*` DDL.

## Runnable evidence (this branch)

Agent VM, **2026-09-22T13:39:16Z** (dispatcher SLO) after #64 same-TX audit (**2026-09-22T13:36:12Z**). OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**, Zonky PostgreSQL **15.18**, Flowable **7.1.0**. No Docker. No live `127.0.0.1:5432`.

```bash
mvn -pl nexus-audit-core -am test
mvn -pl nexus-event-core,nexus-notification-core,nexus-p3-studio,nexus-bootstrap -am test \
  -Dtest=EventCoreConfigurationTest,OutboxEventContractTest,NotificationApiTest,G18BusinessPathIT,NexusOneOutboxDispatcherTest,NexusOneProductBootTest,NexusOnePostgresTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`BUILD SUCCESS`. `JdbcAuditChainTransactionTest` **4/4**. `nexus-audit-core` **11** run / **1** skip (`JdbcAuditChainTest`). Dispatcher wave: `EventCoreConfigurationTest` **5/5**; `OutboxEventContractTest` **13/13** (P95 **238 ms**); `NexusOneOutboxDispatcherTest` **1/1** (product poller, start-of-delivery **25 ms**, inbox DELIVERED). `G18BusinessPathIT` **2/2** (dispatcher still off; happy path + audit/chain-head rollback). Product G18 close is the later browser record, which cites this run.

G7 three types + withdraw (this branch): `BusinessTypeCatalogTest` + `G7BusinessTypesIT`. See `docs/g7-business-types.md`. Not a full S0+S1 close.

Proven vs still MOCK: physical `l_g18a_leave` row after publish/submit; IAM `POST/GET` ledger records without `P2.PERM.0003`; `GET /api/v1/notifications` with issued context without `P1.M02.001`; approval+record+outbox **and** `platform.audit_record` / `audit_chain_meta` roll back together; `platform.exec_outbox` `destination=notify.inbox` PENDING in the G18 IT (dispatcher off); product boot delivers that destination to DISPATCHED / `notify.inbox` DELIVERED; happy-path `platform.audit_record` rows for the path trace (`p3.path.submitted` / `p3.approval.started` / `p3.approval.approved`) with `verifyChain()` intact. Product `OutboxPort` is M0-09 `EventCoreConfiguration` auto-config (`JdbcOutboxStore` after DataSource). Vue now exposes create ledger / form publish / submit-start / path todos / notify / audit-by-trace. The browser loop was re-run on `38f1a73` and closed product G18; see `docs/g18-business-path.md`.

# G3 submit-start idempotency + record versions

See [docs/g3-data-consistency.md](../docs/g3-data-consistency.md). Outbox consumer idempotency is **not** rebuilt; it reuses the M0-09 measurements above (`docs/m0-09-outbox-api.md`). Consume-idempotent **stress / duplicate-delivery** leftover is [docs/g3-outbox-consume-idempotent.md](../docs/g3-outbox-consume-idempotent.md) (not a product G3 close).

| Case | Test | Expected |
|---|---|---|
| Same `Idempotency-Key` on `submit-start` | `G18BusinessPathIT.repeatedSubmitStartWithSameKeyDoesNotCreateSecondRow` | One physical row, one submission, one process, `idempotent_replay=true` on the second call |
| Failed submit rolls back the claim | `G18BusinessPathIT.approvalAndLedgerRowRollBackTogether` | `platform.exec_idempotency` count 0 for `g18-rollback-key` |
| Stale / concurrent `version` | `G18BusinessPathIT.concurrentRecordEditConflictsOnVersion` | `409 P3.RECORD.0002`; concurrent pair is 200 + 409 |
| Key shape | `G3IdempotencyKeyTest` | Tenant prefix; overlong key rejected |

Command:

```bash
mvn -pl nexus-p3-studio -am test -Dtest=G3IdempotencyKeyTest,G18BusinessPathIT
mvn -pl nexus-event-core -am test -Dtest=OutboxEventContractTest,EventCoreConfigurationTest
```

Recorded on this agent **2026-09-22T15:31:06Z** (G3/G18) and **2026-09-22T15:31:42Z** (Outbox reuse). OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**, Zonky PostgreSQL **15.18**. No Docker.

| Suite | Result |
|---|---|
| `G3IdempotencyKeyTest` | **2/2** (0.051 s) |
| `G18BusinessPathIT` | **4/4** (7.028 s) — original happy path + rollback, plus duplicate submit-start and version conflict |
| `OutboxEventContractTest` | **13/13** (not rebuilt) |
| `EventCoreConfigurationTest` | **5/5** (not rebuilt) |
| Reactor | **BUILD SUCCESS** 34.167 s / 3.890 s |

`repeatedSubmitStartWithSameKeyDoesNotCreateSecondRow` (0.070 s): second `submit-start` with the same `Idempotency-Key` returned the same `record_id` / `submission_id` and `idempotent_replay=true`; physical table, `form_submission`, `process_instance`, and `exec_idempotency` counts stayed **1**.

`concurrentRecordEditConflictsOnVersion` (0.067 s): PATCH with stale `version` → **409 `P3.RECORD.0002`**; two concurrent PATCHes on the same version → one **200** and one **409**; winner bumped `version` 2 → 3.

`approvalAndLedgerRowRollBackTogether`: `g18-rollback-key` left **0** rows in `platform.exec_idempotency`.

# G7 withdraw / reject walkthrough (evidence, not a new class)

See [docs/g7-business-types.md](../docs/g7-business-types.md). This is dual-tenant HTTP evidence for **withdraw** (travel-shaped) and **reject** on the existing G18 path. It does not add a catalog. `AuditPort` is unchanged. The leave/expense/travel catalog landed on `main` via **#69**.

Agent VM **2026-09-22T15:40:16Z**. OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**, Zonky PostgreSQL **15.18**. No Docker. No live `127.0.0.1:5432`.

```bash
./scripts/g7-withdraw-walkthrough.sh
```

`BUILD SUCCESS`. **Tests run: 3, Failures: 0, Errors: 0, Skipped: 0**.

| Suite | Result |
|---|---|
| `G7WithdrawWalkthroughIT` | **1/1** — two tenants; travel-shaped `l_*` via M0-06/07; A withdraw `WITHDRAWN` + `destination=上海` + idempotent replay; B withdraw `P3.FLOW.0002`; B cannot read A's row; A reject `REJECTED` + `destination=北京`; audit `p3.approval.withdrawn` / `p3.approval.rejected`; `verifyChain()` intact |
| `G18BusinessPathIT` | **2/2** regression |
| `web npm run assert:p3-live` | **PASS** (`data-testid=todo-withdraw`) |

G7 leftover Vue click of that control (stub login, not a three-type close, G18 stays closed): [docs/g7-withdraw-browser.md](../docs/g7-withdraw-browser.md). Expense/travel list/detail/todo query leftover (not a three-type close, G18 stays closed): [docs/g7-query-expense-travel.md](../docs/g7-query-expense-travel.md). Query honesty leftover (query surfaces PASS; live JAR/IdP SKIP): [docs/g7-query-expense-travel-honesty.md](../docs/g7-query-expense-travel-honesty.md).

# G2 submit-start form.write dual-check

Path `POST /api/v1/p3/path/submit-start` with `form_id` requires `form.write` before start, then `process.instance`/`start`. See [docs/g2-submit-start-form-write.md](../docs/g2-submit-start-form-write.md).

Agent VM after rebase onto `main@440a504` (**#113**). **2026-09-22T17:43:30Z**. OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**, Zonky PostgreSQL **15.18**. No Docker.

```bash
./scripts/g2-submit-start-form-write.sh
```

`G2SubmitStartFormWriteIT` **3/3** — missing `form.write` is `403 P2.PERM.0003` with zero `form_submission` / process / physical row; `tenant_member` seed cells still start leave; explicit deny is `P2.PERM.0002`. `G18BusinessPathIT` **4/4**. Reactor **Tests run: 20, Failures: 0** at **2026-09-22T17:43:30Z**.

