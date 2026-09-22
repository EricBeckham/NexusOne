# G5 发布回滚（表单）

门禁 G5（需求规格 V1.1 §12）：**Draft/Running、校验、失败回滚可用**。  
本记录只覆盖 **P3 表单发布**。不重做 P0/M0 包，不改 G7 业务路径、G2 权限、G4 审计、G1 隔离。产品 G18 保持关闭（浏览器成功发布见 `docs/g18-business-path.md`）。

Eric 的 G18 浏览器走通只观察到 **校验通过 + 发布成功**（`leave2231_form` Validate PASS，`running_version=1`）。失败路径不在那次点击里，以这里的 IT / 脚本为准。

## 口径

| 失败路径 | 必须保持 |
|---|---|
| `POST /api/v1/p3/forms/{id}/validate` 失败（`P3.FORM.0005`） | `draft_status=DRAFT`；不写 `form_version`；已有 Running 指针与 snapshot 不变 |
| `POST /api/v1/p3/forms/{id}/publish` 中途失败（`SchemaService` confirm / evolve DDL abort / `PublishHook` → `P3.FORM.0006`） | 事务回滚；`form.running_version`、`form_version` RUNNING 行、`snapshot_json` 与失败前一致；`form_publish_log.outcome=ROLLED_BACK` |

实现仍是 M0-07 `FormService`：校验在写 VALIDATED 之前抛错；发布在 `PlatformTransactionManager` / `runAtomically` 里切换指针，失败后补一条 ROLLED_BACK 日志。

产品路径在 SchemaService + ledger/field 仓储存在时，默认 `PublishHook` 是 `SchemaServicePublishHook`：在写入 PUBLISHING version 之后、切换 Running 指针之前调用 `SchemaService.confirmPhysicalTable`（只确认物理表已在，**不** CREATE/ALTER）。G18 成功发布走同一扇门。

中途失败证据分三层，都不断言完整 G5：

| 层 | 失败怎么来 | 覆盖 |
|---|---|---|
| 产品扇门 + 现场 PG | 发布后再 `DROP` 物理表，第二次 publish 的 `confirmPhysicalTable` 看到表不在 | `JdbcFormPublishRollbackIT.schemaServiceConfirmMiss…` |
| 产品扇门 + 现场 SchemaService DDL | 第二次 `confirm` 之后走 `JdbcSchemaService.evolvePhysicalTable`；IT 预先 `ADD COLUMN drift_col`，PG 15 拒绝重复列（SQLState `42701`），`IllegalStateException("SchemaService evolve failed…")` | `JdbcFormPublishRollbackIT.schemaServiceDdlAbort…` |
| 回归 / HTTP | 纯 `PublishHook` 抛错；HTTP 与内存态机改走 `SchemaServicePublishHook` + 同形状的 evolve-failed 异常 | 既有 hook IT；`FormPublishFailureApiTest`；`FormPublishStateMachineTest` |

产品路径没有单独的「失败发布」HTTP 开关。DDL abort IT 仍是测试安排的碰撞列，不是现场产品开关。

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
| `JdbcFormPublishRollbackIT` | Zonky PG 15：校验失败保持 DRAFT / 不写 version；已发布后再校验失败指针与 snapshot 不变；Hook 回归；**SchemaService confirm miss** 与 **SchemaService evolve DDL abort**（`42701`）后 `running_version`、RUNNING 行、`snapshot_json` 与失败前相等 |
| `FormPublishFailureApiTest` | HTTP：`P3.FORM.0005` → DRAFT；`P3.FORM.0006` 经 `SchemaServicePublishHook` → Running v1 + snapshot 不变 + `ROLLED_BACK` |
| `FormPublishStateMachineTest` | 内存状态机，Hook 回归 + SchemaService 扇门 abort + 成功路径 |
| `SchemaServicePublishHookTest` | 每 ledger 一次 confirm；evolve-failed / `42701` 原样抛出 |

## 本次证据

Agent VM **2026-09-22T15:42:29Z**. `./scripts/g5-form-publish-rollback.sh` → `BUILD SUCCESS`. No Docker.

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** (IT asserts `server_version` starts with `15.`) |
| Flyway | V100→…→**V530**→V531→V550 on schema `platform` |
| Result | **Tests run: 17, Failures: 0, Errors: 0, Skipped: 0** |

| Suite | Result |
|---|---|
| `JdbcFormPublishRollbackIT` | **5/5** — previous three #70 cases; `schemaServiceConfirmMissLeavesRunningPointerAndSnapshotUnchanged` (drop `l_g5a_leave_request` after v1); `schemaServiceDdlAbortLeavesRunningPointerAndSnapshotUnchanged` (`JdbcSchemaService.evolvePhysicalTable` + pre-created `drift_col`, cause `SchemaService evolve failed`) |
| `FormPublishFailureApiTest` | **2/2** — HTTP `P3.FORM.0005` keeps DRAFT; HTTP `P3.FORM.0006` via `SchemaServicePublishHook` leaves Running v1 + snapshot + `ROLLED_BACK` |
| `FormPublishStateMachineTest` | **8/8** — Hook regression + SchemaService-gate abort + success / tenant cases |
| `SchemaServicePublishHookTest` | **2/2** — one confirm per ledger; evolve-failed / SQLState `42701` |

`FormApiTest` (happy path + HTTP validate-fail) re-run **1/1** at **2026-09-22T15:42:48Z**.

## 诚实缺口（不宣称完整 G5 产品）

| 项 | 状态 |
|---|---|
| 表单 Draft / Validate / Publish / Running 成功路径 | 已有；G18 浏览器走过成功发布 |
| 表单校验失败 → DRAFT | **本包 IT / HTTP** |
| 表单发布中途失败 → Running / snapshot 不变 | **本包 IT / HTTP**。产品扇门是 `SchemaService.confirmPhysicalTable`。DDL abort 是 IT 安排的 `evolvePhysicalTable` + 预置碰撞列，经现场 `JdbcSchemaService` + PG 15 `42701`，不是产品「失败发布」开关 |
| 浏览器再点一次失败发布 | **未做**。UI「查看 publish-log」只读日志，不模拟失败 |
| 应用包 / P4 交付发布 | 不在本包 |
| 表单设计器 | M0-12 壳，不是设计器产品 |
| G7 / G2 / G4 / G1 | 并行包，这里不关 |
| G18 | **保持关闭** |
