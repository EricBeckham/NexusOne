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
| 安全 | 日志、事件、错误中不写字段值与凭证，只写编码和编号 |

## C1 · 功能入口（B 定义、A 授权、C 展示）

**键**：`fn:{app_code}:{form_code}`，如 `fn:leave:leave_apply`。

**授权资源**：`fn.{app_code}.{form_code}`；动作：

| 动作 | 含义 |
|---|---|
| `read` | 在门户菜单中可见、可进入列表 / 查看 |
| `write` | 可办理（新建、提交、编辑自己的记录） |

**入口元数据**（出现在应用版本清单 `entries[]`）：

```json
{
  "key": "fn:leave:leave_apply",
  "app_code": "leave",
  "form_code": "leave_apply",
  "ledger_code": "leave",
  "name": "请假申请",
  "icon": "calendar",
  "kind": "FILL",
  "default_group": "办公",
  "sort": 10
}
```

`kind`：`FILL`（填报并可发起流程）、`LIST`（台账列表）、`VIEW`（只读查看）。

约束：同一应用内 `form_code` 唯一；键在租户内全局唯一；入口必须引用本清单内存在的表单与台账。

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
                   "nodes": [ { "id": "n1", "name": "部门审批", "assignee": { "type": "ROLE", "ref": "leave_approver" } } ] } ],
  "role_pack": [ { "code": "leave_applicant", "name": "请假申请人",
                   "grants": [ { "resource": "fn.leave.leave_apply", "action": "read", "scope": "SELF" },
                               { "resource": "fn.leave.leave_apply", "action": "write", "scope": "SELF" } ] } ],
  "entries": [ { "key": "fn:leave:leave_apply", "app_code": "leave", "form_code": "leave_apply", "ledger_code": "leave",
                 "name": "请假申请", "icon": "calendar", "kind": "FILL", "default_group": "办公", "sort": 10 } ]
}
```

| 部分 | 规则 |
|---|---|
| `ledgers[].fields[].type` | `TEXT` / `NUMERIC` / `INTEGER` / `BOOLEAN` / `TIMESTAMPTZ`（与现有台账字段类型一致）；不得使用系统列名 |
| `forms[].purpose` | `FILL` / `APPROVE` / `VIEW`；`layout` 只引用本台账字段 |
| `processes[].nodes[].assignee.type` | 清单内只允许 `ROLE`（引用本清单 `role_pack`）或 `POSITION`（岗位编码，租户安装后由管理员映射）；**不允许 `PERSON`**（人员属于租户） |
| `role_pack[].grants[].scope` | `SELF` / `DEPT` / `DEPT_AND_CHILDREN` / `ALL`（与现有数据范围一致；`SPECIFIED` 需租户侧配置，不进清单） |
| 引用完整性 | 表单引用的台账、流程引用的表单与角色、入口引用的表单与台账、授权引用的入口资源，均须在本清单内存在 |

B 提供校验端口：`AppManifestValidator.validate(manifest) → List<Problem>`，问题项格式 `{path, reason}`，与 FA3 的「一次列出全部问题」一致。

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

| 规则 | 说明 |
|---|---|
| 租户 | 取当前绑定上下文；调用方不传租户 |
| 租户角色编码 | `app_{app_code}_{role_code}`，避免与租户自建角色冲突。两段编码各 ≤ 24，拼接后最长 53，小于 `iam.perm_role.code` 的 64，**无需截断**；租户自建角色不得以 `app_` 开头（A 在创建角色时校验） |
| 幂等 | 角色已存在则只补缺失授权；重复调用 `created=false, grantsAdded=0` |
| **不绑定岗位** | 只创建角色与授权（D4）；绑定由租户管理员在岗位页完成 |
| 升级 | 新版本新增的授权自动补上；新版本删除的授权**不自动收回**，列入 `grantsNotInPack` 交管理员处理 |
| 审计 | 每次落地记一条权限审计，含应用编码与版本 |

HTTP（仅运维 / 调试）：`POST /api/v1/permission/role-packs/apply`，需 `permission.role:write`。

## C4 · 应用实例事件（B 发出，C 订阅）

outbox：destination `p4.app_instance`；事件类型与载荷：

| event_type | 触发 |
|---|---|
| `p4.app_instance.installed` | 订购后为某应用创建实例 |
| `p4.app_instance.upgraded` | 实例切换到新平台版本 |
| `p4.app_instance.uninstalled` | 退订 / 卸载 |

```json
{
  "instance_id": "uuid",
  "product_code": "oa_basic",
  "app_code": "leave",
  "version": "1.1.0",
  "previous_version": "1.0.0",
  "mode": "INHERIT",
  "entries": ["fn:leave:leave_apply"],
  "occurred_at": "2026-09-28T08:00:00Z"
}
```

- 租户在信封上，不在载荷里；`previous_version` 仅 `upgraded` 有。
- 消费方以 `(instance_id, event_type, version)` 去重。
- `mode` 为实例整体默认；按对象派生的覆盖不改变该值（见 C6）。

## C5 · 成员可读入口（A 提供，C 门户使用）

**Java 端口**：`Set<String> EntryAccessService.readableEntries()`（`nexus-permission-core`，包 `one.nexus.permission.entry`，经 `PermissionModule.entries()` 取得）—— 当前上下文成员可 `read` 的入口键集合。入口候选集合由调用方传入的「租户已安装入口」决定：`readableEntries(Collection<String> candidateKeys)`，A 只做权限过滤，不维护入口清单。

**HTTP**：`GET /api/v1/permission/entries/readable` →

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
- 升级（C4 `upgraded`）只改变未覆盖对象的解析结果；已覆盖对象保持不变并出现 `drift`。
- 台账字段只能**新增**（租户个性化不删除、不改类型平台字段），保证平台升级可叠加。

## 与现有模型的过渡

| 现有 | 契约下的对应 |
|---|---|
| `ProductDefinition`（一个表单模板 + 一个流程） | 单应用清单：`forms[1]`、`processes[1]`、`entries[1]`（B 的 PLB9 迁移） |
| `InstallationProductPin` | 实例引用的平台版本 |
| `InstallationProcessConfiguration`（流程覆盖） | `PROCESS` 类型覆盖记录 |
| `tenant_member` 全员种子 | 由岗位绑定角色替代（A 的 PLA5，保留兼容开关） |
| 办结后动作（FA） | v1 保持租户配置；后续可随清单下发（本契约不含） |

## 评审

| 角色 | 负责核对 | 结论 |
|---|---|---|
| 开发 A | C1 授权资源、C3、C5 | **通过**（2026-09-28）。已核实：`iam.perm_grant.resource` 为 VARCHAR(128)，`fn.{app}.{form}` 最长 52；`iam.perm_role.code` 为 VARCHAR(64)，角色编码无需截断；权限引擎已支持 `position` 授权主体（主体 id 为岗位 UUID，`IamSubjectDirectory` 按 ACTIVE 任职收集）；C3 / C5 端口放 `nexus-permission-core`，C5 改为由调用方传入候选入口键 |
| 开发 B | C1 元数据、C2、C4、过渡映射 | |
| 开发 C | C1 展示、C4 消费、C6 | |

三方均通过后合入；合入即冻结 V1。
