# WEB 反馈与去开发味任务卡（S 波）V0.1

- 基线：`main`（R6–R20 已合：#503–#517；含平台配置台扩编与 R10 打勾）
- 依据：O/Q/R 路线图「R 收尾后开 S」；Eric「开」+「三个人」；每人不低于 5 张；**不做 H5**；自由画布与分支/并行仍后置 **Z**
- 规范：仓库 `docs/design` 现名 WEB 规范（§Toast / Message / Notification、结果反馈、空态文案）；对照 O9 `devNotes` / `NxSourceBanner`
- 目标：演示路径上的 **Toast/消息、提交后结果壳、残留开发 Banner、开发味文案** 收到共享约定；仍非公网商用终态
- 执行方：Cursor / Claude / Codex 三人并行；NexusOne 协调
- 现状速记（扫仓，供卡目标落地）：
  - **尚无**共享 `Toast` / `ElMessage` / `NxMessage`；桌面成功/失败多依赖 `alert()`（如 `GlobalHeader`）、页内 `notice` / `NxRequestAlert` / 行内状态文案
  - `NxSourceBanner` 已接 `devNotesEnabled`（O9），但仍嵌在多页：studio 台账/表单/报表/导出/dump/审计/通知、org MFA、`PermissionView` 等
  - 租户主路径空态已多用 `NxEmpty`；`P4ManagementView` 等仍有「当前读模型未提供」类开发味说明
  - 平台侧：`FormTemplateEditorView` 保存/发布用 `NxRequestAlert` + 确认框；`PublishCenterView` / `ProcessViewerView` 测试刻意避开「发布成功」噪声文案
  - **不做 H5**：`web/src/mobile/**` 的 `alert` 不在本波范围

## 默认决策

1. **一卡一 PR**；标题以 `S*` 开头；做完 **squash** 合并。
2. 主克隆 `D:\Projects\NexusOne` **只 fetch**；worktree 见文末。
3. **明确不做**：H5；支付；CI 复活；真 Keycloak 宣称；自由画布；流转分支/并行（**Z**）；大改业务 API 语义；新后端能力。
4. **复用优先**：Toast/结果壳/空态接已有 O/Q/R 共享件与 token；禁止另起视觉体系。S2/S3 落地后，页卡优先接线共享 API，未就绪时允许本地薄对齐并在 PR 注明。
5. **切片**：每卡 1–2 小时；单页卡不偷偷改全局；全局/共享改动单独成卡并点名影响面。
6. **验收**：本机打开对应页目视 + 截图进 PR；不宣称外网终态。文案卡附「改前→改后」摘录。

## 阶段提醒

| 阶段 | 主题 | 本文档 |
|---|---|---|
| O / Q / R | token·壳层·租户主路径·平台配置台 | 已合入 |
| **S** | 反馈与去开发味 | **本波开启** |
| Z（最后） | 自由画布 + 分支/并行 | **现在不开**；S 收尾后再议 |

## S 波任务总览（S1–S15）

| 卡 | 内容 | 负责人 | 前置 | 估时 |
|---|---|---|---|---|
| **S1** | Toast/`alert`/`NxRequestAlert`/`NxSourceBanner` 对照规范 §feedback 盘点（docs-only 差距表） | Cursor | 无 | 1–2h |
| **S2** | 共享 Toast/message：`success` \| `error` \| `info` API + 样式对齐规范（薄封装或 token）；**暂不**全量改调用点 | Cursor | 建议 S1 后 | 1–2h |
| **S3** | 共享 Result/成功壳（图标 + 标题 + 下一步操作槽），供提交后页/态复用 | Cursor | 无（可与 S2 并行感） | 1–2h |
| **S4** | 残留 `NxSourceBanner` / 调试条：强化默认关（DEV/`VITE_NEXUS_DEV_NOTES`）；盘点仍强开或未走 `devNotesEnabled` 的页 | Cursor | 无 | 1–2h |
| **S5** | S 波打勾记账（docs-only）：勾选 S1–S15 + 截图/文案摘录路径 | Cursor | 大部合入后 | 1h |
| **S6** | 租户主路径关键动作接共享 Toast（订阅/开通/发起/审批或最接近实流）；S2 未就绪则本地薄对齐 | Claude | 建议 S2 | 1–2h |
| **S7** | 1 个租户结果/成功页或提交后态，接入 S3 壳（建议 `ProductStartView` 提交成功态；若更脏则 PR 写明） | Claude | 建议 S3 | 1–2h |
| **S8** | 租户路径页残留 Banner/`NxTodo` 开发条折叠或移除（星市/已开通/发起/待办） | Claude | 无 | 1–2h |
| **S9** | 租户面向文案去开发味：去掉「API未提供」风格 / 英文残留 / 调试口吻（桌面租户路径；**无 H5**） | Claude | 无 | 1–2h |
| **S10** | 1–2 个租户列表空态/错误文案一致（`NxEmpty` 原因+下一步）；建议 `MarketView` + `TodoInboxView` 或 `P4ManagementView` | Claude | 无 | 1–2h |
| **S11** | 平台保存/发布 Toast 接线：1–2 个配置页（建议 `FormTemplateEditorView` 和/或 `PublishCenterView`） | Codex | 建议 S2 | 1–2h |
| **S12** | 平台发布/绑定后的结果或确认反馈（薄；无新后端）；建议 `FormTemplateEditorView` 发布后或 `ProductDefinitionBindingView` 保存后 | Codex | 建议 S3 | 1–2h |
| **S13** | 平台/工作室/Connect 页残留 Banner 收口（已嵌 `NxSourceBanner` 的 studio 页择脏优先） | Codex | 无 | 1–2h |
| **S14** | 平台配置页文案去开发味（1–2 页；建议 `PublishCenterView` / `ProcessViewerView` / `FormTemplateEditorView` 择脏） | Codex | 无 | 1–2h |
| **S15** | Connect 域薄反馈/文案切片：`ClientsView` 或 `CredentialsView` 或 `WebhooksView`（择一；PR 写明） | Codex | 无 | 1–2h |

## 卡片要点

### Cursor S1–S5（共享反馈基建 + 收口文档）

- **S1** docs-only：在 `docs/planning/` 落差距表——对照 WEB 规范 §21 Toast/Message/Notification；列出现状（`alert` / `NxRequestAlert` / 行内 notice / 无共享 Toast）、Banner 嵌入页清单、优先接线的租户/平台动作。不改运行时代码。
- **S2** 提供最小共享 API（如 `nxMessage.success/error/info` 或等价 composable + 样式 token）。对齐规范：短文案、不可替字段错误、不可塞堆栈。本卡只建共享层 + 1 处冒烟演示（可在 ComponentGallery 或单测）；**不**批量替换调用点。
- **S3** `NxResult`（或现名）成功/结果壳：图标区 + 标题 + 描述 + `#action`/`#next` 槽。无路由强绑；页卡自行决定何时展示。
- **S4** 复核 `web/src/utils/devNotes.js` + `NxSourceBanner`：生产/演示默认关；盘点仍「看起来总开」的页（含其它调试条/`NxTodo` 开发说明）。需要时补默认关或文档开关说明；禁止把 Banner 永久钉在租户演示主路径。
- **S5** 新建/更新 `docs/planning/S5-S波打勾记账.md`（或同目录约定名）：S1–S15 勾选 + PR 链接 + 截图/文案摘录路径。docs-only。

### Claude S6–S10（租户主路径反馈与文案）

- **S6** 关键动作成功/失败改走共享 Toast（S2）或本地薄对齐：优先 `MarketView` 安装/订阅、`ProductStartView` 发起、`TodoInboxView` 审批/处理（以本机真实可点流为准；不可达则 PR 写明替代点）。禁止改 API 语义。
- **S7** 提交后结果态：优先 `ProductStartView`（或同主路径更脏的一页；**PR 写明**）接入 S3 壳；提供明确下一步（回待办/回已开通等）。
- **S8** 星市 / 已开通（`P4ManagementView`）/ 发起 / 待办：去掉或折叠残留开发 Banner、`NxTodo` 开发说明；租户演示默认不可见。
- **S9** 桌面租户路径文案：替换「当前读模型未提供」「API 未提供」口吻为用户可读空态/限制说明；清英文调试残留。**不碰** `web/src/mobile/**`。
- **S10** `NxEmpty`：原因 + 下一步一致。建议 `MarketView` + `TodoInboxView`（或 `P4ManagementView` 安装列表）；错误态与空态语气统一。

### Codex S11–S15（平台 / Connect 反馈与文案）

- **S11** `FormTemplateEditorView` 保存草稿/发布 与（可选）`PublishCenterView` 关键反馈：成功/失败 Toast；字段错误仍留表单内。S2 未合则本地薄对齐并注明。
- **S12** 发布确认后或绑定保存后的结果反馈：可用 S3 壳或页内成功条；**无新后端**。建议模板发布后「下一步：绑定产品」已有链路强化，或 `ProductDefinitionBindingView` 保存成功态。
- **S13** studio/平台仍嵌 `NxSourceBanner` 的页（如 `FormListView` / `FormStudioView` / `LedgerListView` / `LedgerDetailView` / `ReportView` / `ExportWizardView` / `DumpView` / `AuditTraceView` / `NotifyInboxView` 等）择更脏 1–3 页收口；默认走 `devNotes`。**择更脏的一页；PR 写明**。
- **S14** 平台配置文案去开发味：1–2 页去掉 M0/Live/接口路径口吻与噪声成功文案；保持测试「不出现发布成功噪声」类约束若仍有效则同步断言。
- **S15** Connect 单切片：`ClientsView` / `CredentialsView` / `WebhooksView` 择一——吊销/保存类 `confirm` 可保留，成功反馈与空态文案对齐 S 约定；不改连接器协议与密钥存储语义。

## 三人队列

| 人 | 顺序 |
|---|---|
| **Cursor** | **S1 → S2 → S3 → S4 →（后）S5**（S2/S3 可感并行但仍一卡一 PR；S5 最后） |
| **Claude** | **S6 → S7 → S8 → S9 → S10**（S6 建议等 S2；S7 建议等 S3；S8–S10 可较早穿插） |
| **Codex** | **S11 → S12 → S13 → S14 → S15**（S11 建议等 S2；S12 建议等 S3） |

S 波收尾后由 NexusOne 再议 **Z**（自由画布 + 分支/并行）；**本波不开 Z**。

## 后置（本波不开）

- Z：表单自由画布；流转分支/并行 BPM
- H5 全文与 `web/src/mobile/**` 反馈改造
- 支付、CI 复活、真 Keycloak / 外网终态宣称

## 本机路径

| 人 | 前缀 | worktree |
|---|---|---|
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| Codex | `codex/` | `D:\Projects\NexusOne-codex` |
| NexusOne | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

## 修订记录

- 2026-09-26：S 波开编。Eric：开 + 三人；基线 R6–R20（#503–#517）；主题 Toast/结果/残留 Banner/文案；不做 H5；Z 最后。

### 本机执行纪律（Eric-Desktop · 强制）

1. 所有文件只落本 worktree；禁止写桌面/`Downloads`/主克隆/他人树。
2. 自己跑终端/构建/测试/git；禁止让 Eric 反复点 Run。
3. 交付物是已推送 PR / 已合 main，不是待点击命令。
4. 禁止 junction；主克隆只 fetch。
