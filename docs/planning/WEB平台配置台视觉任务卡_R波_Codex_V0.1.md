# WEB 平台配置台视觉任务卡（R 波 · Codex 先开）V0.1

- 基线：`main`（Q 波大部合入；Cursor 仍可能在收 Q4–Q6；Claude 可能补 Q8）
- 依据：Eric 要求给 **Codex 加满 ≥5 张**；路线图 R = 平台配置台页；**不做 H5**；自由画布/分支并行仍 **Z**
- 规范：`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md`（以仓库现名为准）
- 目标：平台侧「模板 / 表单工作室 / 产品定义绑定 / 审计」列表与编辑壳层**看起来像正式配置台**，不动业务语义、不做自由画布
- 执行方：**Codex（本波 5 卡）**；Claude/Cursor 继续收尾 Q，暂不抢 R
- worktree：`D:\Projects\NexusOne-codex`；前缀 `codex/`

## 默认决策

1. **一卡一 PR**；标题以 `R*` 开头；做完 squash 合并。
2. 主克隆 `D:\Projects\NexusOne` **只 fetch**；只在 Codex worktree 改文件。
3. **明确不做**：H5；支付；CI 复活；真 Keycloak 宣称；表单自由画布 / 栅格编辑器；流转分支/并行（Z）。
4. **明确不做**：改审批/发布/绑定接口语义；新业务字段；大重构路由。
5. **切片纪律**：每卡 1–2 小时；能复用 Q/O 共享组件（页眉、空态、Skeleton、状态标签、表密度）就复用，禁止另起一套视觉系统。
6. **验收**：对照规范相关章节打勾；本机打开对应平台页目视 + 截图进 PR 说明。

## R 波 · Codex 五卡总览

| 卡 | 内容 | 类型 | 负责人 | 前置 | 估时 |
|---|---|---|---|---|---|
| **R1** | `FormTemplateListView`：页眉槽位、表密度、空态 `NxEmpty`、加载 Skeleton；不大改列表查询逻辑 | 前端·页 | Codex | 无 | 1–2h |
| **R2** | `FormTemplateEditorView` 壳层：顶栏/底栏操作区单一 Primary、字段区吃 O2–O4；**不**做自由画布 | 前端·页 | Codex | 建议 R1 后 | 1–2h |
| **R3** | `FormStudioView` / `ProcessViewerView`（有则两页都碰，优先工作室）：步骤列表可读性、空态、密度；仍为顺序流展示，不做分支编辑器 | 前端·页 | Codex | 无 | 1–2h |
| **R4** | `ProductDefinitionBindingView`：页眉 + 绑定表密度 + 空态/错误贴字段风格；不改绑定 API | 前端·页 | Codex | 无 | 1–2h |
| **R5** | `PlatformAuditView`（或 `StudioHomeView` 若审计页过薄）：列表密度、状态标签、空态；附 R1–R4 对照打勾短记（可同 PR 一小段 docs） | 前端·页+轻文档 | Codex | R1–R4 大部合入后更佳，也可先做页再补勾选 | 1–2h |

## 卡片正文

### R1 — 表单模板列表

对齐租户列表已落地的页眉/表密度/空态/Skeleton。过滤区若仍是大 Card，按 O10/Q 方向轻改，不大重构查询。

### R2 — 模板编辑器壳层

顶栏标题与操作区：一个 Primary（保存/发布择一为主，其余 secondary）。字段编辑区间距与必填星走共享样式。**禁止**引入自由画布或拖拽栅格新交互。

### R3 — 表单工作室 / 流程只读展示

顺序步骤列表可读（标题、状态、空列表）；加载与空态统一。不做分支/并行编辑（Z）。

### R4 — 产品定义绑定页

页眉 + 表格/表单密度；空态说明下一步；字段错误贴字段。禁止改绑定提交语义。

### R5 — 平台审计（或工作室首页）+ 轻打勾

列表视觉与 Q 侧状态标签一致；PR 或 `docs/planning/R5-R波Codex打勾.md` 勾选 R1–R5。

## Codex 队列

| 人 | 顺序 |
|---|---|
| **Codex** | **R1 → R2 → R3 → R4 → R5**（R3/R4 可与 R2 感并行，但仍一卡一 PR；R5 收尾） |

Claude/Cursor：**继续 Q 收尾**（Q4/Q5/Q6/Q8），本文件不改派他们的 R 卡。整波 R 若还需 Claude/Cursor 扩卡，由 NexusOne 另开补篇。

## 本机路径

| 人 | 前缀 | worktree |
|---|---|---|
| Codex | `codex/` | `D:\Projects\NexusOne-codex` |
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| NexusOne | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

## 修订记录

- 2026-09-26：初稿。Eric：给 Codex 加满 5 张；开 R 波平台配置台薄卡；不抢 Cursor Q 收尾。

### 本机执行纪律（Eric-Desktop · 强制）

1. 所有文件只落本 worktree；禁止写桌面/`Downloads`/主克隆/他人树。
2. 自己跑终端/构建/测试/git；禁止让 Eric 反复点 Run。
3. 交付物是已推送 PR / 已合 main，不是待点击命令。