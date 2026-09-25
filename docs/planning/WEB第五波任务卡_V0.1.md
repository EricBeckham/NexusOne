# WEB 第五波任务卡（K 波 · 可配置审批产品 E2E）V0.1

- 基线：`main@d403bd80`（`K20: seed a published simple-approval demo product (#388)`；K0a–K0d 已合；业务 K 卡仅剩 **K16、K18**；新增门禁修卡 **K0e**；上线前见 `docs/planning/WEB上线前任务卡_V0.1.md`）
- 依据：Eric 确认的第五波主线（表单模板 + 流程 + 默认权限打包成产品 → 租户自助订阅 → 一级审批跑通）；`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md` §25–§29；`docs/planning/快进P4P5_范围冻结表_V0.1.md`；`docs/planning/WEB第四波任务卡_V0.1.md`（M 波交付与未合登记）
- 范围：打通「平台配置最简单业务审批产品 → 租户订阅即用 → 发起 / 待办审批 / 查看结果」端到端闭环。审批产品**不写死成业务模块**，用平台能力配出。**本波明确不做 H5**（无 `/m/*`、无 `web/src/mobile/**`）
- 执行方：本地 Codex / Cursor 按卡领取；NexusOne 只负责拆分与审阅

## 默认决策

1. **一卡一产品 + 一种资源类型或一个薄片**；后端 / 前端分卡；**新表 / Flyway 单独成卡**；**密钥 / 权限开通等安全敏感单独成卡**；**仅测试**可单独成卡。
2. **主线 = 可配置审批产品 E2E**：平台表单模板版本 + P3 流程定义版本 + 默认权限 → 打成 P4 产品 → 租户管理员自助订阅（无审核、无支付）→ 运行时发起与一级审批。
3. **平台表单模板 ≠ 现有 P3 `business_meta.form`**：现有表单是**租户作用域**且绑定 `ledger_code` + `field_code`（M0-07）。本波新建**平台级**表单模板（字段名 / key / 类型 / 必填 / 下拉选项），供产品绑定；第一版字段类型：文本、多行文本、数字、金额、日期、单选下拉、成员选择、附件。
4. **第四波依赖**：流程定义发布复用 **M10（#358 已合）/ M11（#359 已合）**；权益投影复用 **M20（#356 已合）**；平台哈希链校验 HTTP 复用 **M22（#357 已合）**；权益页 **M21（#360 已合）**；审计中心校验按钮 **M23（#361 已合）**。**M1–M23 全部已合**（M23 #361 为最后一张）。**K1–K8、K14、K17 等 10 张 K 卡是在 G1/G2 门禁红着时合入的**：主线默认 `mvn test` 至今被 G1（QueryCompiler allowlist）/ G2（SurfaceGuard）打红，因此 **K0a–K0c 已合入**；完整 `mvn test` 仍因 **K15a** 的 `PinnedProductApplicationValidationService` 裸 JDBC 未进 G1 allowlist 而 G1 红（G2 绿）。现优先 **K0e**，再收尾 **K16 / K18**（K20 已合 #388）。K0d 可选，不阻塞 E2E。
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
- **Flyway 版本号（2026-09-25 Eric 定）**：新迁移一律取仓库当前**全局最高版本 +1**（现为 **V705** 起，严格递增），合入前再次确认没人占用更高号；**不按模块窗口**（V1xx / V5xx 等）取号；**不开启 `outOfOrder`**。
- 交付门禁：
  - UI：`npm test`、`npx vite build`、`npm run assert:h5`、`npm run assert:h5-every-page`、`git diff --check`
  - 后端：相关模块测试绿；**含跨租户不可读 / 不可写测试**
- 原有深链保持可开。

---

## K0 — M 波统一审查修复卡（2026-09-25）

- **来源**：M1–M23 合入后统一审查（worktree `D:\NexusOne-review-m` @ `4c6a42dd` / #361 tip）。审查报告仅对话交付，未入仓。
- **总体**：业务与安全验收**有条件通过**；前端门禁全绿；后端默认 `mvn test` 被 G1（QueryCompiler JDBC allowlist）与 G2（SurfaceGuard）打红（`-Dmaven.test.failure.ignore=true` 下 1037 测 / 5 失败 / 27 跳过）。
- **现状与优先级**：审查后 K1–K8、K14、K17 等 K 卡已在 G1/G2 门禁红着时合入（见 §二快照）。**K0a–K0c 已合入**；完整 `mvn test` 仍因 **K15a** 的 `PinnedProductApplicationValidationService` 裸 JDBC 未进 G1 allowlist 而 G1 红（G2 绿）。现优先 **K0e**，再收尾 **K16 / K18**（K20 已合 #388）。**K0d 可选**，不阻塞 E2E。
- **K0 合完后**：跑一次完整 `mvn test`。审查之后才合入的平台模板接口（**K2 / K4 / K6**，`FormTemplateController` `/api/v1/p1/platform/form-templates*`）是否带 SurfaceGuard **尚未核对**；若 G2 报红，**并入 K0c** 一起修。K 卡合入若新增 G1 违例（如新 JDBC 类未入 allowlist），同理并入 K0b。
- 2026-09-25 复核 `main@2af8c72b`：allowlist 的 bootstrap 仍仅 `RuntimeMetaController.java`（`nexus-crypto-core` 只在扫描根列表中，未入 allowlist）；两 Process 草稿/校验控制器仍无 SurfaceGuard；`OidcClientCredentialsTester.java:49` 仍有 `new String(secret, …)`。四张 K0 均**未开始**。
- **不做**：把路径塞进 `UNGUARDED_*` leftover 而不接 Guard（除非诚实登记 leftover 并说明）；静默关闸。

### 总览

| 编号 | 标题 | 产品 | 类型 | 依赖 | 可并行 | 预估 |
|---|---|---|---|---|---|---|
| **K0a** | G1：durable SecretPort JDBC 入 QueryCompiler allowlist + ArchUnit 例外 | 平台/安全 | **安全** | 无 | 同一人串行，其后 K0b | 1h |
| **K0b** | G1：bootstrap demo seed + 平台只读 JDBC 入 allowlist（或迁出 PRODUCT 扫描） | 平台 | 门禁 | K0a（同 Gate 冲突区） | 同一人串行，其后 K0c | 1h |
| **K0c** | G2：P3 流程草稿/校验/发布 API 接入 SurfaceGuard（G2 若报 K2/K4/K6 一并修） | P3 | **安全** | 建议 K0b 后（同一人串行） | 与 K0d 可并行 | 1–2h |
| **K0d** | M4 跟进：OidcClientCredentialsTester 去掉 secret→String 拷贝（**可选**） | P2 | **安全** | 无 | 是 | 1h |
| **K0e** | G1：K15a `PinnedProductApplicationValidationService` JDBC allowlist 或迁出 | 平台/安全 | **安全** | K0a–c、K15a 已合 | 其后 K16/K18 演示更稳 | 1h |

### K0a — G1：durable SecretPort JDBC allowlist + ArchUnit

- **类型**：安全 / 门禁　**分支**：`sec/k0a-crypto-jdbc-allowlist`　**前置**：无
- **目标**：将 `nexus-crypto-core` 的 `JdbcEncryptedSecretAdapter`（及装配路径 `LocalThenEnvSecretPort.durableSystem`）纳入 `QueryCompilerBypassAllowlist` 文档化 MODULE 前缀；为 ArchUnit `QueryCompilerBypassArchTest` 增加平台密文表 JDBC 的包例外。PR 说明：`platform.secret_ciphertext` 为平台级密文存储，非租户 AST 业务表。
- **区域**：`modules/isolation-kernel/.../QueryCompilerBypassAllowlist.java`；相关 ArchUnit；必要时仅文档注释。
- **不做**：改 SecretPort 业务语义；关闸；处理 bootstrap demo（归 K0b）。
- **依赖**：无
- **验收**：`QueryCompilerBypassGateTest.productMainPassesWithDocumentedAllowlist`、`G1QueryCompilerBypassTest.productMainHasNoQueryCompilerBypass`、`QueryCompilerBypassArchTest.applicationLayersMustNotUseJdbc` 在仅含本卡改动时，**不再因 crypto JDBC 失败**（bootstrap 遗留可仍红，由 K0b 清）。
- **阻塞**：主线默认 `mvn test`（与 K0b 一起）。
- **PR 标题**：`K0a: allowlist durable SecretPort JDBC for G1`

### K0b — G1：bootstrap demo / 平台只读 JDBC allowlist

- **类型**：门禁　**分支**：`fix/k0b-bootstrap-jdbc-allowlist`　**前置**：**K0a**（同 allowlist 文件冲突区串行）
- **目标**：将 bootstrap 中仍扫进 PRODUCT main 的 JDBC 纳入文档化 allowlist，或迁出扫描范围（demo 仅 profile）：`P1–P5DemoSeedConfiguration`、`PlatformAuditSearchService`、`PlatformServiceStatusService`（现状 allowlist 仍仅 `RuntimeMetaController.java`）。文档注明 demo-only / 平台只读。开工前在最新 main 重跑 G1，若 K 卡（如 K2/K6/K8/K14 的 JDBC 适配）带来新违例，按同一原则一并处理。
- **不做**：改 demo 业务数据；关闸。
- **依赖**：K0a
- **验收**：与 K0a 合入后，`QueryCompilerBypassGateTest` / `G1QueryCompilerBypassTest` / 相关 ArchUnit **全绿**。
- **阻塞**：主线默认 `mvn test`（与 K0a 一起）。
- **PR 标题**：`K0b: allowlist bootstrap demo and platform JDBC for G1`

### K0c — G2：Process 草稿/校验/发布 SurfaceGuard

- **类型**：安全　**分支**：`sec/k0c-process-surface-guard`　**前置**：建议 K0b 后（同一人串行，避门禁文件冲突）
- **目标**：为 `ProcessDefinitionDraftController`、`ProcessDefinitionValidationController`（及同控制器上的 publish/publication 面，路径含 `/api/v1/process/drafts`）接入合适的 Process/Catalog `SurfaceGuard`（或等价 GUARD_TOKEN + PermissionEngine 决策）。保持既有跨租户否定测试绿。若 G2 同时报审查后合入的平台模板接口（K2/K4/K6）未接 Guard，**并入本卡**一起接。
- **不做**：仅把路径塞进 `UNGUARDED_P3_LEFTOVER_PATHS`（除非诚实登记 leftover 并说明）；改草稿/校验/发布业务语义。
- **依赖**：无硬依赖；建议 K0b 后
- **验收**：`SurfaceGuardBypassGateTest.productMainPassesWithDocumentedAllowlist`、`G2SurfaceGuardBypassTest.productMainHasNoSurfaceGuardBypass` 绿；草稿/校验/发布跨租户否定仍绿。
- **阻塞**：主线默认 `mvn test`（G2）。K0a–K0c 合完后跑一次完整 `mvn test` 确认全绿。
- **PR 标题**：`K0c: guard process draft validate publish surfaces`

### K0d — M4 跟进：去掉 secret→String 拷贝

- **类型**：安全　**分支**：`sec/k0d-oidc-tester-no-secret-string`　**前置**：无
- **目标**：`OidcClientCredentialsTester` 中 `new String(secret, UTF_8)`（约 L49–57）改为对 `byte[]` 直接做 form-urlencode，或用可擦除缓冲，避免堆上不可擦 String 滞留。保持 peer IP 复核与私网/回环/链路本地/组播/CGNAT 拒绝、HTTPS-only。
- **不做**：改 SSRF 策略；FE。
- **依赖**：无
- **验收**：`OidcClientCredentialsTesterTest` 仍拒私网；公共 issuer 路径可用；无行为回归。
- **阻塞**：**可选**；不阻塞 E2E，谁空谁做。
- **PR 标题**：`K0d: avoid String copy of IdP client secret in tester`

---

## 一、分功能现状（已有 vs 缺失）

> 摸查基线：`origin/main@4c6a42dd`（M 波审查尖端）；合入状态同步到 `main@2af8c72b`（#373）。下列「已有」均给路径；**未在代码中证实的不写成已有**。


### K0e — G1：K15a 钉住校验 JDBC allowlist / 迁出

- **类型**：门禁 / 安全　**分支前缀**：`claude/k0e-*`　**前置**：K0a–K0c（已合）、K15a（已合）
- **背景**：K0a–K0c 合入后，K15a 引入的 `PinnedProductApplicationValidationService` 使用裸 JDBC / Spring JDBC，未进 G1 allowlist，导致 `QueryCompilerBypassGateTest` / `G1QueryCompilerBypassTest` / `QueryCompilerBypassArchTest` 仍红。G2 已绿。
- **目标**：小改动修好 G1——(1) 按 K0a/K0b 同一文档化 allowlist 正当列入并写清理由；或 (2) 去掉应用层 JDBC，改为经 QueryCompiler / 已有端口。**禁止关闸**；**禁止**把无关类塞进 allowlist。
- **不做**：改校验业务语义；动前端。
- **验收**：上述 3 个 G1 测试绿；相关模块或完整 `mvn test` 中不再出现该类 G1 失败。
- **PR 标题**：`K0e: allowlist or remove JDBC in pinned application validation`


### 0) 第四波依赖卡合入快照（M1–M23 全部已合）

| 卡 | 状态 | 对本波 |
|---|---|---|
| M5–M9（流程草稿表 / CRUD / 编辑 / 校验 API·UX） | **已合**（#347–#354） | 可做产品绑定前的流程草稿与校验 |
| **M10**（流程定义发布 API） | **已合**（#358，`ProcessDefinitionPublishService`；草稿 `POST .../drafts/{key}/publish`；`SequentialApprovalService` 可开已发布键） | 产品绑定流程版本 + 非预置键开单 **已可用** |
| **M11**（流程设计器发布 UX） | **已合**（#359，`ProcessViewerView.vue` 发布确认） | 设计器路径发布可用 |
| M16–M19（Credential） | **已合**（#348–#355） | 无硬依赖 |
| **M20**（商业权益只读投影） | **已合**（#356） | 订阅后权益投影可复用 |
| **M21**（商业权益页） | **已合**（#360，`CommercialEntitlementsView`；侧栏 `entitlements` 已去 later） | E2E **非阻塞**；可只读核对订阅权益 |
| **M22**（平台审计哈希链校验 API） | **已合**（#357，`AuditChainVerifyController` `/api/v1/audit/chain`） | E2E **非阻塞**；可并行外挂按钮 |
| **M23**（审计中心「校验哈希链」按钮） | **已合**（#361，平台审计中心校验动作；只展示 intact/checked/failure） | E2E **非阻塞** |

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

> 下表按模块窗口列出的是**历史取号现状**。自 2026-09-25 起不再按窗口取号：新迁移一律取全局最高版本 +1（现为 V705 起），见「所有任务通用约束」。

| 窗口（历史） | 现状（`*/db/migration/V*.sql`） |
|---|---|
| 平台 bootstrap | `V001`–`V006`（含 `V004__platform_secret_ciphertext.sql`；**K1** `V005__platform_form_template.sql`；#363 `V006__allow_invalid_form_template_drafts.sql`） |
| P1 | `V100`–`V102` |
| P2 | `V200`–`V202` |
| permission | `V300` |
| P3 | `V400`；表单 `V530`–`V531`；流程 `V550`–`V551`；**K14** `V552__product_application.sql` |
| event/audit/file/notify | `V500`–`V504`、`V510`–`V511`、`V520` |
| P4 | `V600`；**K8** `V601__market_product_definition.sql` |
| P5 | `V700`–`V704` |
| **命名** | `V{数字}__{snake_desc}.sql`；**全局版本号不重复** |
| **本波实际占用（历史，按旧窗口取号）** | `V005`（K1）、`V006`（#363 修复）、`V552`（K14）、`V601`（K8），均已合；**V553 未使用**（K17 未加迁移，见 §四）。全局最高仍为 P5 `V704`；**下一个新迁移从 `V705` 起** |

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
| **K0a** | G1：durable SecretPort JDBC allowlist + ArchUnit | 平台/安全 | **安全** | 无 | 其后 K0b | 1h |
| **K0b** | G1：bootstrap demo/平台 JDBC allowlist | 平台 | 门禁 | 建议 K0a | 与 K0c/K0d 并行 | 1h |
| **K0c** | G2：Process 草稿/校验/发布 SurfaceGuard | P3 | **安全** | 无 | 与 K0a/K0d 并行 | 1–2h |
| **K0d** | M4 跟进：去掉 secret→String | P2 | **安全** | 无 | 是 | 1h |
| K1 ✅ | 平台表单模板表迁移（**已合 #362**） | 平台 | 迁移 | 无 | Flyway 串行 | 1h |
| K2 ✅ | 平台表单模板草稿 CRUD API（**已合 #364**） | 平台 | 后端 | K1 | 其后 K3/K4 | 1–2h |
| K3 ✅ | 平台表单模板编辑页（列表+预览）（**已合 #370**） | 平台 | 前端 | K2 | 是 | 1–2h |
| K4 ✅ | 平台表单模板校验 API（**已合 #366**） | 平台 | 后端 | K1 | 可与 K2 后并行 | 1h |
| K5 ✅ | 平台表单模板校验 UX（**已合 #371**） | 平台 | 前端 | K4、K3 | 是 | 1h |
| K6 ✅ | 平台表单模板发布 API（**已合 #367**） | 平台 | 后端 | K4 | 其后 K7 | 1–2h |
| K7 ✅ | 平台表单模板发布 UX（**已合 #373**） | 平台 | 前端 | K6、K5 | 是 | 1h |
| K8 ✅ | 市场产品定义表迁移（**已合 #368**） | P4 | 迁移 | 无 | Flyway 串行（见 §四） | 1h |
| K9 | 产品定义绑定 API | P4 | 后端 | K8 ✅、K6 ✅；**M10（已合）** | 其后 K10 | 1–2h |
| K10 | 产品定义绑定页 | P4 | 前端 | K9；**M11（已合）** | 是 | 1–2h |
| K11 | 产品定义挂接 SKU / 目录 API | P4 | 后端 | K9 | 其后 K12a | 1–2h |
| K12a | 租户自助订阅并钉版本开通 API | P4 | 后端 | K11；**M20（已合）** | 其后 K12b/K13 | 1–2h |
| K12b | 开通默认权限包下发（安全） | P4 | **安全** | K12a | 其后发起链 | 1–2h |
| K13 | 租户目录「订阅开通」UX | P4 | 前端 | K12a（权限就绪建议等 K12b） | 是 | 1–2h |
| K14 ✅ | 产品申请单表迁移（**已合 #369**） | P3 | 迁移 | 无 | Flyway 串行（见 §四） | 1h |
| K15a | 按钉住模板校验 values_json（可测校验器） | P3 | 后端 | K6、K12a | 可与 K14 后并行 | 1–2h |
| K15b | 创建申请并按钉住流程启动一级审批 API | P3 | 后端 | K15a、K14、K12a（需默认权限时 **K12b**）；**M10（已合）** | 其后 K16 | 1–2h |
| K16 | 已开通产品发起页（模板渲染） | P3/P4 | 前端 | K15b、K13 | 是 | 1–2h |
| K17 ✅ | 审批同意/驳回意见 API（**已合 #365**） | P3 | 后端 | 无（现有 approve/reject） | **可最先并行** | 1–2h |
| K18 | 我的待办意见 + 我的申请结果 UX | P3 | 前端 | K17 ✅；结果依赖 K15b | 是 | 1–2h |
| K19 | 订阅/模板/申请跨租户否定测试 | 平台 | 仅测试 | K2、K12a、K12b、K15a、K15b | 建议其后 | 1–2h |
| K20 | 演示种子：简单审批产品包 | 平台 | 种子 | K11、K6；**M10（已合）** | 仅 local/demo | 1–2h |

**类型合计**：K0 修复 4（安全 3 + 门禁 1）；业务卡迁移 3 · 后端 9 · 前端 7 · **安全 1** · 仅测试 1 · 种子 1 → **22 张业务卡 + 4 张 K0**。

### K 卡合入快照（`main@2af8c72b`）

| 卡 / PR | 状态 | 提交（main） |
|---|---|---|
| **K1** #362 | **已合** | `68cf9ad4` `K1: add platform form template tables`（V005） |
| （修复）#363 | **已合** | `b7876643` `fix: allow invalid form template drafts for validation`（V006） |
| **K2** #364 | **已合** | `f0900251` `K2: platform form template draft CRUD` |
| **K17** #365 | **已合** | `04fd8dac` `K17: accept an optional comment on approve and reject`（无迁移） |
| **K4** #366 | **已合** | `2ec2514a` `K4: validate platform form templates` |
| **K6** #367 | **已合** | `ee0d5fa0` `K6: publish platform form template versions` |
| **K8** #368 | **已合** | `a93d5b5c` `K8: add market product definition tables`（V601） |
| **K14** #369 | **已合** | `60cd9101` `K14: add the product application table`（V552） |
| **K3** #370 | **已合** | `3ba990ca` `K3: edit form templates as a field list with a preview` |
| **K5** #371 | **已合** | `da66af98` `K5: show form template validation on the field list` |
| （修复）#372 | **已合** | `929d5305` `fix: keep P3 tests independent of optional P1 wiring` |
| **K7** #373 | **已合** | `2af8c72b` `K7: confirm form template publish and show the version` |

**未合**：业务卡 **K16、K18**；门禁修卡 **K0e**（K20 已合 #388；K0a–K0d 已合）。上线前 L 卡见 `WEB上线前任务卡_V0.1.md`。

---

## 三、任务卡正文

### K1 — 平台表单模板表迁移

- **状态**：**已合**（#362，`68cf9ad4`）
- **类型**：迁移　**分支**：`mig/k1-platform-form-template`　**前置**：无
- **目标**：`V005__platform_form_template.sql`：`platform.form_template` / `platform.form_template_field` / `platform.form_template_version`（模板头、草稿字段、不可变版本快照）。字段类型 CHECK：`TEXT|TEXTAREA|NUMBER|MONEY|DATE|SELECT|PERSON|ATTACHMENT`。注册 `platform.table_registry`（scope=platform，tenant_required=false）。
- **不做**：改 `business_meta.form*`；发布 / 校验业务逻辑；前端。
- **依赖**：无
- **验收**：migrate 绿；契约测试绿；与 `V530` 并存无冲突。
- **PR 标题**：`K1: add platform form template tables`

### K2 — 平台表单模板草稿 CRUD API

- **状态**：**已合**（#364，`f0900251`）
- **类型**：后端　**分支**：`feat/k2-form-template-draft-api`　**前置**：K1
- **目标**：平台管理员创建 / 列表 / 获取 / 更新草稿（字段列表按序保存）。租户上下文 → FORBIDDEN/404。写审计。
- **不做**：validate / publish；租户直接改平台草稿。
- **依赖**：K1
- **验收**：CRUD 绿；非平台否定；零前端文件。
- **PR 标题**：`K2: platform form template draft CRUD`

### K3 — 平台表单模板编辑页（列表 + 右侧预览）

- **状态**：**已合**（#370，`3ba990ca`）
- **类型**：前端　**分支**：`ui/k3-form-template-editor`　**前置**：K2
- **目标**：平台壳入口「表单模板」：字段列表编辑（名称、key、类型、必填；SELECT 编辑选项）；右侧只读预览。**不做拖拽设计器**。
- **不做**：validate / publish 可用态（可灰置，归 K5/K7）；H5。
- **依赖**：K2
- **验收**：UI 门禁；空「暂无字段」。
- **PR 标题**：`K3: form template editor list and preview`

### K4 — 平台表单模板校验 API

- **状态**：**已合**（#366，`2ec2514a`）
- **类型**：后端　**分支**：`feat/k4-form-template-validate`　**前置**：K1
- **目标**：`POST .../form-templates/{id}/validate`：≥1 字段；key 唯一合法；SELECT 必有选项；失败字段级错误；成功 → `VALIDATED`。写审计。
- **不做**：publish；改台账表单校验。
- **依赖**：K1
- **验收**：合法 / 非法用例；跨职责否定。
- **PR 标题**：`K4: validate platform form templates`

### K5 — 平台表单模板校验 UX

- **状态**：**已合**（#371，`da66af98`）
- **类型**：前端　**分支**：`ui/k5-form-template-validate`　**前置**：K4、K3
- **目标**：校验按钮 + 错误到字段；成功态可见。
- **不做**：publish。
- **依赖**：K4、K3
- **验收**：UI 门禁。
- **PR 标题**：`K5: form template validate UX`

### K6 — 平台表单模板发布 API

- **状态**：**已合**（#367，`ee0d5fa0`）
- **类型**：后端　**分支**：`feat/k6-form-template-publish`　**前置**：K4
- **目标**：仅 `VALIDATED` 可发布；生成不可变 `form_template_version` 快照并切换 running；再改草稿须重校验；已发布版本不可改。写审计。
- **不做**：自动推送到已订阅租户（第一版不升级跟随）。
- **依赖**：K4
- **验收**：发布后版本只读；失败不半发布。
- **PR 标题**：`K6: publish platform form template versions`

### K7 — 平台表单模板发布 UX

- **状态**：**已合**（#373，`2af8c72b`）
- **类型**：前端　**分支**：`ui/k7-form-template-publish`　**前置**：K6、K5
- **目标**：发布确认 + 版本号 + 失败原文。
- **不做**：版本 diff 大件。
- **依赖**：K6、K5
- **验收**：UI 门禁。
- **PR 标题**：`K7: form template publish UX`

### K8 — 市场产品定义表迁移

- **状态**：**已合**（#368，`a93d5b5c`）
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

- **状态**：**已合**（#369，`60cd9101`）
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

- **状态**：**已合**（#365，`04fd8dac`）
- **类型**：后端　**分支**：`feat/k17-approval-comment`　**前置**：无
- **目标**：为 `POST /api/v1/process/tasks/{todoId}/approve|reject` 增加可选 `comment`；持久化；写审计；跨租户否定。
- **实际实现（#365）**：意见存为 Flowable 任务评论（`nexus-p3-studio/.../flow/ApprovalCommentStore.java`），**未加 Flyway**；原设想的 V553 / **K17a 迁移卡不再需要**。
- **不做**：转交 / 加签；改状态机。
- **依赖**：无
- **验收**：有 / 无意见均可；跨租户不可批。
- **PR 标题**：`K17: approval approve reject with comment`

### K18 — 我的待办意见 + 我的申请结果 UX

- **类型**：前端　**分支**：`ui/k18-todo-and-my-requests`　**前置**：K17（✅ 已合 #365）；申请列表字段依赖 **K15b**
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
K1   (V005__platform_form_template.sql)          ✅ #362
 → #363 (V006__allow_invalid_form_template_drafts.sql) ✅ 修复
 → K14  (V552__product_application.sql)          ✅ #369
 → K8   (V601__market_product_definition.sql)    ✅ #368
```

现状（`main@2af8c72b`）：**本波计划内迁移已全部合入**；剩余卡无计划内迁移。上面四个号是按旧的模块窗口规则取的，**仅作历史说明**；今后新迁移按全局递增取号（V705 起，见下）。

**旧库提醒**：本波的 `V005`、`V006`、`V552`、`V601` 都低于 `V704`。在默认 `outOfOrder=false` + `validate-on-migrate: true` 下，**之前已迁移过（已到 V704）的本地 / 演示旧库**启动时**很可能**校验失败（这些号被视为比已应用最高版本小的未应用迁移）。处理方式：**删库重建**。测试用的新库从空库按序迁移，不受影响。（此结论按 Flyway 默认行为推断，**未在旧库实测**。）

**K17a / V553 结论**：K17（#365）用 Flowable 任务评论存意见（`ApprovalCommentStore`），没有加迁移，**K17a 不再需要**。

**如果将来仍要加 V553（或任何低于已应用最高版本的号）**：仓库 Flyway 配置未开启乱序——`nexus-bootstrap/src/main/resources/application.yaml` 的 `spring.flyway` 只设了 `enabled` / `locations` / `schemas: platform` / `table: flyway_schema_history` / `validate-on-migrate: true` / `create-schemas`；`IdentityConfiguration`（schema `iam`）自建 `Flyway.configure()` 也未设；全仓无 `outOfOrder` / `out-of-order` 配置，即取默认 **`outOfOrder=false`**。此时在已迁到 V601（全局已到 V704）的库上新增 V553，Flyway 会把它当作「比已应用最高版本小的未应用迁移」，`validate` 报错 / 被拒，不会执行。所以：

1. **取号规则（Eric 2026-09-25 定）**：新迁移一律取仓库当前全局最高版本 +1（现为 **V705** 起，严格递增），合入前再次确认没人占用更高号；**不再按模块窗口**（V1xx / V5xx 等）取号。
2. **不开启 `outOfOrder`**；不插入低号。

规则：

1. 两人及以上时 **禁止**并行合入两个 Flyway PR。
2. **K9 / K11 / K12a / K12b / K15a / K15b 本身不含 Flyway**（除非实现时被迫加旁表——若加，须另开迁移卡，版本号按上文全局递增规则取（V705 起），禁止塞进业务 PR）。

---

## 五、依赖关系图

```text
M10 (流程发布 API, 已合) ──┐
M11 (流程发布 UX, 已合) ──┼──► K9 / K10 / K15b / K20
M20 (权益投影, 已合) ──────► K12a
M22 (哈希链校验 API, 已合) / M23 (校验按钮, 已合)  # 非本波阻塞
K0a → K0b → K0c   # G1/G2 门禁；现在最优先，一人串行（K 卡已在门禁红时合入）
K0d                # M4 String 拷贝；可选
# ✅ = 已合（main@2af8c72b）

K1✅(mig) → K2✅(BE) → K3✅(FE)
       ↘ K4✅(BE) → K5✅(FE) → K7✅(FE)
                ↘ K6✅(BE) ↗
                     │
K14✅(mig)          K8✅(mig) → K9(BE) → K10(FE)
                     │          └→ K11(BE) → K12a(BE) → K12b(SEC)
                     │                          │            │
                     │                          └→ K13(FE)   │
                     │                          │            │
                     └──────────► K15a(BE) ─────┴────────────┘
                                     └→ K15b(BE) → K16(FE)
                                           ▲
K17✅(BE) → K18(FE)   # K18 展示申请结果需 K15b

K19(test) ← K2 + K12a + K12b + K15a + K15b
K20(seed) ← K6 + K11 + M10(已合)
```

---

## 六、下一批（2026-09-25 · 三人并行 · Eric-Desktop）

> tip：`main@d403bd80`。**K20 已合 (#388)**。本机队列只剩 **K0e →（门禁）** 与前端 **K16 / K18**；上线前 L 卡见 `docs/planning/WEB上线前任务卡_V0.1.md`。

### 本机路径与分支前缀（强制）

- 仓库正迁：`D:\\NexusOne` → `D:\\Projects\\NexusOne`（两者谁存在用谁；主克隆**只允许 fetch**，禁止在主克隆上 checkout 干活）。
- 每人独立 worktree，禁止进入别人的目录 / 前缀分支：

| 人 | 分支前缀 | worktree（优先 Projects） |
|---|---|---|
| **Cursor** | `cursor/` | `D:\\Projects\\NexusOne-cursor` 或 `D:\\NexusOne-cursor` |
| **Codex** | `codex/` | `D:\\Projects\\NexusOne-codex` 或 `D:\\NexusOne-codex` |
| **Claude** | `claude/` | `D:\\Projects\\NexusOne-claude` 或 `D:\\NexusOne-claude` |
| **NexusOne**（协调，若动手） | `nexusone/` | `D:\\Projects\\NexusOne-nexusone` 或 `D:\\NexusOne-nexusone` |

- **流程**：一卡一 PR；PR 标题以卡号开头；做完 → commit → push → **squash 合并** → 再开下一张。

### 本波剩余卡分工

| 人 | Phase 1（第五波收尾） | Phase 2（上线前，见 L 文档） |
|---|---|---|
| **Cursor** | **K16**（已开通产品发起页） | L5 视觉差距 / 小修；L4 手工冒烟记账 |
| **Codex** | **K18**（待办意见 + 我的申请） | L7 演示 runbook；L13 #270；L15 分支清理；L6 demo 隔离 |
| **Claude** | **K0e**（先做，修 G1）→（K20 已合，跳过） | L1 门禁重跑报告；L8 N 波安全复审；L10–L12 密钥清单/备份演练/profile 硬化 |

**理由**：两张前端卡可并行；K0e 先于演示向验收，避免「功能齐但门禁红」。

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
| M21 / M23（权益页 / 哈希链校验按钮） | **已合**（#360 / #361）；不阻塞 E2E |
| 发布中心真 Expand/Migrate/Switch/Contract | 后置 |

---

## 九、修订记录
- 2026-09-25：同步 tip `main@d403bd80`（含 K20 #388）；标注 K0a–K0d 已合；新增 **K0e**；未合仅 K16/K18；改 §六为 Cursor/Codex/Claude 三人分工 + 路径/前缀；上线前 L 卡见 `WEB上线前任务卡_V0.1.md`。

- 2026-09-25：NexusOne 草稿 V0.1。对照当时 `main@8ce154aa`；主线为可配置审批产品 E2E；写明与现有台账表单、预置流程键、P4 安装链路的差异；依赖 M10/M11/M20；K0 占位。
- 2026-09-25：Eric 批准推送。拆分 **K12→K12a/K12b**、**K15→K15a/K15b**（不重编号后续卡）；业务卡合计 22（含安全 1）；同步 **M10 #358 / M11 #359 / M22 #357 已合**，M21/M23 当时仍未合；基线更新为 `main@7b14460d`；第一批派发不再要求「先做 M10」。
- 2026-09-25：推送后发现 **M21 #360** 已合入 main（只读商业权益页）；快照表与默认决策改为 M21 已合，仅 **M23** 仍未合；基线跟到本提交。
- 2026-09-25：第四波统一审查有条件通过（后端 1037 测 5 失败 27 跳过，均在 G1 allowlist / G2 SurfaceGuard；前端全过）后补 K0a–K0d；同步 #361–#373 合入现状（M23 #361；K1/K2/K3/K4/K5/K6/K7/K8/K14/K17 已合；#363、#372 修复）；K17 未加迁移，K17a/V553 取消；下一批改为 K0 优先（人 A：K9→K11→K12a→K12b→K15a；人 B：K0a→K0b→K0c→K10→K13）。基线 `main@2af8c72b`。
- 2026-09-25：Eric 定迁移编号规则为全局递增（V705 起），不开 outOfOrder。
