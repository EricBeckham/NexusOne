# M0-07 基础表单与发布

工作包：表单元数据、Draft→Validate→Publish→Running、失败回滚。  
基线：Java 21、Spring Boot **4.1.1**、PostgreSQL 15、Flyway **13.7.0**。不依赖 Docker。  
**不包含** Flowable（M0-08）和表单设计器 UI（M0-12）。

## M0-06 元数据契约（#58 已合并）

M0-06 拥有应用 / 台账 / 字段元数据和 SchemaService 物理表。本包 **只绑定逻辑码**：

| 绑定 | 允许 | 禁止 |
|---|---|---|
| 台账 | `ledger_code` | `l_{tenant}_{ledger}` / `a_{app}_{ledger}` 物理表名、`table_name` |
| 字段 | `field_code` | `column_name` / `physical_column` / 任意 PG 列名 |

SPI：`one.nexus.p3.studio.meta.LedgerMetadataPort`

```text
Optional<LedgerRef> findLedger(tenantId, ledgerCode)
Optional<FieldRef>  findField(tenantId, ledgerCode, fieldCode)
```

`LedgerRef` / `FieldRef` **不含**物理表名或列名。当 M0-06 `LedgerRepository` / `FieldRepository` 存在时，默认 bean 是 `JdbcLedgerMetadataPort`（只暴露逻辑码；`usable` = ledger `READY`）。无 JDBC 仓储时回退 `InMemoryLedgerMetadataCatalog`（`nexus.studio.seed-demo-metadata=true` 预置 `leave_request`）。

发布后的业务行仍由 M0-06 SchemaService / `GuardedLedgerStore` 写入 `business_data`。填报默认写 `business_meta.form_submission`；当 JDBC `LedgerRecordSink` 存在时（G18），同一次 submit 还会按运行快照的 `ledger_code`/`field_code` 插入物理台账行。没有第二条 DDL 路径。产品 G18 已关闭（见 `docs/g18-business-path.md`）。

## 状态机

```text
DRAFT --validate--> VALIDATED --publish--> RUNNING
                       ^                     |
                       |---- edit draft -----|
```

- `validate`：按 M0-06 SPI 检查每条 binding 的 `ledger_code`+`field_code`。失败保持 DRAFT，不写 version，不改 `running_version`。
- `publish`：必须 VALIDATED；同事务写入 version 快照并切换 RUNNING 指针。上一 RUNNING 变为 SUPERSEDED。
- 发布中途失败（`SchemaService.ensurePhysicalTable` / SchemaService evolve DDL abort / `PublishHook` / 运行时异常）：**事务回滚**。RUNNING 指针与已发布 snapshot 不变；`form_publish_log.outcome=ROLLED_BACK`。产品默认在 SchemaService 可用时走 `SchemaServicePublishHook` → `ensurePhysicalTable`（表缺失则 CREATE，新字段则 evolve；无 drift 只确认。DDL 仍只由 SchemaService 执行）。
- 编辑 VALIDATED 草稿会回到 DRAFT，必须重新校验。

G5（Draft/Running、校验、失败回滚）只验收 **表单发布**。失败路径证据在 [g5-form-publish-rollback.md](./g5-form-publish-rollback.md)：`./scripts/g5-form-publish-rollback.sh`。G18 关闭记录在 `docs/g18-business-path.md`，浏览器走通了校验与**成功**发布；validate-fail→DRAFT 与 publish-fail→Running/snapshot 不变不在那次点击里。

## API

均需服务端 `TenantContext`（演示头 `nexus.studio.demo-context=true` + `X-Nexus-Demo-Tenant-Id` / `X-Nexus-Demo-Actor-Id`，非生产租户来源）。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/p3/forms` | 创建 DRAFT |
| GET | `/api/v1/p3/forms` | 本租户列表 |
| GET | `/api/v1/p3/forms/{id}` | 草稿 + running 指针 |
| PUT | `/api/v1/p3/forms/{id}` | 改草稿（回到 DRAFT） |
| POST | `/api/v1/p3/forms/{id}/validate` | Draft→Validated |
| POST | `/api/v1/p3/forms/{id}/publish` | Validated→Running |
| GET | `/api/v1/p3/forms/{id}/running` | 运行快照（填报 schema） |
| GET | `/api/v1/p3/forms/{id}/versions` | 版本历史 |
| GET | `/api/v1/p3/forms/{id}/publish-log` | 发布日志 |
| POST | `/api/v1/p3/forms/{id}/submissions` | 按 **field_code** 填报运行版本 |
| GET | `/api/v1/p3/forms/{id}/submissions` | 本租户提交 |

填报写入 `business_meta.form_submission`。有 `LedgerRecordSink` 时同时写入 M0-06 物理台账（G18 路径）；无 sink 的内存测试仍只写提交表。

## 错误码

| code | HTTP | 含义 |
|---|---|---|
| `P3.FORM.0001` | 404 | 表单不存在（含跨租户伪装成 404） |
| `P3.FORM.0002` | 409 | 非法状态（未校验就发布） |
| `P3.FORM.0004` | 400 | 物理列/表绑定 |
| `P3.FORM.0005` | 400 | 校验失败（未知 ledger/field code） |
| `P3.FORM.0006` | 409 | 发布失败已回滚，运行版本未变 |
| `P3.FORM.0007` | 409 | 尚无运行版本，不能填报 |
| `P3.FORM.0008` | 400 | 提交缺必填 / 未知 field_code |
| `P3.FORM.0009` | 409 | form_code 冲突 |
| `CORE.CTX.0001` | 403 | 缺少 TenantContext |

## Flyway

- **V530** `business_meta.form*`。窗口 **V530–V549**。
- **V531** `form_submission.ledger_id` / `ledger_record_id`（G18 指向物理行；不建 `l_*`）。
- 不占用 V500（执行）、V501（审计）、V510（文件）、V520（通知）、V400–V499（M0-06 台账元数据）。
- 禁止 `CREATE TABLE business_data` / `l_*` / `a_*`。

## 测试

```bash
./scripts/g5-form-publish-rollback.sh
# or
mvn -pl nexus-p3-studio,nexus-common,nexus-bootstrap -am test
```

Zonky 嵌入式 PostgreSQL **15.18**（`JdbcFormPublishRollbackIT`：校验失败保持 DRAFT；产品 SchemaService CREATE/evolve 成功；Hook / SchemaService evolve DDL abort 后 RUNNING 指针与 `snapshot_json` 不变），无 Docker。
