# G5 发布回滚（表单）

门禁 G5（需求规格 V1.1 §12）：**Draft/Running、校验、失败回滚可用**。  
本记录只覆盖 **P3 表单发布**。不重做 P0/M0 包，不改 G7 业务路径、G2 权限、G4 审计、G1 隔离。产品 G18 保持关闭（浏览器成功发布见 `docs/g18-business-path.md`）。

Eric 的 G18 浏览器走通只观察到 **校验通过 + 发布成功**（`leave2231_form` Validate PASS，`running_version=1`）。失败路径不在那次点击里，以这里的 IT / 脚本为准。

## 口径

| 失败路径 | 必须保持 |
|---|---|
| `POST /api/v1/p3/forms/{id}/validate` 失败（`P3.FORM.0005`） | `draft_status=DRAFT`；不写 `form_version`；已有 Running 指针与 snapshot 不变 |
| `POST /api/v1/p3/forms/{id}/publish` 中途失败（`SchemaService` evolve DDL abort / `PublishHook` → `P3.FORM.0006`） | 事务回滚；`form.running_version`、`form_version` RUNNING 行、`snapshot_json` 与失败前一致；`form_publish_log.outcome=ROLLED_BACK` |

实现仍是 M0-07 `FormService`：校验在写 VALIDATED 之前抛错；发布在 `PlatformTransactionManager` / `runAtomically` 里切换指针，失败后补一条 ROLLED_BACK 日志。

产品路径在 SchemaService + ledger/field 仓储存在时，默认 `PublishHook` 是 `SchemaServicePublishHook`：在写入 PUBLISHING version 之后、切换 Running 指针之前调用 `SchemaService.ensurePhysicalTable`。物理表不在则 `createPhysicalTable`；台账已声明、物理列还没有的字段走 `evolvePhysicalTable`。无 drift 时只 `confirmPhysicalTable`（G18 请假成功发布仍走这条）。DDL 仍只由 SchemaService 执行。

中途失败证据分三层，都不断言完整 G5：

| 层 | 失败怎么来 | 覆盖 |
|---|---|---|
| 产品扇门 + 现场 SchemaService CREATE | 发布前 `DROP` 物理表；第一次 publish 走 `createPhysicalTable`，表回来，`running_version=1` | `JdbcFormPublishRollbackIT.schemaServiceCreateOnMissing…` |
| 产品扇门 + 现场 SchemaService evolve 成功 | v1 发布后往 `ledger_field` 插入 `remark`（未事先 ALTER）；v2 publish 走 `evolvePhysicalTable`，物理列出现，`schema_version=2` | `JdbcFormPublishRollbackIT.schemaServiceEvolveNewField…` |
| 产品扇门 + 现场 SchemaService evolve abort | v1 后插入一行业务数据，再声明 **必填** `remark`；v2 publish 的 `ADD COLUMN … NOT NULL` 被 PG 15 拒绝（已有行会是 null，SQLState `23502`），`IllegalStateException("SchemaService evolve failed…")` | `JdbcFormPublishRollbackIT.schemaServiceEvolveAbort…` |
| 回归 / HTTP | 纯 `PublishHook` 抛错；HTTP 与内存态机走 `SchemaServicePublishHook` + 同形状的 evolve-failed 异常 | 既有 hook IT；`FormPublishFailureApiTest`；`FormPublishStateMachineTest` |
| leftover：产品 CREATE abort（Running 已在） | v1 后 `DROP` 物理表，再种同名 `SEQUENCE`；v2 `createPhysicalTable` 被 PG 15 `42P07` 拒绝 | `JdbcFormPublishRollbackIT.schemaServiceCreateAbortAfterRunning…` |
| leftover：SchemaService 成功后再 abort | v1 后 evolve 可选 `remark` 在发布事务里成功，随后 hook 抛错；DDL 与指针一起回滚 | `JdbcFormPublishRollbackIT.schemaServiceWorkThenMidPublishAbort…`；HTTP/内存 leftover 见 [g5-mid-publish-failure.md](./g5-mid-publish-failure.md) |

产品路径没有单独的「失败发布」HTTP 开关。evolve abort 是真实 SchemaService DDL（必填新列 + 已有行），不是 `ConfirmThenAbortEvolve` 包装、也不是现场产品开关。CREATE abort leftover 是同名 sequence 挡住 `CREATE TABLE`。**不宣称完整 G5。**

## 如何跑

基线：Java 21、Spring Boot **4.1.1**、PostgreSQL **15**（Zonky 嵌入式 **15.18**）、无 Docker。

```bash
./scripts/g5-form-publish-rollback.sh
```

等价：

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=JdbcFormPublishRollbackIT,FormPublishFailureApiTest,FormPublishStateMachineTest,SchemaServicePublishHookTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| Suite | 覆盖 |
|---|---|
| `JdbcFormPublishRollbackIT` | Zonky PG 15：校验失败保持 DRAFT / 不写 version；已发布后再校验失败指针与 snapshot 不变；Hook 回归；**SchemaService CREATE**（表缺失）与 **evolve 新列成功**；**evolve DDL abort**（必填 `remark` + 已有行，`23502`）；**leftover CREATE abort**（同名 sequence，`42P07`）与 **leftover evolve-then-abort**（`remark` 列回滚）后 `running_version`、RUNNING 行、`snapshot_json` 与失败前相等 + `ROLLED_BACK` |
| `FormPublishFailureApiTest` | HTTP：`P3.FORM.0005` → DRAFT；leftover `P3.FORM.0006` 经 `ensurePhysicalTable` 后再 abort → Running v1 + snapshot 不变 + `ROLLED_BACK` |
| `FormPublishStateMachineTest` | 内存状态机，Hook 回归 + SchemaService 扇门 abort + leftover 成功后再 abort + 成功路径 |
| `SchemaServicePublishHookTest` | 每 ledger 一次 `ensurePhysicalTable`；evolve-failed 原样抛出；abort-after-ensure 原样抛出 |

## 本次证据

Agent VM **2026-09-22T16:44:54Z**. `./scripts/g5-form-publish-rollback.sh` → `BUILD SUCCESS`. No Docker.

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** (IT asserts `server_version` starts with `15.`) |
| Flyway | V100→…→**V530**→V531→V550 on schema `platform` |
| Result | **Tests run: 18, Failures: 0, Errors: 0, Skipped: 0** |

| Suite | Result |
|---|---|
| `JdbcFormPublishRollbackIT` | **6/6** — #70 validate/hook cases; `schemaServiceCreateOnMissingPhysicalTableSucceeds` (DROP `l_g5a_leave_request` then product CREATE); `schemaServiceEvolveNewFieldOnPublishSucceeds` (`remark` column + `schema_version=2`); `schemaServiceEvolveAbortLeavesRunningPointerAndSnapshotUnchanged` (required `remark` + existing row, cause `SchemaService evolve failed`, column absent, `ROLLED_BACK`) |
| `FormPublishFailureApiTest` | **2/2** — HTTP `P3.FORM.0005` keeps DRAFT; HTTP `P3.FORM.0006` via `SchemaServicePublishHook` leaves Running v1 + snapshot + `ROLLED_BACK` |
| `FormPublishStateMachineTest` | **8/8** — Hook regression + SchemaService-gate abort + success / tenant cases |
| `SchemaServicePublishHookTest` | **2/2** — one `ensurePhysicalTable` per ledger; evolve-failed shape |

`FormApiTest` **1/1** at **2026-09-22T16:45:00Z**. G18 leave publish `G18BusinessPathIT#publishSubmitApprovalOutboxAndAuditShareOnePath` **1/1** at **2026-09-22T16:45:39Z**.

## 诚实缺口（不宣称完整 G5 产品）

| 项 | 状态 |
|---|---|
| 表单 Draft / Validate / Publish / Running 成功路径 | 已有；G18 浏览器走过成功发布 |
| 表单校验失败 → DRAFT | **本包 IT / HTTP** |
| 表单发布中途失败 → Running / snapshot 不变 | **本包 IT / HTTP**。产品扇门是 `SchemaService.ensurePhysicalTable`（CREATE / evolve / confirm）。evolve abort 是必填新列 + 已有行，经现场 `JdbcSchemaService` + PG 15 `23502`。leftover 见 [g5-mid-publish-failure.md](./g5-mid-publish-failure.md)：CREATE abort（`42P07`）与 SchemaService 成功后再 abort（DDL 回滚）。都不是产品「失败发布」开关 |
| 浏览器再点一次失败发布 | 诚实 leftover [g5-browser-failed-publish.md](./g5-browser-failed-publish.md)。PASS/SKIP leftover [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md)。PublishHook / 真实 SchemaService DDL leftover [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md)。Publish remainder leftover [g5-publishhook-ddl-remainder-honesty.md](./g5-publishhook-ddl-remainder-honesty.md)。活 browser/IdP/JAR / 活 PG 15 不可用时 SKIP。UI「查看 publish-log」只读日志，不模拟失败。不宣称产品 G5 关闭 |
| 应用包 / P4 交付发布 | 不在本包 |
| 表单设计器 | M0-12 壳，不是设计器产品 |
| G7 / G2 / G4 / G1 | 并行包，这里不关 |
| G18 | **保持关闭** |
