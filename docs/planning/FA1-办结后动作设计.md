# FA1 · 办结后动作（审批办结后往另一张台账插一行）

基线 `main@5500381f`。设计定稿，不代表已部署。不含 token、口令、person id 原值。

## 1. 现状

- 表单提交（`BusinessPathService.submitAndStart`）把单写入自己的台账、`approval_status = IN_APPROVAL`，再启动审批。
- 审批通过 / 驳回（`BusinessPathService.complete`）只回写本行 `approval_status`、记审计、发站内通知，没有扩展点。
- `SequentialApprovalService.complete` 目前在处理第一个待办后即 `finishInstance`——事实上的单节点办结。本功能挂在「流程实例进入终态」这一刻，将来多节点只改终态判定。
- 可复用：`LedgerRecordSink.insert`（经 `GuardedLedgerStore`）、`nexus-event-core` 的 outbox（`OutboxPort` / `OutboxDispatcher` / `OutboxHandler`，默认开启，自带重试、死信、执行日志）、`AuditPort`。

## 2. 已确认的决策

| 问题 | 决策 |
|---|---|
| 触发 | `APPROVED` / `REJECTED` / `ANY` 三种都支持 |
| 同步 / 异步 | 异步：办结事务内写 outbox，后台执行；动作失败不影响审批 |
| 目标行创建人 | 发起人（`created_by` / `owner_id` = 发起人 person），审计另记「系统代执行」与审批人 |
| 动作类型 | v1 只做「插一行」（`INSERT_ROW`）；更新目标行放 v2 |

## 3. 模型（FA2 迁移，全局号 V710，`business_meta`）

**`completion_action`**

| 列 | 说明 |
|---|---|
| `id`, `tenant_id` | |
| `name` | 显示名 |
| `source_ledger_code` | 源台账（表单提交写入的那张） |
| `process_key` | 源流程 key |
| `trigger_outcome` | `APPROVED` / `REJECTED` / `ANY` |
| `action_type` | `INSERT_ROW` |
| `target_ledger_code` | 目标台账（同租户、READY、≠ 源台账） |
| `field_mapping` | jsonb：`[{target, source: {kind, value}}]` |
| `enabled`, `version` | 启停、乐观锁 |
| `created_at/by`, `updated_at/by` | |

`field_mapping` 的 `source.kind`：

| kind | value | 含义 |
|---|---|---|
| `FIELD` | 源台账字段 code | 取源行该字段 |
| `CONST` | 字面量（字符串） | 固定值，例如「待付款」；按目标字段类型转换 |
| `SYSTEM` | `SOURCE_RECORD_ID` / `INITIATOR` / `LAST_APPROVER` / `COMPLETED_AT` / `OUTCOME` | 系统变量 |

**`completion_action_run`**：`id, tenant_id, action_id, source_ledger_code, source_record_id, process_instance_id, outcome, status(PENDING/DONE/FAILED), target_record_id, attempts, error_code, created_at, updated_at`；唯一键 `(tenant_id, action_id, source_record_id)`——同一张单同一动作最多插一行。

两表登记 `platform.table_registry`（tenant 作用域）。

## 4. 执行链路（FA4）

1. `BusinessPathService.complete`（非重放）在回写终态的**同一事务**里 `OutboxPort.append`：destination `p3.completion`，eventType `p3.approval.completed`，payload `{source_ledger_code, source_record_id, process_instance_id, process_key, outcome, initiator, approver, completed_at}`。
2. `CompletionActionHandler implements OutboxHandler`（destination `p3.completion`）：
   1. 断言 envelope 租户与当前隔离上下文一致；
   2. 查本租户启用中、`source_ledger_code + process_key` 匹配且 `trigger_outcome ∈ {outcome, ANY}` 的动作；
   3. 对每个动作先占 run（唯一键；已 `DONE` 则跳过）；
   4. 读源行，按映射组装；以**发起人**身份重新绑定内核上下文后 `LedgerRecordSink.insert(target, row)`（`created_by`/`owner_id` = 发起人），随后恢复原上下文；
   5. run 置 `DONE` 并回填 `target_record_id`；审计 `p3.completion_action.executed`。
   6. 失败：run 置 `FAILED`（仅错误类别，无原文数据），抛出让 outbox 退避重试；超限入死信。
3. 手动重试：`POST /api/v1/p3/completion-actions/runs/{id}/retry` 重新 append 一条同载荷事件（run 唯一键保证不重复插行）。

执行不叠加发起人的 P2 授权检查（调度线程无 P2 会话，走仅租户隔离判定）：这是配置者授权的自动动作；写入限定同租户、配置内的目标台账，全程审计。

## 5. 配置校验（FA3）

- 源、目标台账同租户存在且 READY；目标 ≠ 源。
- 目标台账每个 `required` 业务字段都有映射。
- 映射目标字段存在；`FIELD` 来源字段存在于源台账；类型兼容（`TEXT` 接受任意；`NUMERIC`/`INTEGER`/`BOOLEAN`/`TIMESTAMPTZ` 要求同型，`CONST` 须可解析）。
- 不映射系统列（`id`、`tenant_id`、`created_*` 等）。

## 6. 权限（FA3）

- 新资源 `process.action`：`read` 看配置和执行记录，`write` 增改启停和重试。
- 授给 PV4 的 `tenant_process_designer`（租户管理员）；一般用户无入口。

## 7. 接口（FA3 / FA4）

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/api/v1/p3/completion-actions` | `process.action:read` |
| POST | `/api/v1/p3/completion-actions` | `process.action:write` |
| PUT | `/api/v1/p3/completion-actions/{id}` | `process.action:write`（带 `version`） |
| POST | `/api/v1/p3/completion-actions/{id}/preview` | `process.action:read`（用一条源记录试算，不写） |
| GET | `/api/v1/p3/completion-actions/runs?action_id=` | `process.action:read` |
| POST | `/api/v1/p3/completion-actions/runs/{id}/retry` | `process.action:write` |

## 8. 界面（FA5 / FA6，P3 星构，租户管理员）

- `/p3/completion-actions`：列表（名称、源 → 目标、触发、启停）。
- 编辑：源台账 + 流程 key、触发、目标台账、映射表（目标字段 ← 来源类型 + 值），必填 / 类型错误就地提示；「试算」输入源记录 id 预览将插入的行。
- 执行记录：源单、目标行、状态、错误类别、重试。

## 9. 演示与验收（FA7）

「费用报销」审批通过 → 「付款台账」插一行：申请人、金额、事由、来源单号，处理状态「待付款」。周然提交、林晓通过，付款台账出现该行，执行记录为成功；驳回不插行（触发 = 通过时）。

## 10. 卡片

| 卡 | 内容 |
|---|---|
| FA1 | 本文 |
| FA2 | V710 迁移（仅迁移） |
| FA3 | 后端配置 CRUD + 校验 + `process.action` 授权 |
| FA4 | 后端 outbox 事件 + 执行 handler + 执行记录 + 重试 |
| FA5 | 前端配置页 |
| FA6 | 前端执行记录 + 重试 |
| FA7 | 演示脚本与验收 |
