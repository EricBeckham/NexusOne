# WEB 第二波任务卡 V0.1

- 基线：`main@c53f2eb`（B1 已合入，PR #292；五产品壳 W0–W5 关闭）
- 依据：壳波次复盘存货；`docs/planning/WEB五产品壳任务卡_V0.1.md` 缺口清单；`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md` §28/§29；`docs/planning/快进P4P5_范围冻结表_V0.1.md`
- 范围：桌面 WEB 第二波（权限正式页、权益配额页、应用工作台、P4/P5 读模型与子页）。**H5（`/m/*`、`web/src/mobile/**`）继续整波延后**
- 执行方：本地 Codex、Cursor 等按卡领取；NexusOne 只负责拆分与审阅

## 默认决策（可改）

1. **先吃已有 API**：X1 / X2 / X3 一律前端-only，禁止顺手改后端契约。
2. **P2 权限优先级最高（X1）**：路由仍指向 `PermissionStubView.vue`，但 `PermissionController` 与 `perm.*` 已齐。
3. **B1 只补读模型**：P4/P5 缺的是 list/read；写路径已有，本波不重做。
4. **P5 仅自用**：第三方开放平台 / 开发者门户 / 外部 OAuth 按冻结表整包后置；X5 不做对外门户。
5. **Credential CRUD**：B1 评估改动面小可同卡交付；否则标 B2/后置，不堵 X5 其它子页。
6. **不编造数字**：无数据一律「暂无」；X3 禁止伪造聚合 KPI。

## 所有任务通用约束

- 每个任务从最新 `origin/main` **新开分支 + 独立 worktree**；**禁止**在有人占用的 `D:\NexusOne` 主工作区直接改；**禁止**动 Codex worktrees。
- **一卡一 PR**；PR 只含本卡改动。
- UI 卡（X0–X5）：只改 `web/src`，**排除** `web/src/mobile/**`；不改后端。
- 后端卡（B1）：只改 Java 模块 / 必要的 Flyway / 对应测试；**不改前端**。
- 原有深链必须仍可打开（同路径或显式 redirect）。
- 交付前必须通过：
  - UI：`npm test`（**X0 之后须全绿**）、`npx vite build`、`npm run assert:h5`、`npm run assert:h5-every-page`、`git diff --check`
  - 后端：相关 Maven 模块测试全绿；租户隔离（冻结表「应用层三道防线」）有测试覆盖
- 页面不编造业务数据；空列表 / 空 KPI 显示「暂无」。

## 任务总览

| 编号 | 任务 | 分支 | 前置 | 建议执行方 | 负责人 | 状态 |
|---|---|---|---|---|---|---|
| X0 | 壳收尾小修（单测全绿 + 死代码 / later / KPI / 路由测） | `ui/x0-shell-polish` | 无 | 空闲者 | | 待领取 |
| X1 | P2 角色 / 数据范围 / 字段权限正式页 | `ui/x1-p2-permissions` | 无（建议 X0 先合） | Cursor / Codex | | 待领取 |
| X2 | P1 权益与配额正式页 | `ui/x2-p1-entitlement` | 无 | 空闲者 | Cursor | 进行中 |
| X3 | P3 应用工作台正式首页 | `ui/x3-p3-workbench` | 无 | 空闲者 | Cursor | 进行中 |
| B1 | P4/P5 读模型后端（list/read；Credential 视体积） | `feat/b1-p4-p5-read-models` | 无 | **建议 Codex** | | 已合并（PR #292，main `c53f2eb`） |
| F1 | 修复后端全量测试编译失败 | `fix/f1-backend-test-compile` | 无（与 X4/X5 独立，可并行） | 空闲者 | Cursor | 已合并（PR #299） |
| F2 | 修复 P3 集成测试缺少 `platform.exec_outbox` 表 | `fix/f2-p3-integration-test-db` | 无（建议 F1 先合） | 空闲者 | Cursor | 已合并（PR #301） |
| X4 | P4 规范 §28 子页 | `ui/x4-p4-subpages` | **B1 已合并（PR #292）** | 空闲者 | | 已合并（PR #293） |
| X5 | P5 规范 §29 子页（自用） | `ui/x5-p5-subpages` | **B1 已合并（PR #292）** | 空闲者 | Cursor | 进行中 |

顺序：**X0、X1、X2、X3、B1、F1、F2 可并行**；**X4 / X5 等 B1 合并后**再开；F2 建议 F1 先合。业务优先级：**X1 最高**。

---

## X0 壳收尾小修

分支：`ui/x0-shell-polish`　前置：无　类型：前端

要做（全部为壳波次复盘遗留，**不开新业务页**）：

1. **修预存红测**，使 `web/` 下 `npm test` 全绿：  
   `web/src/views/org/recovery-codes.browser.test.js`（复盘时 215 pass / 1 fail：重复核销路径找不到 `[data-testid=recovery-error]`）。壳 PR 未改该文件；X0 负责让基线变绿。
2. **死代码**：`web/src/views/ProductHomeView.vue` 路由未引用（仅渲染「暂无」）。删除，或明确挂到开发路由并补测——二选一，禁止继续漂着。
3. **侧栏 `later` 对齐 P1**：`web/src/nav/p3.js`、`p4.js`、`p5.js` 中无 `to` 的占位项补 `later: true`（与 `web/src/nav/p1.js` 的 relations/quotas/service/audit 一致），让 `web/src/layout/ProductSidebar.vue` 走 `v-else-if="item.later"` 的「后续」标签分支，而不是落入无标签的 `v-else`。
4. **KPI 加载态**：`web/src/views/market/MarketHomeView.vue`、`web/src/views/connect/ConnectHomeView.vue`（及同类）在 `loading===true` 时勿把空数组算成 `0` 塞进 `NxStatCard`；应显示加载中或让 value 为 `null` 以出「暂无」，避免闪零。
5. **AppShell 浏览器测路由补全**：`web/src/layout/AppShell.browser.test.js` 为侧栏链接注册匹配路由，消除 `No match found for location` 的 Vue Router warn。

验收：`npm test`、`npx vite build`、`npm run assert:h5`、`npm run assert:h5-every-page`、`git diff --check` 全过；无新增业务路由；深链不变。

---

## X1 P2 角色 / 数据范围 / 字段权限正式页（最高优先）

分支：`ui/x1-p2-permissions`　前置：无（建议 X0 已合）　类型：**前端-only**

存量 API（勿改契约）——`PermissionController`（`nexus-permission-core`），前缀 `/api/v1/permission`：

| 能力 | HTTP | FE `web/src/api/client.js` |
|---|---|---|
| 角色列表 / 创建 | `GET/POST /roles` | `perm.roles` / `perm.createRole` |
| 分配列表 / 创建 | `GET/POST /assignments` | `perm.assignments` / `perm.assign` |
| 授权格（含 scopeKind + fields） | `GET/POST /grants` | `perm.grants` / `perm.grant` |
| 矩阵总览 | `GET /matrix` | `perm.matrix` |
| 健康 / 快照 / 判定 | `GET /health`、`GET /snapshot`、`POST /check` | `perm.health` / `perm.snapshot` / `perm.check` |

现状：`web/src/router.js` 中 `/p2/permissions/:kind?` → `web/src/views/permission/PermissionStubView.vue`。

要做：

- 将 stub 换成正式页（可按 `kind` 分角色 / 分配 / 授权 / 矩阵，或单页 Tab）；侧栏「权限」指向真实可用界面。
- 列表 / 空态 / 错误态用 W1 组件（`NxPageHeader`、`NxEmpty`、`NxErrorState`、`NxNoPermission` 等）。
- 数据范围、字段规则只展示/提交后端已有字段（`GrantBody.scopeKind`、`fields`）；**禁止**前端本地发明权限码或 DataScope。
- 保留深链 `/p2/permissions` 与带 `kind` 的变体可打开。

验收：有 TenantContext 时可见真实数据或诚实空态；无上下文时明确提示；UI 门禁全过；**不改** Java。

---

## X2 P1 权益与配额正式页

分支：`ui/x2-p1-entitlement`　前置：无　类型：**前端-only**

存量 API——`TenantController`（`/api/v1/p1`）+ `web/src/api/client.js`：

- `GET /api/v1/p1/tenants/{id}/entitlement` → `p1.entitlement(id)`
- `GET /api/v1/p1/tenants/{id}/quotas` → `p1.quotas(id)`
- （只读展示为主；`POST .../quotas/consume` 已有，本卡不做运营扣减台，除非验收明确要求）

现状：`web/src/views/tenants/TenantDetailView.vue` 已局部调用；`web/src/nav/p1.js` 中 `quotas` 为 `later: true` 占位。

要做：

- 正式「权益 / 配额」页（建议 `/p1/tenants/:id/entitlement` 或 `/p1/quotas`，并从租户详情进入）；侧栏去掉该占位的「后续」或改为可点链接。
- 展示权益投影与配额使用量；无数据「暂无」；失败用 `NxErrorState`。
- 不编造限额数字。

验收：对真实租户 id 能拉到后端数据或诚实空/错；深链与租户详情不被破坏；纯前端。

---

## X3 P3 应用工作台正式首页

分支：`ui/x3-p3-workbench`　前置：无　类型：**前端-only（薄）**

存量 API：

- `GET /api/v1/p3/applications`、`GET /api/v1/p3/applications/{id}`（`ApplicationController`）
- FE：`p3.listApplications` 等（`web/src/api/p3.js` → `p3-live.js`）

现状：`web/src/views/studio/StudioHomeView.vue` 仍是快捷入口 + 会话信息；**无**工作台聚合 API。

要做：

- 将 `/p3` 做成「应用工作台」：列出应用（code / 名称 / 类型等后端已有字段），可进入现有台账 / 表单等深链。
- **禁止**假造「待办总数 / 发布成功率 / 聚合健康度」等没有 API 的指标；只能用列表长度等真实可算值，否则「暂无」。
- 保留现有快捷入口（台账 / 表单 / 待办 / …）以免回归。
- 流程设计器、发布中心继续侧栏「后续」（本卡不做）。

验收：有应用时列表来自 API；无应用「暂无」；不新增后端。

---

## B1 P4/P5 读模型后端

状态：已合并（PR #292，main commit `c53f2eb`）。因此 X4 / X5 现已解锁，可领取并行执行。

分支：`feat/b1-p4-p5-read-models`　前置：无　类型：**后端-only**　建议执行方：Codex

### 已有（不要重复造写路径）

P4 `MarketController`（`/api/v1/p4`）：

- 写：`POST /apps`、`/apps/{appId}/versions`、版本 review/approve/publish/unpublish、`POST /skus`、`POST /tenants/{tenantId}/installations`、`/installations/{id}/upgrade|rollback|uninstall`
- 读：仅 `GET /catalog`

P5 `ConnectController`（`/api/v1/p5`）：

- 写：`POST /clients`、`POST /webhooks`、`POST /events`、`POST /webhooks/dispatch`、`POST /connectors/{code}/run`
- 读：`GET /connectors`、`GET /connector-runs`
- 调用：`POST /internal/call`（执行，不是日志列表）

FE 对照（只读参考，B1 PR **不得**改这些文件）：`p4.catalog`、`p5.issueClient`、`p5.createWebhook`、`p5.connectors`、`p5.connectorRuns`（`web/src/api/client.js`）。

### 本卡要补的读模型

**P4（规范 §28 子页所需）：**

- 应用包 / 版本列表与详情（不止 catalog 聚合）
- SKU 列表与详情
- 订阅 / 安装实例列表（按租户隔离）

**P5（规范 §29 自用子页所需）：**

- API Client **列表 / 详情**（现只有签发 POST）
- Webhook 订阅 **列表 / 详情**
- 调用日志 **列表**（基于 internal call / 投递记录落库或只读视图）
- **Credential**：若改动面小，可同卡做最小 CRUD；否则在 PR 说明挪到 B2，并在「后置」登记

### 硬约束

- **租户隔离**：遵守 `docs/planning/快进P4P5_范围冻结表_V0.1.md`「应用层三道防线」；测试覆盖跨租户不可读。
- 需要时才加 Flyway；无 schema 变更则不加。
- **零前端文件**出现在本 PR。
- 对应模块测试必须绿。

验收：MockMvc 或等价证明 list/read；契约字段写进 PR 说明供 X4/X5 使用。

---

## F1 修复后端全量测试编译失败

分支：`fix/f1-backend-test-compile`　前置：无（与 X4 / X5 独立，可并行）　类型：后端测试修复

问题：

- `T2PlatformAdminAuditTest` 内部的 `InMemoryTenantRepository` 测试替身没有实现仓库接口新增的 `updateProjection` 方法，导致后端 reactor `testCompile` 失败。
- 该问题已存在于 `main`，并非 B1 引入。

要做：

- 仅修改测试代码，使测试替身实现 `updateProjection`，恢复后端全量测试编译与运行。
- 小 nit：`ConnectTenantReadTest` 存在重复 import，可顺手清理；该项为可选，不影响验收。

范围：只改测试代码。

验收：全量 Maven 测试能编译并运行，且 `T2PlatformAdminAuditTest` 通过。

交付：单独一个 PR；预计 1 小时。

---

## F2 修复 P3 集成测试缺少 `platform.exec_outbox` 表

分支：`fix/f2-p3-integration-test-db`　前置：无（建议 F1 先合；与 X4 / X5 独立，可并行）　类型：后端测试健康

问题：P3 path 集成测试依赖 `platform.exec_outbox` 表，但本地测试库没有该表。

要做：

- 先查清该表应由哪个迁移创建，并区分是迁移缺失、迁移未在测试环境执行，还是测试环境配置问题。
- 按根因修复测试环境或迁移执行路径；如需新增迁移，单独成卡、单独 PR，本卡不直接扩大为迁移开发。

依赖：无；建议 F1 先合。

验收：该集成测试在本地全新库上通过，且不影响其他模块。

---

## X4 P4 规范 §28 子页

分支：`ui/x4-p4-subpages`　前置：**B1 已合并**　类型：前端

要做：

- 将 `web/src/nav/p4.js` 中「应用包 / Product·SKU / 订阅 / 安装升级」等占位改为真路由页面（商业权益 / 运营分析若仍无 API 则继续「后续」）。
- 对接 B1 读模型 + 现有 `p4.catalog` / install / upgrade 等；按需扩展 `web/src/api/client.js` 的 `p4.*`。
- 空态「暂无」；保留 `/p4/market` 深链。

验收：UI 门禁全过；无假数据；不改 Java（除非发现 B1 契约 bug，另开修复卡）。

---

## X5 P5 规范 §29 子页（自用）

分支：`ui/x5-p5-subpages`　前置：**B1 已合并**　类型：前端

要做：

- 将 `web/src/nav/p5.js` 中 API Client / Webhook / Connector / 调用日志（及 B1 已交付的 Credential）做成真页面；对接 B1 list/read + 现有 `p5.connectors` / `connectorRuns` / `issueClient` / `createWebhook`。
- **范围 = 自用**：不做第三方开发者门户、外部 OAuth 生态、公开 API 市场文案。
- 「开放接口」目录若 B1 未交付 API 目录读模型，保持「后续」。
- 保留 `/p5/connect` 深链。

验收：同 X4 UI 门禁；无假数据；纯前端。

---

## 缺口 / 后置（下一轮，需先后端或冻结表禁止本波）

| 项 | 原因（复盘事实） |
|---|---|
| P1 租户关系树 | 无 relation-tree API / FE client |
| P1 平台服务态 | 无服务态聚合；仅松散 runtime/meta 一类 |
| P1 平台审计中心 | 仅有 `GET /api/v1/audit/traces/{traceId}` 与租户 `audit-events`；缺平台级检索列表 |
| P2 身份集成配置卡 | 仅有 Keycloak/Stub IdP 装配；无集成配置 CRUD API |
| P3 流程设计器 | Process API 仅实例/任务审批，无定义/设计器读写 |
| P3 发布中心 | 仅有表单 publish/publish-log、业务类型 provision；无统一发布中心聚合 |
| P5 开放 API 目录 | 无目录读模型；且第三方开放平台整包后置 |
| B1 未纳入的 Credential 大件 | 若 B1 声明延期，则进 B2 |
| **H5** | `/m/*`、`web/src/mobile/**` 继续延后 |
| **S2 治理、备份/恢复、开发者门户** | 见 `docs/planning/快进P4P5_范围冻结表_V0.1.md` 整包后置 |

工程结构（`products/p1-tenant`… + `layouts/`）仍不强制大搬迁。

---

## 修订记录

- 2026-09-24：Cursor 根据壳波次（W0–W5）复盘 API 存货编写 V0.1；基线 `origin/main@0fac808`（含 #287 W5）；规划文档按 Eric 要求直推 `main`。
- 2026-09-24：B1 已合入 `main@c53f2eb`（PR #292）；X4 / X5 解锁。
- 2026-09-24：Cursor 从 `origin/main@fef30e5` 领取 X2，分支 `ui/x2-p1-entitlement`。权益与配额页开工。
- 2026-09-24：Cursor 从 `origin/main@c5b0b02` 领取 X3，分支 `ui/x3-p3-workbench`。星构应用工作台开工。
- 2026-09-24：Cursor 从 `origin/main@c53f2eb` 领取 X5，分支 `ui/x5-p5-subpages`。P5 自用子页开工。开放接口目录与 Credential 仍为后续：B1 未提供这两类读模型。
- 2026-09-24：Cursor 从 `origin/main@f02c2b7` 领取 F1，分支 `fix/f1-backend-test-compile`。测试替身补 `updateProjection`，不改产品代码。
- 2026-09-24：F1 已合并（PR #299）。
- 2026-09-24：Cursor 从 `origin/main@648d60e` 领取 F2，分支 `fix/f2-p3-integration-test-db`。`platform.exec_outbox` 由 V500 创建，全新测试库已执行该迁移；路径测试失败点是 text 列与 uuid 参数比较。
- 2026-09-24：F2 已合并（PR #301）。
