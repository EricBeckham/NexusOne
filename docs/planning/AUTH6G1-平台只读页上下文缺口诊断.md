# AUTH6G1 · 平台只读页上下文缺口诊断

基线 `main@d6128963`（含 AUTH6F1–F5）。只读诊断，不改行为；不含 token、context id、person id 原值；没有接触演示机。

## 现象

AUTH6F 之后租户目录与注册审订已恢复。下列四页仍报 `P1.CONTEXT.0001`：

| 页面 | 前端路由 | 前端 helper（`web/src/api/client.js`） | 当前 options |
|---|---|---|---|
| 租户关系 | `/p1/relations`、`/p1/relations/:id` | `p1.tenantTree` / `p1.tenantChildren` / `p1.tenantAncestors` | `{ platform: true, auth: false }` |
| 平台服务态 | `/p1/services` | `p1.serviceStatus` | `{ platform: true, auth: false }` |
| 平台审计中心 | `/p1/audit` | `audit.platformEvents`（另有 `audit.verifyChain`，POST） | `{ platform: true, auth: false }` |
| 表单模板 | `/p1/form-templates`（列表）、`/p1/form-templates/:id`（编辑器跟读） | `p1.formTemplates`、`p1.formTemplate` | `{ platform: true, auth: false }` |

## 后端守卫

| 页面 | 接口 | 控制器 / 服务 | 守卫调用 | 所需职责 |
|---|---|---|---|---|
| 租户关系 | `GET /api/v1/p1/tenants/tree` | `TenantRelationController` → `TenantRelationService.scope("tenant.relation.tree")` | `requirePlatformContext(op, SYSTEM_ADMIN)` | SYSTEM_ADMIN |
| | `GET /api/v1/p1/tenants/{id}/children` | 同上，`tenant.relation.children` | 同上 | SYSTEM_ADMIN |
| | `GET /api/v1/p1/tenants/{id}/ancestors` | 同上，`tenant.relation.ancestors` | 同上 | SYSTEM_ADMIN |
| 平台服务态 | `GET /api/v1/p1/platform/services` | `PlatformServiceStatusController` | `requirePlatformContext("platform.service.status", SYSTEM_ADMIN)` | SYSTEM_ADMIN |
| 平台审计中心 | `GET /api/v1/audit/events` | `PlatformAuditSearchController` | `requirePlatformContext("audit.events.search", AUDIT_ADMIN)` | **AUDIT_ADMIN** |
| 表单模板 | `GET /api/v1/p1/platform/form-templates` | `FormTemplateController.list` | `requirePlatformContext("form-template.list", SYSTEM_ADMIN)` | SYSTEM_ADMIN |
| | `GET /api/v1/p1/platform/form-templates/{id}` | `FormTemplateController.get` | `requirePlatformContext("form-template.get", SYSTEM_ADMIN)` | SYSTEM_ADMIN |

`ThreadLocalIsolationGuard.requirePlatformContext` 在隔离快照里没有活动平台上下文时抛 `ErrorCode.CONTEXT_PLATFORM_REQUIRED`，即 `P1.CONTEXT.0001`，消息原文为 `platform context required for <operation>`（`P1ApiExceptionHandler` 映射为 403）。平台上下文存在但 actor 不持有所需职责时抛 `P1.DUTY.0001`（`platform duty <DUTY> required for <operation>`）。

## 根因组合

1. **前端**：四页 helper 仍为 `auth: false`，不发 `Authorization` 与 `X-Nexus-Context-Id`，只发演示头。
2. **后端**：`P1PlatformReadContextFilter`（AUTH6 桥）只精确匹配 `GET /api/v1/p1/tenants` 与 `GET /api/v1/p1/tenant-registrations`。其他路径即使带了凭据，也不会得到平台上下文。
3. 演示上下文开关关闭，演示头不生效。于是守卫看到的隔离快照没有平台上下文 → `P1.CONTEXT.0001`。

两边都要补：只改前端，桥不认这些路径，仍是 `P1.CONTEXT.0001`；只改后端，前端不带凭据，会变成 `AUTH6.PLATFORM_READ_DENIED · missing_credentials`。

## 职责矩阵（不可混用）

| 接口族 | 桥要求的职责集合 | 桥绑定给 actor 的职责 | 权限检查 |
|---|---|---|---|
| 租户目录、注册审订（AUTH6F，已上线） | 恰好 `{platform_system_admin}` | `SYSTEM_ADMIN` | `tenant:list` |
| 租户关系、服务态、表单模板（G2） | 恰好 `{platform_system_admin}` | `SYSTEM_ADMIN` | 见下 |
| 审计事件（G3） | 恰好 `{platform_audit_admin}` | `AUDIT_ADMIN` | `audit:read` |

现在的桥把 actor 的职责硬编码为 `SYSTEM_ADMIN`。审计接口若照抄，控制器会以 `P1.DUTY.0001` 拒绝；若为了通过而给系统管理员绑上 `AUDIT_ADMIN`，则破坏三权分立。所以审计桥（G3）必须按路由绑定 `AUDIT_ADMIN`，并要求持有者的职责集合恰好是审计管理员。

权限检查：`PlatformDutyCatalog` 给系统管理员的授权里没有 `platform.service` 或 `form-template` 这类资源。G2 对系统管理员族沿用 `tenant:list`（AUTH6F 的平台只读基线），租户关系的子节点 / 祖先读取用 `tenant:get`。审计族用目录里已有的 `audit:read`。这样不新增授权、不需要 migration。

## 与 AUTH6F 白名单的边界

- 仍是**精确**匹配：`GET` + 固定路径，或固定路径加一个 UUID 段。不用前缀，不匹配任意子路径。
- **不进桥**的写操作：`PATCH /api/v1/p1/tenants/{id}/parent`（reparent）、`POST/PUT /api/v1/p1/platform/form-templates…`（新建、更新、validate、publish）、`POST /api/v1/audit/chain/verify`、租户创建 / 启停等。它们仍需要各自的授权路径，本波不处理。
- `GET /api/v1/p1/tenants/{id}`（租户详情）等其他平台读取不在本波四页范围内，保持原样。

## 注意：审计页对平台运营仍会被拒（设计如此）

三权分立下，`nexus-demo-platform`（`…0222`）只持 `platform_system_admin`。G3 之后，平台运营打开审计中心会得到 `AUTH6.PLATFORM_READ_DENIED · duty_set`（可读的拒绝），而不是数据。要在演示中看到审计列表，需要另一个只持 `platform_audit_admin` 的平台身份。U3 三人里没有这个身份，需要单独立卡，本波不新建身份、不改职责指派。

## 卡片

| 卡 | 内容 |
|---|---|
| G2 | 桥精确加入租户关系 3 条、服务态、表单模板列表 / 详情 GET；SYSTEM_ADMIN |
| G3 | 桥加入 `GET /api/v1/audit/events`；AUDIT_ADMIN，按路由绑定职责 |
| G4 | G2 + G3 路由测试：缺凭据、无效 context、错职责、成功、路由边界、写操作不进桥 |
| G5 | 前端：关系 / 服务态 / 表单模板 helper 带凭据 |
| G6 | 前端：审计 helper 带凭据，共享 options 常量，四页测试，验收文档 |
