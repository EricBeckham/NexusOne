# WEB 平台配置台视觉任务卡（R 波扩）V0.1

- 基线：`main`（Codex R1–R5 已合：模板列表/编辑壳、工作室与流程只读、产品定义绑定、平台审计）
- 依据：Eric「扩 R」；Cursor/Claude/Codex 均空；每人 ≥5 张；**不做 H5**；自由画布/分支并行仍 **Z**
- 规范：以仓库 `docs/design` 下 WEB 规范现名为准
- 目标：把平台侧**尚未扫到的配置/运营页**拉到与 O/Q/R1–R5 同一观感
- 执行方：Cursor / Claude / Codex 三人并行；NexusOne 协调
- 已完成勿重复：R1 `FormTemplateListView`；R2 `FormTemplateEditorView` 壳；R3 `FormStudioView`/`ProcessViewerView`；R4 `ProductDefinitionBindingView`；R5 `PlatformAuditView`

## 默认决策

1. **一卡一 PR**；标题以 `R*` 开头（本扩编继续编号 **R6 起**）；做完 squash 合并。
2. 主克隆 `D:\Projects\NexusOne` **只 fetch**；worktree 见文末。
3. **明确不做**：H5；支付；CI 复活；真 Keycloak 宣称；自由画布；流转分支/并行（Z）；大改业务 API 语义。
4. **复用优先**：页眉/空态/Skeleton/状态标签/表密度用已有 O/Q/R 共享件，禁止另起视觉体系。
5. **切片**：每卡 1–2 小时；单页卡不偷偷改全局。
6. **验收**：本机打开对应页目视 + 截图进 PR；不宣称外网终态。

## 扩编总览（R6–R20）

| 卡 | 内容 | 负责人 | 前置 | 估时 |
|---|---|---|---|---|
| **R6** | `TenantListView`：表密度、空态、Skeleton、页眉槽 | Cursor | 无 | 1–2h |
| **R7** | `TenantDetailView`（及同壳 `TenantHomeView` 若同路径）：页眉/分区标题/次要操作 secondary | Cursor | 建议 R6 后 | 1–2h |
| **R8** | `PlatformServicesView`：列表/卡片密度、状态标签、空态 | Cursor | 无 | 1–2h |
| **R9** | `PublishCenterView`：列表密度、单一 Primary、空态/加载 | Cursor | 无 | 1–2h |
| **R10** | R 波扩打勾记账（docs-only）：勾选 R6–R20 与截图路径 | Cursor | 大部合入后 | 1h |
| **R11** | `StudioHomeView`：入口卡密度、标题层级、去掉开发味堆砌 | Claude | 无 | 1–2h |
| **R12** | `FormListView`（studio）：表密度、空态、Skeleton、页眉 | Claude | 无 | 1–2h |
| **R13** | `LedgerListView`（+ 列表进详情入口观感，不改账本语义） | Claude | 无 | 1–2h |
| **R14** | `ExportWizardView` 壳：步骤条/底栏单一 Primary、字段间距 | Claude | 无 | 1–2h |
| **R15** | `ReportView` 或 `AuditTraceView`（择更「配置台」的一页；PR 写明）：空态+密度+页眉 | Claude | 无 | 1–2h |
| **R16** | `ConnectHomeView`：入口与分区对齐 StudioHome 标准 | Codex | 无 | 1–2h |
| **R17** | `ApiCatalogView`：Filter/表密度/空态（复用 FilterBar 方向） | Codex | 无 | 1–2h |
| **R18** | `ClientsView`：表密度、状态标签、空态 | Codex | 无 | 1–2h |
| **R19** | `CredentialsView`：表密度、危险操作 secondary/danger 分层、空态 | Codex | 无 | 1–2h |
| **R20** | `WebhooksView` 或 `CallLogsView`（择一；PR 写明）：列表密度+空态+页眉 | Codex | 无 | 1–2h |

## 卡片要点

### Cursor R6–R10
- **R6** 租户列表对齐 Q 表密度与空态。
- **R7** 租户详情页眉与分区；一个操作区一个 Primary。
- **R8** 平台服务页状态与空态。
- **R9** 发布中心列表与主操作。
- **R10** docs-only 打勾 + 截图路径。

### Claude R11–R15
- **R11** 工作室首页入口卡。
- **R12** 表单列表。
- **R13** 台账列表（不改账本规则）。
- **R14** 导出向导壳（不做新导出能力）。
- **R15** 报表或审计追踪页择一收口。

### Codex R16–R20
- **R16–R20** Connect 域首页与目录/客户端/凭证/Webhook 或调用日志；不改连接器协议与密钥存储语义。

## 三人队列

| 人 | 顺序 |
|---|---|
| **Cursor** | **R6 → R7 → R8 → R9 →（后）R10** |
| **Claude** | **R11 → R12 → R13 → R14 → R15** |
| **Codex** | **R16 → R17 → R18 → R19 → R20** |

扩 R 收尾后开 **S**（Toast/结果/残留 Banner/文案）；**Z** 最后。

## 本机路径

| 人 | 前缀 | worktree |
|---|---|---|
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| Codex | `codex/` | `D:\Projects\NexusOne-codex` |
| NexusOne | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

## 修订记录

- 2026-09-26：R 波扩编。Eric：扩 R；三人各 ≥5 卡；接 Codex R1–R5 之后编号 R6–R20。

### 本机执行纪律（Eric-Desktop · 强制）

1. 所有文件只落本 worktree；禁止写桌面/`Downloads`/主克隆/他人树。
2. 自己跑终端/构建/测试/git；禁止让 Eric 反复点 Run。
3. 交付物是已推送 PR / 已合 main，不是待点击命令。