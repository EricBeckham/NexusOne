# WEB 第五波任务卡（K 波 · 可配置审批产品 E2E）V0.1

- 基线：`main@fa5d276e`（`docs(planning): blank line before later section after K-wave pointer`；M10 #358 / M11 #359 / M21 #360 / M22 #357 已合；M20 #356 已合）
- 依据：Eric 确认的第五波主线（表单模板 + 流程 + 默认权限打包成产品 → 租户自助订阅 → 一级审批跑通）；`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md` §25–§29；`docs/planning/快进P4P5_范围冻结表_V0.1.md`；`docs/planning/WEB第四波任务卡_V0.1.md`（M 波交付与未合登记）
- 范围：打通「平台配置最简单业务审批产品 → 租户订阅即用 → 发起 / 待办审批 / 查看结果」端到端闭环。审批产品**不写死成业务模块**，用平台能力配出。**本波明确不做 H5**（无 `/m/*`、无 `web/src/mobile/**`）
- 执行方：本地 Codex / Cursor 按卡领取；NexusOne 只负责拆分与审阅

## 默认决策

1. **一卡一产品 + 一种资源类型或一个薄片**；后端 / 前端分卡；**新表 / Flyway 单独成卡**；**密钥 / 权限开通等安全敏感单独成卡**；**仅测试**可单独成卡。
2. **主线 = 可配置审批产品 E2E**：平台表单模板版本 + P3 流程定义版本 + 默认权限 → 打成 P4 产品 → 租户管理员自助订阅（无审核、无支付）→ 运行时发起与一级审批。
3. **平台表单模板 ≠ 现有 P3 `business_meta.form`**：现有表单是**租户作用域**且绑定 `ledger_code` + `field_code`（M0-07）。本波新建**平台级**表单模板（字段名 / key / 类型 / 必填 / 下拉选项），供产品绑定；第一版字段类型：文本、多行文本、数字、金额、日期、单选下拉、成员选择、附件。
4. **第四波依赖**：流程定义发布复用 **M10（#358 已合）/ M11（#359 已合）**；权益投影复用 **M20（#356 已合）**；平台哈希链校验 HTTP 复用 **M22（#357 已合）**。**M21（#360 已合）**；**M23 仍未合**，不阻塞本波 E2E。
5. **租户隔离硬约束**：每个后端读 / 写带租户边界 + 跨租户否定测试；平台级接口须平台职责。
6. **不编造数据**：空态「暂无」；演示种子只走 local/demo 开关，并标明演示数据。
7. **第一版明确不做**：会签 / 或签、条件分支、转交加签、拖拽式设计器、支付、平台审核订阅、订阅后跟随模板新版本升级、H5。

## 所有任务通用约束

- 从最新 `origin/main` **新开分支 + 独立 worktree**；**禁止**在占用中的 `D:\NexusOne` 直接改；**禁止**动 `C:\Users\Administrator\.codex\worktrees\`。
- **一卡一 PR**；PR 只含本卡；**PR 标题以卡号开头**（如 `K2: ...`）。
- UI 卡：只改 `web/src`，**排除** `web/src/mobile/**`。
- 后端 / 安全 / 测试卡：不改前端（除非卡面写明）。
- 迁移卡：只含 Flyway + 契约测试。
- 写路径必须经 `AuditPort.append` / `appendNewTransaction`（`nexus-audit-core` → `AuditPort`）；细节已脱敏。
- Flyway **冲突区单写**：迁移卡串行合入。
- 交付门禁：
  - UI：`npm test`、`npx vite build`、`npm run assert:h5`、`npm run assert:h5-every-page`、`git diff --check`
  - 后端：相关模块测试绿；**含跨租户不可读 / 不可写测试**
- 原有深链保持可开。

---

## K0 — 修复卡占位

- **状态**：占位。第四波（及本波前期）统一审查完成后再补具体修复卡。
- **说明**：不占用 K1 起的业务编号；若审查产出必须先修的缺陷，在本小节追加 `K0.x` 并注明阻塞哪些 K 卡。

---

## 一、分功能现状（已有 vs 缺失）

> 摸查基线：`origin/main@fa5d276e`。下列「已有」均给路径；**未在代码中证实的不写成已有**。

### 0) 第四波依赖卡合入快照（@fa5d276e）

| 卡 | 状态 | 对本波 |
|---|---|---|
| M5–M9（流程草稿表 / CRUD / 编辑 / 校验 API·UX） | **已合**（#347–#354） | 可做产品绑定前的流程草稿与校验 |
| **M10**（流程定义发布 API） | **已合**（#358，`ProcessDefinitionPublishService`；草稿 `POST .../drafts/{key}/publish`；`SequentialApprovalService` 可开已发布键） | 产品绑定流程版本 + 非预置键开单 **已可用** |
| **M11**（流程设计器发布 UX） | **已合**（#359，`ProcessViewerView.vue` 发布确认） | 设计器路径发布可用 |
| M16–M19（Credential） | **已合**（#348–#355） | 无硬依赖 |
| **M20**（商业权益只读投影） | **已合**（#356） | 订阅后权益投影可复用 |
| **M21**（商业权益页） | **已合**（#360，`CommercialEntitlementsView`；侧栏 `entitlements` 已去 later） | E2E **非阻塞**；可只读核对订阅权益 |
| **M22**（平台审计哈希链校验 API） | **已合**（#357，`AuditChainVerifyController` `/api/v1/audit/chain`） | E2E **非阻塞**；可并行外挂按钮 |
| M23（审计中心「校验哈希链」按钮） | **未合** | E2E **非阻塞** |

### 1) P3 流程运行时

| | 内容 |
|---|---|
| **已有** | 表：`business_meta.process_instance` / `process_todo` / `process_action`（`nexus-p3-studio/.../V550__process_metadata.sql`）；状态含 `RUNNING` / `APPROVED` / `REJECTED` / `WITHDRAWN`。服务：`SequentialApprovalService`（`.../flow/SequentialApprovalService.java`；**M10 后可启动已发布 process key**）。HTTP：`ProcessController`（`/api/v1/process/instances`、`/tasks/{todoId}/approve|reject`、`/instances/{id}/withdraw`）；`TodoController`（pending/done/initiated）。引擎：Flowable，条件属性 `nexus.flow.engine`。 |
| **缺口 / 差异** | approve/reject **无意见字段**（桌面待办页已有意见框但注明 API 无 comment）。本波产品态状态只展示 **进行中 / 通过 / 驳回**（不把撤回当第一版主路径）。 |
| **结论** | 运行时与已发布定义开单 **已具备**；本波补 **审批意见** 与 **产品申请编排**。 |

### 2) P3 流程定义草稿 / 校验 / 发布

| | 内容 |
|---|---|
| **已有** | 草稿表 / CRUD / 校验（M5–M9）；**发布 API（M10）** + **发布 UX（M11）**；已发布定义进入目录并可开单。 |
| **本波用法** | 产品定义绑定**已发布**流程 key/version；K15b 按钉住版本启动一级审批。 |

### 3) 表单

| | 内容 |
|---|---|
| **已有** | `business_meta.form` / `form_binding` / `form_version` / `form_submission`（`V530__form_metadata.sql`）；`FormService` Draft→Validate→Publish；`FormController` `/api/v1/p3/forms`；FE `FormStudioView.vue` / `FormListView.vue`。`WidgetType`：`TEXT/TEXTAREA/NUMBER/DATE/BOOLEAN/PERSON/DEPT/POSITION`（**无** MONEY / SELECT / ATTACHMENT）。G18 `BusinessPathController` `POST /api/v1/p3/path/submit-start` 可填报并启动审批。 |
| **缺口** | 表单为**租户作用域**且绑定台账字段，不是平台管理员定义的独立模板；编辑器强调 ledger 绑定，不是「字段列表 + 右侧预览」的产品模板编辑器。 |
| **决策** | **新建平台表单模板资源**；不把台账表单主路径改造成产品模板。 |

### 4) P4 商品目录 / SKU / 订阅 / 安装

| | 内容 |
|---|---|
| **已有** | 表：`market.app_package` / `app_version` / `sku` / `subscription` / `installation`（`V600__p4_market.sql`）。`MarketService.subscribeAndInstall`：订阅 + 权益投影到 P1 + 安装（`nexus-p4-market/.../MarketService.java`）。HTTP：`MarketController` `/api/v1/p4/...`（含 `POST /tenants/{tenantId}/installations`）。目录 FE：`web/src/views/market/*`。M20：`CommercialEntitlementController` `GET /api/v1/p4/commercial-entitlements`。Demo：`P4DemoSeedConfiguration`（**明确不 install/subscribe**）。 |
| **缺口** | SKU/版本**不绑定**表单模板版本或流程定义版本；订阅后**不会**下发模板/流程，也**不会**按产品开通默认发起权限包。 |
| **复用** | 目录 / SKU / 订阅 / 安装 / 权益投影尽量复用；本波加「产品定义」与开通时钉版本（K12a）+ 默认权限包（K12b）。 |

### 5) P1 / P2 租户、成员、选人

| | 内容 |
|---|---|
| **已有** | `OrganizationController`：`GET/POST /api/v1/persons`、部门树、任职、memberships（`nexus-p2-iam`）。现有填报页已用人员列表做审批人下拉。 |
| **缺口** | 无独立成员选择器组件硬性要求；本波 FE 可内联下拉。 |

### 6) 产品壳入口

| | 内容 |
|---|---|
| **已有** | `web/src/nav/products.js`：P1–P5。P3 含表单 / 待办 / 流程设计器；P4 含目录 / 订阅 / 安装。 |
| **缺口** | 租户订阅后，没有「进入该业务审批产品」的已开通应用运行入口（不是第六个平台产品，而是**已安装应用运行页**）。 |

### 7) Flyway 最大版本与命名

| 窗口 | 现状（`*/db/migration/V*.sql`） |
|---|---|
| 平台 bootstrap | `V001`–`V004`（含 `V004__platform_secret_ciphertext.sql`） |
| P1 | `V100`–`V102` |
| P2 | `V200`–`V202` |
| permission | `V300` |
| P3 | `V400`；表单 `V530`–`V531`；流程 `V550`–`V551` |
| event/audit/file/notify | `V500`–`V504`、`V510`–`V511`、`V520` |
| P4 | `V600` |
| P5 | `V700`–`V704` |
| **命名** | `V{数字}__{snake_desc}.sql`；**全局版本号不重复** |
| **本波建议占用** | 平台模板 `V005__platform_form_template.sql`；申请单 `V552__product_application.sql`；意见列（若需）`V553__process_todo_comment.sql`；产品定义 `V601__market_product_definition.sql` |

### 8) 审计

| | 内容 |
|---|---|
| **已有** | `AuditPort.append` / `appendNewTransaction`；`verifyChain()`；**M22** 平台 HTTP `GET/POST /api/v1/audit/chain`（`AuditChainVerifyController`）。 |
| **要求** | 模板发布、产品上架、订阅开通、权限下发、发起、同意 / 驳回均写审计。 |

### 9) 附件

| | 内容 |
|---|---|
| **已有** | `FileController` `/api/v1/files`（`nexus-file-core`；`V510`/`V511`）。 |
| **用法** | 模板字段 `ATTACHMENT` 存本租户 `file_id`；不重做对象存储。 |

---

## 二、任务总览

| 编号 | 标题 | 产品 | 类型 | 依赖 | 可并行 | 预估 |
|---|---|---|---|---|---|---|
| **K0** | 修复卡占位 | — | — | 审查后补 | — | — |
| K1 | 平台表单模板表迁移 | 平台 | 迁移 | 无 | Flyway 串行 | 1h |
| K2 | 平台表单模板草稿 CRUD API | 平台 | 后端 | K1 | 其后 K3/K4 | 1–2h |
| K3 | 平台表单模板编辑页（列表+预览） | 平台 | 前端 | K2 | 是 | 1–2h |
| K4 | 平台表单模板校验 API | 平台 | 后端 | K1 | 可与 K2 后并行 | 1h |
| K5 | 平台表单模板校验 UX | 平台 | 前端 | K4、K3 | 是 | 1h |
| K6 | 平台表单模板发布 API | 平台 | 后端 | K4 | 其后 K7 | 1–2h |
| K7 | 平台表单模板发布 UX | 平台 | 前端 | K6、K5 | 是 | 1h |
| K8 | 市场产品定义表迁移 | P4 | 迁移 | 无 | Flyway 串行（见 §四） | 1h |
| K9 | 产品定义绑定 API | P4 | 后端 | K8、K6；**M10（已合）** | 其后 K10 | 1–2h |
| K10 | 产品定义绑定页 | P4 | 前端 | K9；**M11（已合）** | 是 | 1–2h |
| K11 | 产品定义挂接 SKU / 目录 API | P4 | 后端 | K9 | 其后 K12a | 1–2h |
| K12a | 租户自助订阅并钉版本开通 API | P4 | 后端 | K11；**M20（已合）** | 其后 K12b/K13 | 1–2h |
| K12b | 开通默认权限包下发（安全） | P4 | **安全** | K12a | 其后发起链 | 1–2h |
| K13 | 租户目录「订阅开通」UX | P4 | 前端 | K12a（权限就绪建议等 K12b） | 是 | 1–2h |
| K14 | 产品申请单表迁移 | P3 | 迁移 | 无 | Flyway 串行（见 §四） | 1h |
| K15a | 按钉住模板校验 values_json（可测校验器） | P3 | 后端 | K6、K12a | 可与 K14 后并行 | 1–2h |
| K15b | 创建申请并按钉住流程启动一级审批 API | P3 | 后端 | K15a、K14、K12a（需默认权限时 **K12b**）；**M10（已合）** | 其后 K16 | 1–2h |
| K16 | 已开通产品发起页（模板渲染） | P3/P4 | 前端 | K15b、K13 | 是 | 1–2h |
| K17 | 审批同意/驳回意见 API | P3 | 后端 | 无（现有 approve/reject） | **可最先并行** | 1–2h |
| K18 | 我的待办意见 + 我的申请结果 UX | P3 | 前端 | K17；结果依赖 K15b | 是 | 1–2h |
| K19 | 订阅/模板/申请跨租户否定测试 | 平台 | 仅测试 | K2、K12a、K12b、K15a、K15b | 建议其后 | 1–2h |
| K20 | 演示种子：简单审批产品包 | 平台 | 种子 | K11、K6；**M10（已合）** | 仅 local/demo | 1–2h |

**类型合计（不含 K0）**：迁移 3 · 后端 9 · 前端 7 · **安全 1** · 仅测试 1 · 种子 1 → **22 张业务卡 + K0**。

---

## 三、任务卡正文

### K1 — 平台表单模板表迁移

- **类型**：迁移　**分支**：`mig/k1-platform-form-template`　**前置**：无
- **目标**：`V005__platform_form_template.sql`：`platform.form_template` / `platform.form_template_field` / `platform.form_template_version`（模板头、草稿字段、不可变版本快照）。字段类型 CHECK：`TEXT|TEXTAREA|NUMBER|MONEY|DATE|SELECT|PERSON|ATTACHMENT`。注册 `platform.table_registry`（scope=platform，tenant_required=false）。
- **不做**：改 `business_meta.form*`；发布 / 校验业务逻辑；前端。
- **依赖**：无
- **验收**：migrate 绿；契约测试绿；与 `V530` 并存无冲突。
- **PR 标题**：`K1: add platform form template tables`

### K2 — 平台表单模板草稿 CRUD API

- **类型**：后端　**分支**：`feat/k2-form-template-draft-api`　**前置**：K1
- **目标**：平台管理员创建 / 列表 / 获取 / 更新草稿（字段列表按序保存）。租户上下文 → FORBIDDEN/404。写审计。
- **不做**：validate / publish；租户直接改平台草稿。
- **依赖**：K1
- **验收**：CRUD 绿；非平台否定；零前端文件。
- **PR 标题**：`K2: platform form template draft CRUD`

### K3 — 平台表单模板编辑页（列表 + 右侧预览）

- **类型**：前端　**分支**：`ui/k3-form-template-editor`　**前置**：K2
- **目标**：平台壳入口「表单模板」：字段列表编辑（名称、key、类型、必填；SELECT 编辑选项）；右侧只读预览。**不做拖拽设计器**。
- **不做**：validate / publish 可用态（可灰置，归 K5/K7）；H5。
- **依赖**：K2
- **验收**：UI 门禁；空「暂无字段」。
- **PR 标题**：`K3: form template editor list and preview`

### K4 — 平台表单模板校验 API

- **类型**：后端　**分支**：`feat/k4-form-template-validate`　**前置**：K1
- **目标**：`POST .../form-templates/{id}/validate`：≥1 字段；key 唯一合法；SELECT 必有选项；失败字段级错误；成功 → `VALIDATED`。写审计。
- **不做**：publish；改台账表单校验。
- **依赖**：K1
- **验收**：合法 / 非法用例；跨职责否定。
- **PR 标题**：`K4: validate platform form templates`

### K5 — 平台表单模板校验 UX

- **类型**：前端　**分支**：`ui/k5-form-template-validate`　**前置**：K4、K3
- **目标**：校验按钮 + 错误到字段；成功态可见。
- **不做**：publish。
- **依赖**：K4、K3
- **验收**：UI 门禁。
- **PR 标题**：`K5: form template validate UX`

### K6 — 平台表单模板发布 API

- **类型**：后端　**分支**：`feat/k6-form-template-publish`　**前置**：K4
- **目标**：仅 `VALIDATED` 可发布；生成不可变 `form_template_version` 快照并切换 running；再改草稿须重校验；已发布版本不可改。写审计。
- **不做**：自动推送到已订阅租户（第一版不升级跟随）。
- **依赖**：K4
- **验收**：发布后版本只读；失败不半发布。
- **PR 标题**：`K6: publish platform form template versions`

### K7 — 平台表单模板发布 UX

- **类型**：前端　**分支**：`ui/k7-form-template-publish`　**前置**：K6、K5
- **目标**：发布确认 + 版本号 + 失败原文。
- **不做**：版本 diff 大件。
- **依赖**：K6、K5
- **验收**：UI 门禁。
- **PR 标题**：`K7: form template publish UX`

### K8 — 市场产品定义表迁移

- **类型**：迁移　**分支**：`mig/k8-market-product-definition`　**前置**：无（**合入序见 §四**）
- **目标**：`V601__market_product_definition.sql`：`market.product_definition`（code、name、form_template_version_id、process_definition_key、process_definition_version、default_permission_pack jsonb、status…）；可选 `market.sku_product_binding(sku_code, product_definition_id)`。
- **不做**：业务 API；改写 `V600` 历史脚本。
- **依赖**：无
- **验收**：migrate + 契约测试绿。
- **PR 标题**：`K8: add market product definition tables`

### K9 — 产品定义绑定 API

- **类型**：后端　**分支**：`feat/k9-product-definition-bind`　**前置**：K8、K6；**M10（已合）**
- **目标**：平台管理员 CRUD 产品定义：绑定**已发布**表单模板版本 + **已发布**流程定义 key/version + 默认权限包说明（如：租户成员可发起；审批人来自表单 PERSON 字段）。引用不存在或未发布 → 4xx。写审计。
- **不做**：支付字段；租户编辑产品定义；会签等高级模型校验；权限实际下发（归 **K12b**）。
- **依赖**：K8、K6、**M10（已合）**
- **验收**：绑定校验绿；跨职责否定。
- **PR 标题**：`K9: bind form and process versions on product definition`

### K10 — 产品定义绑定页

- **类型**：前端　**分支**：`ui/k10-product-definition`　**前置**：K9；**M11（已合）**
- **目标**：平台侧选择已发布模板版本、已发布流程版本、展示默认权限说明并保存。
- **不做**：拖拽编排；租户可见此页。
- **依赖**：K9、**M10/M11（已合）**
- **验收**：UI 门禁；无假「已绑定租户数」。
- **PR 标题**：`K10: product definition binding page`

### K11 — 产品定义挂接 SKU / 目录 API

- **类型**：后端　**分支**：`feat/k11-sku-product-binding`　**前置**：K9
- **目标**：将 `product_definition` 挂到 `market.sku`（或创建薄 SKU）；目录可解析到产品定义；复用现有 app_version 发布态机。写审计。
- **不做**：支付 / 审核流；灰度渠道。
- **依赖**：K9
- **验收**：目录项可解析 product_definition；无定义 SKU 行为不变。
- **PR 标题**：`K11: attach product definition to SKU catalog`

### K12a — 租户自助订阅并钉版本开通 API

- **类型**：后端　**分支**：`feat/k12a-tenant-self-subscribe-pin-versions`　**前置**：K11；**M20（已合）**
- **目标**：租户管理员对已发布目录项「订阅开通」：**无平台审核、无支付**。在 `subscribeAndInstall` 之上：① 复用订阅 / 安装 / 权益投影（M20）；② **钉住**表单模板版本 id 与流程定义 key/version 到开通记录（installation 旁表或扩展列）；③ 审计。重复开通：**幂等返回已有记录或 409**（二选一，卡内写死并测）。**第一版不做**订阅后跟随模板新版本升级。
- **不做**：默认权限包下发（归 **K12b**）；支付；平台审批工单；跨租户开通；升级迁移。
- **依赖**：K11、**M20**
- **验收**：开通后可读钉住版本 id；幂等或 409 行为稳定；跨租户否定；无金额字段；写审计。
- **PR 标题**：`K12a: tenant self-subscribe pins form and process versions`

### K12b — 开通默认权限包下发（安全）

- **类型**：**安全**　**分支**：`sec/k12b-provision-default-permission-pack`　**前置**：K12a
- **目标**：在 K12a 开通成功后（同事务或明确后置步骤）下发**默认权限包**：本租户成员可发起该产品申请；审批人由发起人从**本租户**成员中选择（与 SurfaceGuard / 既有 permission 模型对齐）。权限**不得**落到其他租户；写审计；带跨租户与越权否定测试（他租户成员不能获得本租户发起权；非成员不能发起）。
- **不做**：自定义角色矩阵大件；平台级授权 UI；改 M20 权益投影语义。
- **依赖**：K12a
- **验收**：开通后本租户成员可发起、他租户不可；越权否定绿；审计含权限下发事件；零前端文件（除非卡面另行批准）。
- **PR 标题**：`K12b: provision tenant-scoped default permission pack`

### K13 — 租户目录「订阅开通」UX

- **类型**：前端　**分支**：`ui/k13-tenant-subscribe`　**前置**：K12a（完整开通体验建议等 **K12b**）
- **目标**：目录安装升级为「订阅开通」文案；标明无支付 / 即时开通；成功进「我的已开通」；失败展示服务端原文。复用 `web/src/views/market/*`，不新建 H5。
- **不做**：支付 UI；假审批进度。
- **依赖**：K12a（建议 K12b）
- **验收**：UI 门禁；空目录「暂无」。
- **PR 标题**：`K13: tenant subscribe and provision UX`

### K14 — 产品申请单表迁移

- **类型**：迁移　**分支**：`mig/k14-product-application`　**前置**：无（**合入序见 §四**）
- **目标**：`V552__product_application.sql`：`business_meta.product_application`（tenant_id、installation_id 或 product_definition_id、form_template_version_id、process_instance_id、values_json、status∈`RUNNING|APPROVED|REJECTED`、initiator_person_id…）。第一版 API/UI **不暴露 WITHDRAWN**。租户索引 + table_registry。
- **不做**：API；改 Flowable 引擎表。
- **依赖**：无
- **验收**：migrate + 契约测试绿。
- **PR 标题**：`K14: add product application table`

### K15a — 按钉住模板校验 values_json（可测校验器）

- **类型**：后端　**分支**：`feat/k15a-template-values-validator`　**前置**：K6、K12a
- **目标**：抽取**可单独单测**的校验器 / 服务：按开通记录钉住的表单模板版本校验 `values_json`——必填、类型、SELECT 选项、PERSON 必须为本租户成员、ATTACHMENT 必须为本租户 `file_id`。提供校验 API **或**纯内部服务 + 充分单元测试（卡内二选一，须可独立跑绿）。失败返回字段级错误，**不**建申请、**不**启流程。
- **不做**：创建 `product_application`；启动流程（归 **K15b**）；改台账 FormService。
- **依赖**：K6、K12a
- **验收**：合法 / 非法用例单测绿；跨租户 PERSON/file 否定；零前端文件。
- **PR 标题**：`K15a: validate values_json against pinned template version`

### K15b — 创建申请并按钉住流程启动一级审批 API

- **类型**：后端　**分支**：`feat/k15b-start-product-application`　**前置**：K15a、K14、K12a；需默认发起权时 **K12b**；**M10（已合）**
- **目标**：租户成员发起：先走 K15a 校验；通过后创建 `product_application`；按产品钉住的 process key/version 启动**一级**审批（审批人来自 PERSON 字段；**M10 已支持已发布键**）；状态 `RUNNING`；写审计。
- **不做**：校验器本体（K15a）；多级 / 会签 / 或签 / 条件分支；撤回主路径。
- **依赖**：K15a、K14、K12a、（建议）K12b、**M10（已合）**
- **验收**：校验失败不建单；成功后待办可见；跨租户 installation → NOT_FOUND；写审计。
- **PR 标题**：`K15b: create application and start pinned one-level approval`

### K16 — 已开通产品发起页（模板自动渲染）

- **类型**：前端　**分支**：`ui/k16-product-start-form`　**前置**：K15b、K13
- **目标**：从「我的已开通应用」进入发起页；按模板版本渲染（含 SELECT / PERSON / ATTACHMENT / MONEY）；提交 K15b；成功跳转我的申请或待办深链。
- **不做**：拖拽；H5；自定义布局引擎。
- **依赖**：K15b、K13
- **验收**：UI 门禁；无运行版本时诚实空态。
- **PR 标题**：`K16: render start form from provisioned template`

### K17 — 审批同意 / 驳回意见 API

- **类型**：后端　**分支**：`feat/k17-approval-comment`　**前置**：无
- **目标**：为 `POST /api/v1/process/tasks/{todoId}/approve|reject` 增加可选 `comment`；持久化（若需 DDL：极小迁移 `V553__process_todo_comment.sql`——**超 1h 则拆 K17a 迁移卡，禁止超大 PR**）。写审计；跨租户否定。
- **不做**：转交 / 加签；改状态机。
- **依赖**：无（可与 K1 并行）；合入序若含 V553 见 §四
- **验收**：有 / 无意见均可；跨租户不可批。
- **PR 标题**：`K17: approval approve reject with comment`

### K18 — 我的待办意见 + 我的申请结果 UX

- **类型**：前端　**分支**：`ui/k18-todo-and-my-requests`　**前置**：K17；申请列表字段依赖 **K15b**
- **目标**：待办意见真正提交 API（去掉「仅本页」文案）；「我的申请」展示进行中 / 通过 / 驳回与意见。本波隐藏撤回入口。
- **不做**：H5 待办；复杂筛选。
- **依赖**：K17、K15b
- **验收**：UI 门禁；空「暂无」。
- **PR 标题**：`K18: wire approval comment and my requests`

### K19 — 订阅 / 模板 / 申请跨租户否定测试（仅测试）

- **类型**：仅测试　**分支**：`test/k19-cross-tenant-negatives`　**前置**：K2、K12a、K12b、K15a、K15b
- **目标**：租户 A 不能改平台模板；不能动 B 的开通；不能用 B 的 PERSON/file 通过 K15a；不能经 K15b 开 B 的单；不能批 B 的待办；K12b 权限不泄漏到他租户。断言 NOT_FOUND/FORBIDDEN。
- **不做**：新功能。
- **依赖**：K2、K12a、K12b、K15a、K15b
- **验收**：测试绿；无前端 / 无生产功能 diff。
- **PR 标题**：`K19: cross-tenant negatives for product E2E`

### K20 — 演示种子：简单审批产品包

- **类型**：种子　**分支**：`feat/k20-demo-approval-product-seed`　**前置**：K11、K6；**M10（已合）**
- **目标**：仅 local/demo 开关（沿用现有 DemoProfileGate / P4 demo 模式）：已发布表单模板（含文本 + PERSON 等最小字段）+ 已发布一级审批流程（走 M10 发布语义）+ 产品定义 + SKU + 目录 PUBLISHED。标明「演示数据」。**默认不替租户订阅**（与 `P4DemoSeedConfiguration` 一致）。
- **不做**：生产默认开启；假 KPI。
- **依赖**：K11、K6、**M10（已合）**
- **验收**：仅开关开启时插入；关闭零副作用。
- **PR 标题**：`K20: demo seed for simple approval product`

---

## 四、Flyway 严格合并顺序

以**全局版本号数值**为准（Flyway 扫描全序）：

```text
K1   (V005__platform_form_template.sql)
 → K14  (V552__product_application.sql)
 → [可选 K17a V553__process_todo_comment.sql]
 → K8   (V601__market_product_definition.sql)
```

规则：

1. 两人及以上时 **禁止**并行合入两个 Flyway PR。
2. 逻辑上产品定义（K8）先于申请业务（K15b），但 **K14 表可先合**；K15a/K15b 代码仍依赖 K12a（及建议 K12b）/K6。
3. K17 若需意见列，必须单独迁移 PR，插在 V552 与 V601 之间。
4. **K12a / K12b / K15a / K15b 本身不含 Flyway**（除非实现时被迫加旁表——若加，须另开迁移卡并插入本序，禁止塞进业务 PR）。

---

## 五、依赖关系图

```text
M10 (流程发布 API, 已合) ──┐
M11 (流程发布 UX, 已合) ──┼──► K9 / K10 / K15b / K20
M20 (权益投影, 已合) ──────► K12a
M22 (哈希链校验 API, 已合)  # 非本波阻塞；可外挂 M23

K1(mig) → K2(BE) → K3(FE)
       ↘ K4(BE) → K5(FE) → K7(FE)
                ↘ K6(BE) ↗
                     │
K14(mig)            K8(mig) → K9(BE) → K10(FE)
                     │          └→ K11(BE) → K12a(BE) → K12b(SEC)
                     │                          │            │
                     │                          └→ K13(FE)   │
                     │                          │            │
                     └──────────► K15a(BE) ─────┴────────────┘
                                     └→ K15b(BE) → K16(FE)
                                           ▲
K17(BE) → K18(FE)   # 可早并行；K18 展示申请结果需 K15b

K19(test) ← K2 + K12a + K12b + K15a + K15b
K20(seed) ← K6 + K11 + M10(已合)
```

---

## 六、建议第一批（当前 2 人）

| 批次 | 人 A | 人 B | 理由 |
|---|---|---|---|
| **Batch 0** | **K1 → K2** | **K17**（无迁移时） | A 开模板主链；B 补审批意见（M10/M11 已合，无需再优先卸依赖） |
| **Batch 0b** | K4 | K3（K2 合入后） | 校验与编辑 UX |
| **Batch 1** | K6 → K7 | **K14**（K1 已合后） | 模板发布；申请表迁移排队 |
| **Batch 2** | K8 → K9 → K10 | K5 / K18 | 产品定义（流程发布已可用）vs UX |
| **Batch 3** | K11 → **K12a → K12b** → K13 | **K15a → K15b** → K16 | 订阅+权限 vs 校验+开单 |
| **Batch 4** | K19 | K20 | 否定测试与演示种子 |

两人时 **不要**并行合入两个 Flyway PR。

---

## 七、端到端验收路径（演示）

**准备（仅 local/demo）**

1. 开启演示开关，加载 **K20** 种子（或手工：平台管理员创建模板 → 校验 → 发布；用 **M10/M11** 发布一级审批流程；K9–K11 绑定并上架）。
2. 租户 T1：至少发起人 A、审批人 B；T2 用于否定。
3. 产品钉住的 process 为 **已发布**定义（M10）。

**路径**

1. 平台管理员：表单模板编辑（K3）→ 校验（K5）→ 发布（K7）。
2. 平台管理员：产品定义绑定模板版本 + 流程版本 + 默认权限说明（K10）→ SKU 上架（K11）。
3. 租户 T1 管理员：目录「订阅开通」（K13）——无支付、无审核；**K12a** 钉版本开通；**K12b** 下发本租户默认权限；权益投影可读（M20）。
4. 成员 A：从「我的已开通」发起（K16）→ 选择审批人 B → 提交（**K15a 校验 → K15b 开单**）→ 状态「进行中」。
5. 审批人 B：待办同意或驳回并填意见（K18/K17）。
6. 发起人 A：「我的申请」见「通过」或「驳回」及意见。
7. 否定：T2 无法读 T1 申请 / 待办 / 开通 / 权限（K19）。

**演示数据**：仅 K20 / Demo 开关；界面与注释标明「演示数据」；生产配置不得默认打开。

---

## 八、本波不做 / 后置登记

| 项 | 处理 |
|---|---|
| **H5**（`/m/*`、`web/src/mobile/**`） | **整波不做** |
| 会签 / 或签 / 条件分支 / 转交加签 | 后置 |
| 拖拽式表单设计器 | 后置（本波列表 + 预览） |
| 支付 / 平台审核订阅 | 不做 |
| 订阅后模板版本自动升级迁移 | 不做 |
| 把 `business_meta.form` 台账表单改造成产品模板 | **不做**（新建平台模板） |
| S2 治理 / 备份恢复 / 开发者门户 | 冻结表后置 |
| M23 哈希链校验按钮 | 不阻塞 E2E；M22 API 已合，按钮可外挂；M21 权益页已合 |
| 发布中心真 Expand/Migrate/Switch/Contract | 后置 |

---

## 九、修订记录

- 2026-09-25：NexusOne 草稿 V0.1。对照当时 `main@8ce154aa`；主线为可配置审批产品 E2E；写明与现有台账表单、预置流程键、P4 安装链路的差异；依赖 M10/M11/M20；K0 占位。
- 2026-09-25：Eric 批准推送。拆分 **K12→K12a/K12b**、**K15→K15a/K15b**（不重编号后续卡）；业务卡合计 22（含安全 1）；同步 **M10 #358 / M11 #359 / M22 #357 已合**，M21/M23 当时仍未合；基线更新为 `main@7b14460d`；第一批派发不再要求「先做 M10」。
- 2026-09-25：推送后发现 **M21 #360** 已合入 main（只读商业权益页）；快照表与默认决策改为 M21 已合，仅 **M23** 仍未合；基线跟到本提交。
