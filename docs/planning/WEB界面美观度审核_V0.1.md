# WEB 界面美观度审核 V0.1

- **基线**：`origin/main` @ `2eb9b971`（2026-09-26 22:42 CST）— 含 PC1–PC5、PC6；标题 PC5 form-template entry copy
- **规范**：`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md`（§Token / Header / Card / Table / Form / Designer / Empty·Error·Toast）
- **对照波次**：O（token/壳）、Q（租户主路径）、R（平台配置台视觉）、S（反馈壳 / 去开发味）
- **方法**：代码 + 规范对照审计（**本机未起 Vite/后端做像素截图**——无可用 demo 登录态与完整本地栈；结论标「代码审计」；截图目录预留见文末）
- **评分口径**：
  - **合格**：页眉/Token/空错态大体对齐 O/Q/R/S；无明显死控或开发味主文案
  - **半合格**：壳层在，但密度/文案/假空态/未接共享表或反馈有残留
  - **不合格**：缺反馈壳、硬编码空面板伪装数据、或开发味/调试信息面向用户

## 1. 总览结论

平台 + 租户主路径在 O/Q/R/S 之后**观感底座已立**（`NxPageHeader` / `NxEmpty` / `NxErrorState` / `NxSkeleton` / `NxStatusTag` / token CSS 普遍接入）。下一波美观债集中在：

1. **首页假空面板**（P1/P2 待办/活动/健康硬 `items=[]`）
2. **开发味残留**（导出向导 JSON decision、表单提交 `JSON.stringify` / `trace_id` 文案、Dump/审计页技术词）
3. **共享表未落地到 main**（`NxDataTable` 在 open PR #585，main 列表仍手搓 `<table class="nx-table">`）
4. **配置向导文案滞后**（页眉仍写「就绪态未接读口」，PC6 已合、PC7 FE 接线未合）
5. **设计器体验**（流程/表单编辑器功能可用，但距规范 §18 Designer 三栏密度与属性分区仍有差距——属 CF 加深，不在本审核「薄抛光」范围外扩成重做）

**不做宣称**：外网商用视觉终态、真 Keycloak、H5、完整视觉体系重设计。

## 2. 路由/页面评分表

| 路由/页面 | 评分 | 主要问题 | 建议薄修卡 |
|---|---|---|---|
| `/` 产品选择 | 合格 | 结构简单；非业务主路径 | — |
| `/p1` 星域首页 | 半合格 | `NxTodoList` / Timeline / Health 硬编码 `items=[]`，像坏了的模块 | UI1 |
| `/p1/tenants` 租户目录 | 合格 | R6 已收；FilterBar/Skeleton/空态齐 | 合 #585 后迁 NxDataTable |
| `/p1/tenants/:id` 租户详情 | 合格 | R7 页眉/Primary | — |
| `/p1/form-templates` 表单模板列表 | 合格 | PC5 文案/空态；链向导 | — |
| `/p1/form-templates/:id` 模板编辑器 | 半合格 | 有 Palette/属性；发布反馈有 `NxResult`；字段区密度与 §18 属性 Tab 分区仍粗 | CF 轴，不单开美观卡 |
| `/p1/services` 平台服务态 | 合格 | R8 | — |
| `/p1/audit` 平台审计 | 半合格 | 技术筛选项偏多；可接受平台态 | UI8（文案薄） |
| `/p2` 星权首页 | 半合格 | 同 P1：空 Todo/Timeline/Health | UI1 |
| `/p2/persons` 用户目录 | 合格 | 空/错态齐 | 合 #585 后迁表 |
| `/p2/permissions` 权限 | 半合格 | 能力完整；矩阵密度与说明文案仍偏工程 | UI9 |
| `/p3` 星构首页 | 合格 | R11；快捷入口清晰 | — |
| `/p3/forms` 表单列表 | 合格 | R12 | — |
| `/p3/forms/:id` 填报/发起 | 半合格 | 成功态仍 `JSON.stringify` / `record · todo · trace` 开发味 | UI2 |
| `/p3/workflow` 流程设计器 | 半合格 | 功能深（1265 行）；fallback 硬色 `#238636`；无完整 Palette/Property 三栏 | CF 轴；硬色 → UI3 |
| `/p3/publish-center` 发布中心 | 合格 | R9 | — |
| `/p3/todos` 待办 | 合格 | Q/S 空态；FilterBar | — |
| `/p3/ledgers` 台账 | 合格 | R13 | — |
| `/p3/reports` 报表 | 半合格 | 有 NxEmpty；仍见 dump/JSON 痕迹 | UI4 |
| `/p3/exports/wizard` 导出向导 | 不合格 | `NxSourceBanner` 技术句；`JSON.stringify(exportDecision)` 在 `devNotes` 外也可能露；缺 `NxEmpty`/`NxErrorState` 统一壳 | UI4 |
| `/p3/dumps` / `/p3/audit` | 半合格/偏不合格 | 标题含 dump/哈希链；面向平台可留，租户演示应藏或改名 | UI4 |
| `/p4` 星市首页 | 合格 | 壳齐；#585 拟迁表 | — |
| `/p4/market` 应用目录 | 合格 | Q7 密度；占位图标诚实 | — |
| `/p4/packages|skus|subscriptions|installations` | 半合格 | 空态诚实；包详情「暂未开放查看」死说明；列表未统一 NxDataTable | UI5、UI6 |
| `/p4/product-definitions` | 合格 | journey 清晰；链向导 | — |
| `/p4/product-config-wizard` | 半合格 | PC1–5 壳+空态好；页眉文案仍「未接读口」；PC7 未接线真就绪态 | UI7（文案）+ 等 PC7 |
| `/p4/installations/:id/start` 发起 | 合格 | 有 `NxResult`；Q9 | — |
| `/p4/entitlements` | 合格 | XB 投影已合；#585 拟迁表 | — |
| `/p4/operations` | 合格 | XB 诚实空/计数；禁假 KPI | — |
| `/p5` 及子页 | 合格 | R16–R20 | — |

## 3. Top 10 高影响抛光卡（各 1–2h）

| # | 卡 | 范围 | FE/BE | 估时 | 说明 |
|---|---|---|---|---|---|
| **UI1** | 首页假空面板诚实化 | `TenantHomeView` / `IamHomeView`：空 Todo/Timeline/Health 改 `NxEmpty` 原因+下一步，或藏面板直至有数据 | FE | 1–2h | 观感杀伤力最大 |
| **UI2** | 表单提交成功去开发味 | `FormStudioView`：去掉面向用户的 `JSON.stringify`；成功用 `NxResult`/Toast；trace 仅 `devNotes` | FE | 1–2h | S 残留 |
| **UI3** | 流程设计器硬色收口 | `ProcessViewerView`：`#238636` / `#fff` 等改 token；禁用态说明保持 | FE | ~1h | O token 债 |
| **UI4** | 导出/报表/dump 演示面去开发味 | `ExportWizardView` 接 `NxErrorState`；默认关 Banner；决策 JSON 仅 DEV；Dump 导航文案「物理导出」 | FE | 1–2h | 可与 S 遗留合并 |
| **UI5** | 星市管理列表迁 `NxDataTable` | `P4ManagementView` 四段列表（依赖 #585 合入） | FE | 1–2h | 密度/空错统一 |
| **UI6** | 包详情死说明改诚实空 | 「暂未开放查看」→ `NxEmpty` 原因+无 CTA 或链文档；禁假按钮 | FE | ~1h | 死控感 |
| **UI7** | 配置向导页眉文案同步 | 去掉「未接读口」；PC7 合入后改为「就绪态来自产品定义读口」 | FE | ~0.5–1h | 与 PC7 同 PR 亦可 |
| **UI8** | 平台审计筛选项人话 | 标签 Trace ID →「追踪编号」等；不改 API | FE | ~1h | 薄文案 |
| **UI9** | 权限矩阵说明条 | `PermissionView` 顶栏一句业务说明；减少 Code 恐吓感 | FE | 1–2h | 半合格→合格 |
| **UI10** | 主路径抛光截图记账 | docs-only：`docs/planning/screenshots/ui-audit/` + 本表复评 6 页 | Docs | 1h | 需本地 demo 登录后补 |

**建议队列**：Cursor UI1–UI5；Claude UI6–UI10（或 NexusOne 自跑 UI7+UI10）。**不派 Codex**（配额空时）。

## 4. 明确不在范围

- **H5** / `web/src/mobile/**`
- 全站视觉体系重设计、新 Design Token 体系、换组件库
- 完整表单/流程/打印设计器重做（→ **CF 波**）
- 产品配置导入导出实现（→ **IM 波**）
- 真支付、租户自助注册、共享表实现本身（#585）、PC 向导样例实现
- 为审核强行起 Keycloak / 外网宣称

## 5. 截图与复验

| 项 | 状态 |
|---|---|
| 本 PR 截图 | **无**（代码审计；未跑 `npm run dev` + 登录） |
| 预留目录 | `docs/planning/screenshots/ui-audit/`（UI10 补） |
| 复验建议页 | `/p1` `/p2` `/p3/forms/:id` `/p3/exports/wizard` `/p4/installations` `/p4/product-config-wizard` |

## 6. 修订记录

- 2026-09-26：V0.1 初稿。基线 `2eb9b971`；代码+规范审计；无浏览器截图。
