# G5 发布回滚（表单）

门禁 G5（需求规格 V1.1 §12）：**Draft/Running、校验、失败回滚可用**。  
本记录只覆盖 **P3 表单发布**。不重做 P0/M0 包，不改 G7 业务路径、G2 权限、G4 审计、G1 隔离。产品 G18 保持关闭（浏览器成功发布见 `docs/g18-business-path.md`）。

Eric 的 G18 浏览器走通只观察到 **校验通过 + 发布成功**（`leave2231_form` Validate PASS，`running_version=1`）。失败路径不在那次点击里，以这里的 IT / 脚本为准。

## 口径

| 失败路径 | 必须保持 |
|---|---|
| `POST /api/v1/p3/forms/{id}/validate` 失败（`P3.FORM.0005`） | `draft_status=DRAFT`；不写 `form_version`；已有 Running 指针与 snapshot 不变 |
| `POST /api/v1/p3/forms/{id}/publish` 中途失败（`PublishHook` / 运行时异常 → `P3.FORM.0006`） | 事务回滚；`form.running_version`、`form_version` RUNNING 行、`snapshot_json` 与失败前一致；`form_publish_log.outcome=ROLLED_BACK` |

实现仍是 M0-07 `FormService`：校验在写 VALIDATED 之前抛错；发布在 `PlatformTransactionManager` / `runAtomically` 里切换指针，失败后补一条 ROLLED_BACK 日志。

中途失败用 `PublishHook.beforeActivate` 注入（与既有模块测试同一探针）。产品路径没有单独的「失败发布」HTTP 开关。

## 如何跑

基线：Java 21、Spring Boot **4.1.1**、PostgreSQL **15**（Zonky 嵌入式 **15.18**）、无 Docker。

```bash
./scripts/g5-form-publish-rollback.sh
```

等价：

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=JdbcFormPublishRollbackIT,FormPublishFailureApiTest,FormPublishStateMachineTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| Suite | 覆盖 |
|---|---|
| `JdbcFormPublishRollbackIT` | Zonky PG 15：校验失败保持 DRAFT / 不写 version；已发布后再校验失败指针与 snapshot 不变；第二次 publish 失败后 `running_version`、RUNNING 行、`snapshot_json` 与失败前相等 |
| `FormPublishFailureApiTest` | HTTP：`P3.FORM.0005` → DRAFT；`P3.FORM.0006` → Running v1 + snapshot 不变 + `ROLLED_BACK` |
| `FormPublishStateMachineTest` | 内存状态机，同上两条 + 成功路径 |

## 本次证据

Agent VM **2026-09-22T15:27:34Z**. `./scripts/g5-form-publish-rollback.sh` → `BUILD SUCCESS`. No Docker.

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** (`starting PostgreSQL 15.18`; IT asserts `server_version` starts with `15.`) |
| Flyway | V100→…→**V530**→V531→V550 on schema `platform` |
| Result | **Tests run: 12, Failures: 0, Errors: 0, Skipped: 0** |

| Suite | Result |
|---|---|
| `JdbcFormPublishRollbackIT` | **3/3** — `validateFailureKeepsDraftAndDoesNotWriteVersionOnPostgres15`; `validateFailureAfterRunningLeavesPointerAndSnapshotUnchanged`; `failedPublishLeavesRunningPointerAndSnapshotUnchangedOnPostgres15` |
| `FormPublishFailureApiTest` | **2/2** — HTTP `P3.FORM.0005` keeps DRAFT; HTTP `P3.FORM.0006` leaves Running v1 + snapshot + `ROLLED_BACK` |
| `FormPublishStateMachineTest` | **7/7** — same two failed paths in memory, plus the existing success / tenant cases |

`FormApiTest` (happy path + HTTP validate-fail) re-run **1/1** at **2026-09-22T15:27:49Z**.

## 诚实缺口（不宣称完整 G5 产品）

| 项 | 状态 |
|---|---|
| 表单 Draft / Validate / Publish / Running 成功路径 | 已有；G18 浏览器走过成功发布 |
| 表单校验失败 → DRAFT | **本包 IT / HTTP** |
| 表单发布中途失败 → Running / snapshot 不变 | **本包 IT / HTTP**（Hook 注入，不是现场 SchemaService DDL 崩溃） |
| 浏览器再点一次失败发布 | **未做**。UI「查看 publish-log」只读日志，不模拟失败 |
| 应用包 / P4 交付发布 | 不在本包 |
| 表单设计器 | M0-12 壳，不是设计器产品 |
| G7 / G2 / G4 / G1 | 并行包，这里不关 |
| G18 | **保持关闭** |
