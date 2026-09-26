# WEB 自由画布与分支并行任务卡（Z 波）V0.1

- 基线：`main`（S1–S15 已合；S 反馈/去开发味收尾后 Eric「开始」开 Z）
- 依据：O 起路线图「Z（最后）= 自由画布 + 分支/并行」；上线口径曾锁 **MVP 列表 DnD（非自由画布）**；现 Eric 打开 Z，交付**此前延后**的自由画布与分支/并行薄片；三人；每人不少于 5 张；**不做 H5**
- 规范：以仓库 `docs/design` 桌面 WEB 规范为准；布局/流程编辑观感对齐 O/Q/R token 与 S 反馈壳
- 目标：在现有 `FormTemplateEditorView` 列表 DnD 与 `ProcessViewerView` **顺序流**之上，各开一条**可验收的薄 MVP**：表单自由布局编辑器 MVP（非完整产品）；流转 exclusive/parallel 网关与条件边薄片（非高级 BPM 全家桶）
- 执行方：Cursor / Claude / Codex 三人并行；NexusOne 协调
- 现状速记（扫仓库相关）：
  - **表单**：`FormTemplateEditorView` 已有类型拖入 + 列表内排序（G3 MVP）；`canvas` 仍是字段列表，**无** x/y/w/h 自由放置；`FormStudioView` 有 `pane canvas` 预览壳，未消费自由布局元数据
  - **流转**：`ProcessViewerView` 文案已写「顺序节点…不支持分支与并行」；`processDraft.js` 序列化认识 `exclusiveGateway`，但 `addDraftNode` **拒绝**网关；`sequentialFlow.js` 把网关判为非顺序；`processLayout.js` 按深度分列自动排版
  - **后端**：`nexus-p3-studio` `ProcessDefinitionDraft*` + `V551__process_definition_draft.sql`；分支/并行校验与 `parallelGateway` 仍薄/缺
  - **明确不做 H5**：`web/src/mobile/**` 不在本波范围

## 默认纪律

1. **一卡一 PR**；标题以 `Z*` 开头；合并优先 **squash**。
2. 裸克隆 `D:\Projects\NexusOne` **只 fetch**；worktree 干活末尾。
3. **明确不做**：H5；支付；CI 复活；真 Keycloak 宣称；高级 BPM 全家桶（会签/或签/加签转交/子流程等，除非卡面点名）；整站视觉体系重做；大改无关业务 API；完整商业化自由画布产品。
4. **视觉约束**：编辑器壳/空态/错误贴字段走 O/Q/R/S token 与反馈壳；禁止另起视觉体系。
5. **薄片**：每卡 1–2 小时；一产品切片；前端/后端能拆则拆；**迁移单独成卡**；禁止一卡吞整编辑器或整 BPM 引擎。
6. **验收**：本地打开对应页目检 + 截图进 PR；注明覆盖空态/错误态；有测试则跑相关单测并在 PR 摘录。

## 阶段对照

| 阶段 | 内容 | 本卡地位 |
|---|---|---|
| O / Q / R / S | token·租户主路径·平台配置台·反馈去开发味 | 已合入 / 已收尾 |
| **Z** | 自由画布 + 分支/并行 | **本波开启** |
| （后） | 高级 BPM / H5 全文等 | 本波不开 |

## Z 波任务总表（Z1–Z15）

| 卡 | 标题 | 建议方 | 前置 | 估时 |
|---|---|---|---|---|
| **Z1** | 自由画布 + 分支/并行差距盘点（对照 G3/G4 列表 DnD 与顺序流；docs-only 清单） | Cursor | 无 | 1–2h |
| **Z2** | 字段布局元数据模型 FE：`layout: { x,y,w,h }`（或等价 grid）util + 单测；**不改**拖放交互 | Cursor | 建议 Z1 后 | 1–2h |
| **Z3** | `FormTemplateEditorView` 自由画布壳 MVP：画布区绝对/网格放置字段（薄片，非完整产品） | Cursor | 建议 Z2 后 | 1–2h |
| **Z4** | 自由布局读写进模板 payload FE（JSON 内嵌优先；若需新列则停并开 Z13） | Cursor | 建议 Z3 后 | 1–2h |
| **Z5** | Z 波打勾记账（docs-only）：勾选 Z1–Z15 + 截图/摘录路径 | Cursor | 大部合入后 | 1h |
| **Z6** | `processDraft` + `ProcessViewerView`：允许 `exclusiveGateway` 增删薄片（解除「仅支持起/用户/结束」） | Claude | 无 | 1–2h |
| **Z7** | 分支边条件表达式编辑薄片：`sequenceFlow.condition` 在选中边上可编 | Claude | 建议 Z6 后 | 1–2h |
| **Z8** | `processLayout.js` 分支扇出布局薄片：gateway 多出边可读（非整引擎重写） | Claude | 建议 Z6 后 | 1–2h |
| **Z9** | `parallelGateway` 节点增删 + fork/join 展示薄片 FE（`processDraft` 序列化对齐） | Claude | 建议 Z6 后 | 1–2h |
| **Z10** | 非顺序流校验提示 UX：`ProcessViewerView` / `sequentialFlow` 错误与空态文案对齐 S 反馈壳 | Claude | 建议 Z6–Z9 有进展 | 1–2h |
| **Z11** | BE `ProcessDefinitionDraftService`：接受 `exclusiveGateway` + 条件流校验薄片 | Codex | 建议与 Z6/Z7 对齐 | 1–2h |
| **Z12** | BE：接受 `parallelGateway` 校验/草稿序列化薄片 | Codex | 建议与 Z9 对齐 | 1–2h |
| **Z13** | **Migration 专卡**：布局元数据或草稿节点类型若需新列则 Flyway；否则 schema contract 证明无需并记账 | Codex | Z4/Z11/Z12 触发时 | 1–2h |
| **Z14** | `FormStudioView` 消费布局元数据只读预览薄片（无编辑器则列表回退） | Codex | 建议 Z2/Z4 后 | 1–2h |
| **Z15** | 分支/并行草稿 validate HTTP/契约测试薄片（`p3-process-draft-validate` 等） | Codex | 建议 Z11/Z12 后 | 1–2h |

## 薄片要求

### Cursor Z1–Z5（盘点 · 布局模型 · 画布壳 · 持久化 · 记账）

- **Z1** docs-only：在 `docs/planning/` 写清差距——G3 列表 DnD vs 自由画布；G4 顺序流 vs exclusive/parallel；点名真实文件：`FormTemplateEditorView.vue`、`FormStudioView.vue`、`ProcessViewerView.vue`、`processDraft.js`、`processLayout.js`、`sequentialFlow.js`、`ProcessDefinitionDraftService.java`、`V551__process_definition_draft.sql`。列出 Z2–Z15 建议验收句，不写实现。
- **Z2** 共享 util（如 `web/src/views/tenants/fieldLayout.js` 或邻近模块）：读写/规范化 `layout`；单测覆盖缺省与非法值；**禁止**本卡改拖放 UI。
- **Z3** `FormTemplateEditorView`：在保留列表 DnD 可回退的前提下，增加自由放置薄片（拖到画布坐标或简易网格）；空态/选中态走 token；**禁止**一次做对齐辅助线/多选/吸附全家桶。
- **Z4** 保存/加载模板时带上 layout；优先字段 JSON 内嵌。若发现必须新 DB 列，**停写**并在 PR 注明交 Z13，勿私自开迁移。
- **Z5** 新建/更新 `docs/planning/Z5-Z波打勾记账.md`（或同目录约定名）：Z1–Z15 勾选 + PR 链接 + 截图/摘录路径；docs-only。

### Claude Z6–Z10（流转分支/并行 FE）

- **Z6** 放开 `addDraftNode` 对 `exclusiveGateway`；`ProcessViewerView` 可增删网关节点；单测更新 `processDraft.test.js` / `ProcessViewerView.test.js`；**不做**完整条件表单 DSL。
- **Z7** 选中 `sequenceFlow` 可编辑 `condition`；序列化进 BPMN `conditionExpression`（已有草稿路径则复用）；空条件允许（无条件边）。
- **Z8** `processLayout.js`：gateway 多出边时扇出排版可读；单测；禁止引入第三方图编辑器。
- **Z9** `parallelGateway`：增删 + 序列化/解析；视图上可区分 fork/join（可先用命名或成对约定，PR 写清）；**不做**运行时引擎改造。
- **Z10** 非顺序/非法网关结构：页内错误贴节点或汇总，文案去开发味，对齐 S Toast/Result 习惯；不改 BE 语义。

### Codex Z11–Z15（BE 校验 · 迁移 · Studio 预览 · 契约测）

- **Z11** `ProcessDefinitionDraftService`（及 Controller/测试）：草稿含 `exclusiveGateway` + 条件流时校验通过或给出明确错误码/文案；薄片，禁止重写整套 Flowable 部署。
- **Z12** 同上路径接受 `parallelGateway`；与 Z9 FE 契约对齐；单测。
- **Z13** **仅本卡**可加 Flyway；若 Z4/Z11/Z12 证明 JSON/现列足够，则写 schema contract 测试 + 短记账「无需迁移」；禁止夹带功能 UI。
- **Z14** `FormStudioView`：若模板含 layout 元数据则按坐标/网格只读预览，否则回退现列表预览；空态清楚；**不做** Studio 内编辑。
- **Z15** 补/扩 `web/src/api/p3-process-draft-validate-http.test.js` 或等价契约测：分支/并行草稿 validate 路径；PR 摘录通过命令。

## 三人队列

| 人 | 顺序 |
|---|---|
| **Cursor** | **Z1 → Z2 → Z3 → Z4 ···· Z5**（Z5 大部合入后；Z2/Z3/Z4 一卡一 PR，**禁止中途停等 Eric 点 Run/Always Allow**——本批连续做完队列全部卡） |
| **Claude** | **Z6 → Z7 → Z8 → Z9 → Z10**（Z7–Z9 可在 Z6 合入后串行；一卡一 PR） |
| **Codex** | **Z11 → Z12 → Z13 → Z14 → Z15**（Z13 按需；Z14 可与 Z11/Z12 交错但一卡一 PR；Z15 靠后） |

## 延后（本波不做）

- H5 全文与 `web/src/mobile/**` 改造
- 会签/或签/加签转交/子流程/监听器等高级 BPM
- 完整商业化自由画布（对齐线、多选、撤销栈、组件市场等）
- 支付、CI 复活、真 Keycloak / 外网终态宣称

## 工作树路径

| 人 | 前缀 | worktree |
|---|---|---|
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| Codex | `codex/` | `D:\Projects\NexusOne-codex` |
| NexusOne | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

## 修订记录

- 2026-09-26：Z 波初稿。Eric「开始」于 S 收尾后；范围=延后的自由画布 + 分支/并行；对照 G3/G4 MVP DnD；不做 H5；三人各 ≥5 卡。

### 本地执行纪律（Eric-Desktop · 强制）

1. 改动文件只落本 worktree；禁止写桌面/`Downloads`/裸克隆/他人目录。
2. 自己跑终端/构建/测试/git；**禁止等 Eric 点 Run / Always Allow**——批内连续做完队列全部卡，一卡一 PR，卡与卡之间不停顿等批准。
3. 自己开 PR / 能合则 squash 合 main；不是只交半截补丁。
4. 禁止 junction；裸克隆只 fetch。
