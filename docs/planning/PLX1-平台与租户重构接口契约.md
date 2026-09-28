# PLX1 · 平台与租户重构接口契约 V1

对应 [PL 规划](PL-平台级与租户级功能重新规划_V0.1.md) §9。三人（A 组织与权限、B 应用与市场、C 租户侧与门户）评审通过后合入，之后按本文并行开发。契约变更须三方同意并修订本文版本号。

示例清单：[`plx1/leave-app-manifest.example.json`](plx1/leave-app-manifest.example.json)（各线契约测试共用）。

## 0. 通用约定

| 项 | 约定 |
|---|---|
| 线上字段 | JSON 一律 snake_case（与现有产品序列化一致）；枚举值大写 |
| 租户 | 一律取自服务端签发的上下文（`X-Nexus-Context-Id`）；请求体、查询参数不得携带租户标识（ARCH-024） |
| 编码 | `app_code`、`form_code`、`ledger_code`、`process_key`、`role_code`、`category_code`、`product_code` 均为 `^[a-z][a-z0-9_]{0,23}$`（与台账编码同规则） |
| 版本 | 应用版本 `MAJOR.MINOR.PATCH`；小版本 = MINOR 或 PATCH 变化，用于 D3「小版本自动升级」 |
| 错误 | 沿用 `{code, message, details}`；各线错误码前缀：A `P2.PERM.*`，B `P4.APP.*` / `P4.PRODUCT.*`，C `P3.INSTANCE.*` / `P3.PORTAL.*` |
| 进程内调用 | 跨线调用优先 Java 端口（同一 jar 内），HTTP 仅供界面与运维；端口接口放在提供方模块 |
| 幂等 | 所有「落地 / 安装 / 升级」操作可重复调用，结果一致 |
| 跨租户写入 | 平台上下文里对某个租户的写入（如批准开通）一律经能力对象显式给出目标租户，并在同一事务内完成；不得伪造目标租户的上下文或 membership，包括不得写入会被调度器恢复成租户身份的 `exec_outbox` 行（r2 / r3） |
| 安全 | 日志、事件、错误中不写字段值与凭证，只写编码和编号 |

## C1 · 功能入口（B 定义、A 授权、C 展示）

**键**：`fn:{app_code}:{form_code}`，如 `fn:leave:leave_apply`。

**授权资源**：`fn.{app_code}.{form_code}`；动作：

| 动作 | 含义 |
|---|---|
| `read` | 在门户菜单中可见、可进入；按授权的数据范围查看记录 |
| `write` | 仅对 `FILL` 入口有意义：新建、提交、编辑自己未提交的记录，并发起该入口的流程 |

**审批办理不是入口权限**（r2）：审批表单（`purpose = APPROVE`）不挂入口，也不授予 `fn.*`。能否办理某条待办，由流程节点的办理人（`ROLE` / 已映射的 `POSITION`）加现有 `process.task` 能力决定，审批人从「待办」进入。这样门户菜单只反映 `fn.*:read`，服务端的办理授权只看节点办理人，两者不会分叉。

**入口元数据**（出现在应用版本清单 `entries[]`）：

```json
{
  "key": "fn:leave:leave_apply",
  "app_code": "leave",
  "form_code": "leave_apply",
  "ledger_code": "leave",
  "process_key": "leave_approval",
  "name": "请假申请",
  "icon": "calendar",
  "kind": "FILL",
  "default_group": "办公",
  "sort": 10
}
```

| `kind` | 引用的表单 | `process_key` | 门户行为 |
|---|---|---|---|
| `FILL` | `purpose = FILL` | 可空。非空时必须是本清单内 `form_code` 相同的流程，点入口提交即启动它；为空则只写台账、不走流程 | 新建 / 我的记录 |
| `LIST` | `purpose = VIEW` | 必须为空 | 按数据范围列台账，点开用该表单布局展示详情 |
| `VIEW` | `purpose = VIEW` | 必须为空 | 单条只读查看（如被链接打开） |

`LIST` 也引用表单，原因有两个：详情需要布局；授权资源按 `fn.{app}.{form}` 命名，入口和授权一一对应。

约束：同一应用内 `form_code` 唯一；入口键在清单内唯一（`app_code` 全局唯一，所以在租户内也唯一）；入口只能引用本清单内存在的表单、台账与流程；`APPROVE` 表单不得被入口引用。

## C2 · 应用版本清单（B）

`schema = "nexus.app-manifest.v1"`。平台发布后不可变；租户实例引用某一版本。

```json
{
  "schema": "nexus.app-manifest.v1",
  "app_code": "leave",
  "version": "1.0.0",
  "category_code": "oa",
  "name": "请假",
  "ledgers": [ { "code": "leave", "name": "请假台账", "fields": [ { "code": "days", "name": "天数", "type": "NUMERIC", "required": true, "indexed": false } ] } ],
  "forms": [ { "code": "leave_apply", "name": "请假申请", "ledger_code": "leave", "purpose": "FILL",
               "layout": { "sections": [ { "title": "基本信息", "fields": ["title", "days", "reason"] } ] },
               "readonly_fields": [] } ],
  "processes": [ { "key": "leave_approval", "name": "请假审批", "form_code": "leave_apply",
                   "nodes": [ { "id": "n1", "name": "部门审批", "form_code": "leave_approve", "assignee": { "type": "ROLE", "ref": "leave_approver" } },
                              { "id": "n2", "name": "人事备案", "form_code": "leave_approve", "assignee": { "type": "POSITION", "ref": "hr_clerk" } } ] } ],
  "role_pack": [ { "code": "leave_applicant", "name": "请假申请人",
                   "grants": [ { "resource": "fn.leave.leave_apply", "action": "read", "scope": "SELF" },
                               { "resource": "fn.leave.leave_apply", "action": "write", "scope": "SELF" } ] } ],
  "entries": [ { "key": "fn:leave:leave_apply", "app_code": "leave", "form_code": "leave_apply", "ledger_code": "leave",
                 "process_key": "leave_approval", "name": "请假申请", "icon": "calendar", "kind": "FILL", "default_group": "办公", "sort": 10 } ]
}
```

（完整示例见 `plx1/leave-app-manifest.example.json`，含 `APPROVE` 节点表单、`POSITION` 节点与 `LIST` 入口。）

| 部分 | 规则 |
|---|---|
| `ledgers[].fields[].type` | `TEXT` / `NUMERIC` / `INTEGER` / `BOOLEAN` / `TIMESTAMPTZ`（与现有台账字段类型一致）；不得使用系统列名 |
| `forms[].purpose` | `FILL` / `APPROVE` / `VIEW`；`layout` 与 `readonly_fields` 只引用本台账字段 |
| `processes[].nodes[].form_code` | 可空；非空时须为本清单内同台账的 `APPROVE` 表单 |
| `processes[].nodes[].assignee.type` | 清单内只允许 `ROLE`（引用本清单 `role_pack`）或 `POSITION`（岗位编码，租户安装后由管理员映射）；**不允许 `PERSON`**（人员属于租户） |
| `role_pack[].grants[]` | `resource` 只能是本清单入口对应的 `fn.{app}.{form}`；`action` 为 `read` / `write`，`write` 只能授给 `FILL` 入口；`scope` 为 `SELF` / `DEPT` / `DEPT_AND_CHILDREN` / `ALL`（`SPECIFIED` 需租户侧配置，不进清单） |

**校验分两层**（r2）：

| 层 | 时机 | 负责 | 检查 |
|---|---|---|---|
| 清单校验 | 平台保存草稿 / 发布 | B：`AppManifestValidator.validate(manifest) → List<Problem>` | 只看清单自身：编码格式；`ledgers` / `fields` / `forms` / `processes` / `role_pack` 编码与入口键不重复；表单 → 台账、`layout` / `readonly_fields` → 字段、流程 → 表单、节点 `form_code` → `APPROVE` 表单、`ROLE` 办理人 → `role_pack`、入口 → 表单 / 台账 / 流程、入口 `app_code` 与清单一致、`kind` 与表单 `purpose` 匹配、授权 → 入口资源。`POSITION` 只查编码格式，**不查租户岗位是否存在** |
| 安装校验 | 租户安装 / 升级后 | C（租户侧） | 列出实例中所有 `POSITION` 引用及其映射状态。未映射的流程标记 `NEEDS_MAPPING`：其 `FILL` 入口仍显示但不可提交，并提示「管理员尚未配置审批岗位」；服务端启动该流程时返回 `P3.INSTANCE.0002`。映射完成后立即可用，不需要重新安装 |

`Problem = {path, reason}`：`path` 为 JSON Pointer（如 `/entries/1/process_key`），`reason` 为稳定的小写码（如 `duplicate_code`、`unknown_ref`、`kind_purpose_mismatch`、`invalid_code`）。清单校验一次收集全部问题再返回，不在第一个错误处停止（与 FA3 一致）。

## C3 · 角色包落地（A 提供，B 在订购 / 升级时调用）

**Java 端口**（`nexus-permission-core`，包 `one.nexus.permission.pack`，经 `PermissionModule.rolePacks()` 取得）：

```java
RolePackResult RolePackService.apply(RolePackCommand command);

record RolePackCommand(String appCode, String appVersion, List<RoleSpec> rolePack) {}
record RoleSpec(String code, String name, List<GrantSpec> grants) {}
record GrantSpec(String resource, String action, String scope) {}
record RolePackResult(List<AppliedRole> roles) {}
record AppliedRole(String tenantRoleCode, boolean created, int grantsAdded, List<String> grantsNotInPack) {}
```

**平台批准开通时的落地**（r2，同 C4 的问题）：平台运营在平台上下文里批准，目标是另一个租户，不能用当前上下文，也不能伪造目标租户的登录态。所以另开一个入口：

```java
RolePackResult RolePackService.applyApproved(ApprovedTenantWrite target, RolePackCommand command);

/** permission-core 定义；B 的 PlatformActivationCommand 实现它。 */
interface ApprovedTenantWrite {
    UUID targetTenantId();   // 被批准的申请里的租户
    UUID actorId();          // 平台运营
    String traceId();
    void assertActive();     // 能力仍在签发它的请求内，否则抛异常
}
```

A 先调 `assertActive()`，再以 `targetTenantId()` 显式写入该租户的角色与授权、刷新其策略版本、记审计（执行人 = 平台运营，目标租户 = 被批准租户）。仍然**不绑定岗位**。

**事务**（r3.2 更正）：权限库使用独立的 DataSource（`PermissionConfiguration`），和市场写入**不在同一个数据库事务**。正确性靠两点保证：落地幂等，可以放心重试；未绑定岗位的角色不影响任何人，市场事务回滚后留下的角色无害，下次落地时复用。B 在安装事务提交**之前**调用 `apply` / `applyApproved`，调用失败时让安装一起失败。实现见 #786。

| 规则 | 说明 |
|---|---|
| 租户 | `apply` 取当前绑定上下文，调用方不传租户；`applyApproved` 只接受 `ApprovedTenantWrite` 给出的目标租户 |
| 租户角色编码 | `app_{app_code}_{role_code}`，避免与租户自建角色冲突。两段编码各 ≤ 24，拼接后最长 53，小于 `iam.perm_role.code` 的 64，**无需截断**；租户自建角色不得以 `app_` 开头（A 在创建角色时校验） |
| 幂等 | 角色已存在则只补缺失授权；重复调用 `created=false, grantsAdded=0` |
| **不绑定岗位** | 只创建角色与授权（D4）；绑定由租户管理员在岗位页完成 |
| 升级 | 新版本新增的授权自动补上；新版本删除的授权**不自动收回**，列入 `grantsNotInPack` 交管理员处理 |
| 审计 | 每次落地记一条权限审计，含应用编码与版本 |

HTTP（仅运维 / 调试）：`POST /api/v1/permission/role-packs/apply`，需 `permission.role:write`。

## C4 · 应用实例状态同步（B 提供，C 拉取）

r3 改为**状态同步**，替代 r2 的 outbox 事件。原因见开发 B 的 r2 复核：`OutboxDispatcher` 投递时，`ExecutionIsolation.runRestored` 会把行上的 `membership_id` 重新签发成目标租户的 `TenantContext`。平台批准时，平台运营并没有目标租户的成员身份，往 `exec_outbox` 写任何一种身份都等于冒充租户成员。本契约**不新增**平台来源的 outbox 行，也不修改调度器的恢复语义。

**B 的职责**：每个实例保存当前状态和 `revision`。安装、升级、卸载时，在与安装写入**同一事务**内把 `revision` +1，从 1 开始。

- 租户自助路径：沿用现有仓储写入。
- 平台批准路径：沿用现有的 `saveApprovedInstallation(PlatformActivationCommand, …)` 这类能力写入器，只多写 `revision` 与状态列。

**查询端口**（r3.1）：接口由 C 定义在 `nexus-p3-studio`（`one.nexus.p3.instance.AppInstanceQuery`，#779 已有），B 在 `nexus-p4-market` 实现并注册为 Spring bean。`p4-market` 依赖 `p3-studio`，反过来不行，所以接口放在消费方。形状如下：

```java
List<AppInstanceState> AppInstanceQuery.current();          // 当前租户的全部实例，含已卸载
Optional<AppInstanceState> AppInstanceQuery.current(UUID instanceId);

record AppInstanceState(UUID instanceId, long revision, String status,   // INSTALLED / UNINSTALLED
                        String productCode, String appCode, String version, String mode,
                        List<String> entries, Instant changedAt) {}
```

对应 JSON（snake_case）：

```json
{ "instance_id": "uuid", "revision": 3, "status": "INSTALLED", "product_code": "oa_basic",
  "app_code": "leave", "version": "1.1.0", "mode": "INHERIT",
  "entries": ["fn:leave:leave_apply", "fn:leave:leave_view"], "changed_at": "2026-09-28T08:00:00Z" }
```

| 规则 | 说明 |
|---|---|
| 租户 | 只取服务端上下文，调用方不传租户；没有租户上下文时拒绝 |
| `entries` | 当前版本的全部入口键（全量）；`UNINSTALLED` 时为 `[]`，`mode` 为 `null` |
| 卸载 | 实例行保留，状态为 `UNINSTALLED`，这样 C 能看到卸载 |
| 历史 | 状态变化照常记审计（平台批准时执行人为平台运营、目标为该租户），端口本身只给当前状态 |

**C 的职责**：C 在**合法的租户上下文**里对账，时机包括门户加载、入口列表、个性化解析，以及 C 自己的租户内任务。

- C 为每个实例保存已应用的 `revision`，拿到的 revision 比本地大时，以状态整体覆盖本地；相等时不做任何事。
- 因为状态是全量的，同一版本再次升回、中间变化被跳过、重复拉取都不会出错。
- 对账本身不需要 `event_id`，也不需要 `previous_version`（`event_id` 只用于下文租户自助路径事件的去重）。C6 的 `drift` 用覆盖记录的 `base_version` 和当前 `version` 比较得出。

**租户自助路径的事件**（r3.1）：租户自己订购、升级、卸载时有真实的租户成员身份，B 用现有的 `OutboxPort.append` 发事件，destination 为 `p4.app_instance`。载荷就是上面的状态，外加 `event_id`（UUIDv7，与 outbox 行 id 相同）和事件类型（`installed` / `upgraded` / `uninstalled`）。C 按 #779 已实现的规则消费：先按 `event_id` 去重，再按 `revision` 定顺序；等于已应用 +1 时整体覆盖钉扎与入口；有缺口时查 `current(instanceId)`；查询结果仍落后于事件时不记已处理，交给 outbox 重试；覆盖不删租户个性化。

**平台批准路径不发事件**，理由见本节开头。C 在门户加载、入口列表、个性化解析时调 `current()` 对账，按 `revision` 比较补齐。这条对账路径也兜住了事件丢失或延迟的情况，所以它是必需的，事件只是加速。

**事务**：r2 说 `JdbcOutboxStore.append`「自开 JDBC 事务」是错的。它经 `Jdbc.transactionalCall` → `DataSourceUtils.getConnection`，已有 Spring 事务时会加入该事务。r3 的状态同步本身不依赖 outbox。

**契约测试**：

| 线 | 测试 |
|---|---|
| B | ① 平台批准事务回滚后，没有实例，也没有 revision 变化；② 安装、升级、再升回同一版本、卸载，revision 依次为 1、2、3、4；③ `current()` 只返回本租户的实例，无上下文时拒绝；④ 平台批准路径不产生 `exec_outbox` 行 |
| C | ① 对账幂等（同一 revision 拉两次，结果不变）；② revision 从 1 跳到 3 时按状态整体覆盖；③ `UNINSTALLED` 后入口消失；④ 升回旧版本后，已覆盖对象的 `drift` 正确；⑤ 没有任何事件、只有平台批准产生的实例时，门户加载后入口出现 |

`mode` 为实例整体默认；按对象派生的覆盖不改变该值（见 C6）。

## C5 · 成员可读入口（A 提供，C 门户使用）

**Java 端口**：`Set<String> EntryAccessService.readableEntries()`（`nexus-permission-core`，包 `one.nexus.permission.entry`，经 `PermissionModule.entries()` 取得）—— 当前上下文成员可 `read` 的入口键集合。入口候选集合由调用方传入的「租户已安装入口」决定：`readableEntries(Collection<String> candidateKeys)`，A 只做权限过滤，不维护入口清单。

**HTTP**（仅运维 / 调试，门户走 Java 端口）：`GET /api/v1/permission/entries/readable?keys=fn:leave:leave_apply&keys=...`，只回答调用者本人。候选键由调用方给出（r3，与 Java 端口一致；实现见 #784）→

```json
{ "entries": ["fn:leave:leave_apply", "fn:expense:expense_apply"], "computed_at": "2026-09-28T08:00:00Z" }
```

| 规则 | 说明 |
|---|---|
| 解析 | 成员 ACTIVE 任职岗位 → 岗位绑定角色 → 授权；叠加例外授权（直接给人，D5）；显式拒绝优先 |
| 范围 | 仅业务租户；平台租户返回空集合（平台功能走 PV / AUTH6） |
| 一致性 | 以当前策略版本计算；策略版本变化后下次调用即生效 |
| 服务端兜底 | 门户仅据此显示；每个入口的接口仍独立校验 `fn.*` 授权 |

## C6 · 个性化解析（C 提供，B 与运行时使用）

对象类型：`FORM` / `PROCESS` / `ROLE`（D2 按对象派生）。

**覆盖记录**：

```json
{ "instance_id": "uuid", "object_type": "FORM", "object_code": "leave_apply",
  "base_version": "1.0.0", "content": { }, "updated_at": "...", "updated_by": "..." }
```

**解析顺序**：租户覆盖 > 实例所引用的平台版本。

**解析结果**（`GET /api/v1/p3/instances/{instance_id}/resolved/{object_type}/{object_code}`）：

```json
{ "object_type": "FORM", "object_code": "leave_apply", "source": "TENANT",
  "base_version": "1.0.0", "platform_version": "1.1.0", "drift": true, "content": { } }
```

- `drift = true` 表示平台已有更新版本而覆盖仍基于旧基线（C 的差异与合并功能使用）。
- 升级（C4 对账发现 `version` 变化）只改变未覆盖对象的解析结果；已覆盖对象保持不变并出现 `drift`。
- 台账字段只能**新增**（租户个性化不删除、不改类型平台字段），保证平台升级可叠加。

## 与现有模型的过渡

| 现有 | 契约下的对应 |
|---|---|
| `ProductDefinition`（一个表单模板 + 至多一个流程） | 单应用清单：`forms[1]`（`FILL`）、`entries[1]`（`FILL`）；有流程时 `processes[1]` 且入口 `process_key` 指向它，流程键与版本都为空时 `processes[]` 为空、入口 `process_key = null`（仍迁移，只是不走流程）。B 的 PLB9 迁移 |
| 不合新编码规则的旧流程键（如 `demoSimpleApproval`） | 迁移时规范化：驼峰转下划线、转小写、截到 24 位（`demo_simple_approval`），原键写入该流程的 `legacy_key`（可选字段，只在迁移产生的清单里出现）。运行时按新键查找，查不到再按 `legacy_key` 查，已在途的流程实例不改键、照常办完。规范化后冲突或仍不合规则的，**整批迁移中止**（r3）：一条都不写，输出报告，逐条列出旧产品、旧键、冲突对象和建议的新键；管理员在旧产品上改好键后重跑。迁移是幂等的，可以反复执行。迁移完成前，旧产品继续按旧路径运行（`ProductDefinition` + 固定流程），已安装的租户不受影响、不下线；不允许部分迁移、静默漏迁 |
| `InstallationProductPin` | 实例引用的平台版本 |
| `InstallationProcessConfiguration`（流程覆盖） | `PROCESS` 类型覆盖记录 |
| `tenant_member` 全员种子 | 由岗位绑定角色替代（A 的 PLA5，保留兼容开关） |
| 办结后动作（FA） | v1 保持租户配置；后续可随清单下发（本契约不含） |

## 评审

| 角色 | 负责核对 | 结论 |
|---|---|---|
| 开发 A | C1 授权资源、C3、C5 | **通过**（2026-09-28）。已核实：`iam.perm_grant.resource` 为 VARCHAR(128)，`fn.{app}.{form}` 最长 52；`iam.perm_role.code` 为 VARCHAR(64)，角色编码无需截断；权限引擎已支持 `position` 授权主体（主体 id 为岗位 UUID，`IamSubjectDirectory` 按 ACTIVE 任职收集）；C3 / C5 端口放 `nexus-permission-core`，C5 改为由调用方传入候选入口键 |
| 开发 B | C1 元数据、C2、C4、过渡映射 | C1 元数据、C2 **可接受**（r2 复核）。C4、过渡映射第二轮暂不通过；r3 已改为状态同步，并调整迁移策略，待 B 复核 |
| 开发 C | C1 展示、C4 消费、C6 | C1 展示、C6 **通过**（2026-09-28）。r3 把 C4 改为 C 拉取对账，另新增 `NEEDS_MAPPING`（C 负责），待 C 复核这两处 |

三方均通过后合入；合入即冻结 V1。

## 修订记录

**r2（2026-09-28，回应开发 B 第一轮评审）**

| B 的意见 | 修订位置 | 结论 |
|---|---|---|
| 1. 平台批准开通时无法写目标租户的事件信封 | C4「平台批准开通时的写入」；C3 同样问题，新增 `applyApproved` | B 在批准事务内用能力校验的专用写入器写 outbox；A 通过 `ApprovedTenantWrite` 显式写目标租户角色包。都不冒充租户登录态，都与安装同一事务 |
| 2. 去重键吞掉再次升级 | C4「字段」「去重与顺序」 | 改为 `event_id` 去重、`instance_revision` 定顺序、有缺口时查全量；列出三种事件的必填字段与 `null` 约定 |
| 3. 入口到流程不确定；审批入口授权语义 | C1 | 入口加 `process_key`；审批办理由节点办理人决定，不挂入口、不授 `fn.*`；示例去掉审批入口，改为 `LIST` 入口；说明 `LIST` 为何引用表单 |
| 4. 平台校验与租户安装校验分层；问题汇总 | C2「校验分两层」 | 平台只查清单自身，`POSITION` 只查格式；租户侧标 `NEEDS_MAPPING`；列出全部检查项，全部汇总返回 |
| 5. 过渡映射容纳真实数据 | 「与现有模型的过渡」 | 无流程的定义迁为 `processes[]` 空；旧流程键规范化并保留 `legacy_key` |

另：三线并行都会加 Flyway 迁移（C 已用 V712），版本号先合先得；每个 PR 合入前 rebase 到 main 并确认版本号没有和别人重复。
**r3（2026-09-28，回应开发 B 对 r2 的复核）**

| B 的意见 | 修订 |
|---|---|
| 平台写目标租户的 outbox 行时，调度器会恢复成租户成员身份 | C4 改为状态同步：B 在实例上保存 `revision` 与全量状态，平台批准路径不写 `exec_outbox`；C 在合法租户上下文里经 `AppInstanceQuery` 拉取对账。租户自助路径可以额外发一个不携带状态的 `changed` 提示事件。调度器语义不变。B / C 各四条契约测试 |
| 事务描述错误 | 更正：`JdbcOutboxStore.append` 经 `DataSourceUtils` 加入已有 Spring 事务 |
| 旧键冲突时「跳过」会静默漏迁 | 改为整批中止、输出可操作报告、可重跑；迁移完成前旧产品按旧路径运行 |

r2 中的 `previous_version` 取消；`event_id` 与三种事件类型在 r3.1 中恢复，只用于租户自助路径。

**r3.1（2026-09-28，对齐 C 在 #779 按 r2 做的实现）**：租户自助路径恢复带完整状态的事件，即 r2 的形状加 event_id，C 的消费逻辑不用改；平台批准路径仍不发事件，由 C 在门户加载时调 `current()` 对账补齐（C 新增契约测试 ⑤）。`AppInstanceQuery` 接口由 C 定义在 p3-studio，B 实现。

**r3.2（2026-09-28）**：更正 C3 的事务描述。权限库使用独立的 DataSource，无法与市场写入同一事务，改为「落地幂等 + 未绑定的角色无害 + 安装提交前调用」。
