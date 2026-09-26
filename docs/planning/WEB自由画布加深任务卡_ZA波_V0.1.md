# WEB 自由画布加深任务卡（ZA 波）V0.1

- 基线：`main`（Z1–Z15 已合；Z5 打勾记账已收口；Eric「NexusOne next wave = Z deepen (ZA波)」）
- 依据：Z5 建议下一波「表单对齐/吸附、条件 DSL、运行时并行语义」；本波在 Z 薄 MVP 之上各开一条**可验收的加深薄片**；三人；每人 5 张；**不做 H5**
- 规范：以仓库 `docs/design` 桌面 WEB 规范为准；编辑器壳/空态/错误贴字段对齐 O/Q/R/S token 与反馈壳；流程侧契约对齐 Z6–Z12/Z15 已合路径
- 目标：三轴加深——① 对齐/吸附（薄边/网格，非对齐线全家桶）；② 条件 DSL（薄分支条件编辑，非规则引擎）；③ 运行时并行语义（校验/只读理解已有 `parallelGateway`，非引擎改造）
- 执行方：Cursor / Claude / Codex 三人并行；NexusOne 协调
- 现状速记（承接 Z 合入后）：
  - **表单**：`fieldLayout.js` + `FormTemplateEditorView` 自由画布壳 + `options.layout {x,y,w,h}` 持久化已齐；**无**边对齐/网格吸附
  - **流转 FE**：`exclusiveGateway` / `parallelGateway` 增删、条件边编辑、扇出布局、非顺序 UX 已齐；条件仍是薄字符串，**无**结构化 DSL / 校验提示加深
  - **流转 BE**：`ProcessDefinitionDraft*` 接受 exclusive/parallel + 条件流校验；Z13 证现列足够、无新 Flyway；**无**运行时并行语义只读说明 / 契约加深
  - **明确不做 H5**：`web/src/mobile/**` 不在本波范围

## 默认纪律

1. **一卡一 PR**；标题以 `ZA*` 开头；合并优先 **squash**。
2. 裸克隆 `D:\Projects\NexusOne` **只 fetch**；worktree 干活末尾。
3. **明确不做 / 硬禁**：
   - H5；支付；CI 复活；真 Keycloak 宣称
   - 完整商业化自由画布（对齐线全家桶 / 多选 / 撤销栈 / 组件市场）
   - 高级 BPM（会签 / 或签 / 加签 / 转交 / 子流程）
   - **Snap** = 仅薄边对齐 + 网格吸附；禁止磁力对齐线全家桶、多选框、撤销栈
   - **DSL** = 仅薄分支条件（结构化编辑 / 校验 UX）；禁止规则引擎、表达式语言全家桶、脚本沙箱
   - **Parallel** = 仅校验 / 只读理解现有 `parallelGateway` 语义；禁止运行时引擎改造、Flowable 部署重写
   - 整站视觉体系重做；大改无关业务 API
4. **视觉约束**：编辑器壳/空态/错误贴字段走 O/Q/R/S token 与反馈壳；禁止另起视觉体系。
5. **薄片**：每卡 1–2 小时；一产品切片；**前端/后端能拆则拆**；**迁移单独成卡**；禁止一卡吞整编辑器或整 BPM 引擎。
6. **Flyway**：仅证明必须时由专卡（ZA12）新增；取**下一全局版本号**；`outOfOrder` **关闭**；优先证明 JSON/现列足够、无需迁移。
7. **验收**：本地打开对应页目检 + 截图进 PR；注明覆盖空态/错误态；有测试则跑相关单测并在 PR 摘录。

## 阶段对照

| 阶段 | 内容 | 本卡地位 |
|---|---|---|
| O / Q / R / S | token·租户主路径·平台配置台·反馈去开发味 | 已合入 / 已收尾 |
| Z | 自由画布 + 分支/并行薄 MVP | 已合入（Z1–Z15） |
| **ZA** | Z 加深三轴：对齐/吸附 · 条件 DSL · 运行时并行语义 | **本波开启** |
| （后） | 高级 BPM / H5 全文 / 商业化画布全家桶 | 本波不开 |

## ZA 波任务总表（ZA1–ZA15）

| 卡 | 标题 | 建议方 | 前置 | 估时 |
|---|---|---|---|---|
| **ZA1** | 对齐/吸附差距盘点（对照 Z2–Z4 自由布局；docs-only 清单） | Cursor | 无 | 1–2h |
| **ZA2** | 吸附 util 薄片：边对齐 + 网格 snap（纯函数 + 单测；**不改**拖放 UI） | Cursor | 建议 ZA1 后 | 1–2h |
| **ZA3** | `FormTemplateEditorView` 薄接线：拖拽时消费 snap util（仅边/网格；非对齐线全家桶） | Cursor | 建议 ZA2 后 | 1–2h |
| **ZA4** | 吸附相关偏好/元数据持久化（若需）；优先 JSON 内嵌；若需新列则停并开 ZA12 | Cursor | 建议 ZA3 后 | 1–2h |
| **ZA5** | ZA 波打勾记账（docs-only）：勾选 ZA1–ZA15 + 截图/摘录路径 | Cursor | 大部合入后 | 1h |
| **ZA6** | 条件 DSL 差距盘点（对照 Z7 字符串条件边；docs-only；点名真实文件） | Claude | 无 | 1–2h |
| **ZA7** | 分支条件 FE 薄编辑器：结构化字段（如字段/运算符/值）↔ 现有 `condition` 串互转；**非**规则引擎 | Claude | 建议 ZA6 后 | 1–2h |
| **ZA8** | 条件 DSL 校验 UX：非法/空/不完整条件的页内提示，对齐 S 反馈壳 | Claude | 建议 ZA7 后 | 1–2h |
| **ZA9** | 条件相关布局若需：选中边面板/只读摘要薄片（非整表单重做） | Claude | 建议 ZA7 后 | 1–2h |
| **ZA10** | Claude 轴打勾/对照记账（docs-only）或条件轴收口清单 | Claude | 大部合入后 | 1h |
| **ZA11** | BE：`parallelGateway` 运行时语义校验薄片（fork/join 配对、非法结构错误码；**不改**引擎执行） | Codex | 无（可与 Claude 并行） | 1–2h |
| **ZA12** | **Schema/契约专卡**：优先证明无需迁移 + contract 测试；仅证明必须时 Flyway（下一全局版本；outOfOrder off） | Codex | ZA4/ZA11/ZA13 触发时 | 1–2h |
| **ZA13** | 并行语义只读预览/说明薄片（Studio 或 ProcessViewer 只读理解 fork/join；无编辑器加深） | Codex | 建议 ZA11 后 | 1–2h |
| **ZA14** | 并行/条件草稿 validate 契约测试加深（`p3-process-draft-validate` 等） | Codex | 建议 ZA11 后 | 1–2h |
| **ZA15** | Codex 轴打勾记账 / ZA 波 BE 侧收口清单（docs-only） | Codex | 大部合入后 | 1h |

## 薄片要求

### Cursor ZA1–ZA5（对齐/吸附：盘点 · util · 接线 · 持久化 · 记账）

- **ZA1** docs-only：在 `docs/planning/` 写清差距——Z2–Z4 `layout {x,y,w,h}` 自由放置 vs 边对齐/网格吸附；点名真实文件：`fieldLayout.js`、`FormTemplateEditorView.vue`、`FormStudioView.vue`、相关单测。列出 ZA2–ZA5 建议验收句，不写实现。**硬禁**：对齐线全家桶、多选、撤销栈、组件市场。
- **ZA2** 共享 util（扩 `fieldLayout.js` 或邻近 `fieldSnap.js`）：给定拖拽坐标 + 兄弟框 + 网格步长 → 返回吸附后坐标；单测覆盖边对齐命中/未命中、网格取整、阈值外不吸附；**禁止**本卡改拖放 UI / 画辅助线。
- **ZA3** `FormTemplateEditorView`：拖拽移动时调用 snap util；可选极简吸附反馈（如坐标取整可见即可）；**禁止**一次做对齐辅助线全家桶、多选框、磁力参考线产品化。
- **ZA4** 若吸附开关/网格步长需持久化：优先字段 JSON / 模板 options 内嵌。若发现必须新 DB 列，**停写**并在 PR 注明交 ZA12，勿私自开迁移。若 ZA3 证明无需新元数据，本卡写短记账「无需持久化」+ 单测/摘录即可。
- **ZA5** 新建/更新 `docs/planning/ZA5-ZA波打勾记账.md`（或同目录约定名）：ZA1–ZA15 勾选 + PR 链接 + 截图/摘录路径；docs-only。

### Claude ZA6–ZA10（条件 DSL：盘点 · FE 编辑 · 校验 UX · 布局 · 记账）

- **ZA6** docs-only：对照 Z7 `sequenceFlow.condition` 字符串编辑；点名 `ProcessViewerView.vue`、`processDraft.js`、条件序列化进 BPMN `conditionExpression` 的现路径；写清「薄 DSL = 字段/运算符/值互转」vs「规则引擎」边界；列出 ZA7–ZA10 验收句。
- **ZA7** 选中条件边时：薄结构化编辑（字段选择 + 运算符 + 值）与现有 condition 字符串互转；空条件仍允许；单测覆盖往返；**禁止**规则引擎、脚本、多条件组/AND-OR 树产品化。
- **ZA8** 非法运算符、缺字段、不完整表单：页内错误贴边或汇总，文案去开发味，对齐 S Toast/Result 习惯；不改 BE 语义（BE 加深归 Codex）。
- **ZA9** 若选中边面板过挤：只做条件摘要/面板布局薄整理；禁止重做整页流程编辑器。
- **ZA10** docs-only：Claude 轴（ZA6–ZA9）勾选 + PR 链接 + 与 Cursor/Codex 交界备注；或条件轴收口清单。

### Codex ZA11–ZA15（运行时并行语义：BE 校验 · schema · 只读预览 · 契约测 · 记账）

- **ZA11** `ProcessDefinitionDraftService` / `ProcessDefinitionValidator`（及测试）：加深 `parallelGateway` 结构校验（如 fork/join 配对、非法多入多出组合给出明确错误码/文案）；**禁止**改 Flowable 运行时执行、部署管线、会签或签。
- **ZA12** **仅本卡**可加 Flyway；优先证明 JSON/现列足够 → schema contract 测试 + 短记账「无需迁移」。若必须迁移：下一全局版本号、`outOfOrder` off、禁止夹带功能 UI。承接 ZA4（吸附元数据）与 ZA11（若误触 schema）触发。
- **ZA13** Studio 或 `ProcessViewerView`：对已有 parallel 结构做**只读**语义说明/预览（如标注 fork→join 范围）；无编辑加深；空态清楚。
- **ZA14** 补/扩 `p3-process-draft-validate-http.test.js` 或 Java 契约测：并行非法结构 / 合法 fork-join / 与条件边组合；PR 摘录通过命令。
- **ZA15** docs-only：Codex 轴（ZA11–ZA14）勾选 + PR 链接 + schema 结论回指 ZA12；ZA 波 BE 侧收口。

## 三人队列

| 人 | 顺序 |
|---|---|
| **Cursor** | **ZA1 → ZA2 → ZA3 → ZA4 → ZA5**（ZA5 大部合入后；ZA1–ZA4 一卡一 PR，**禁止中途停等 Eric 点 Run/Always Allow**——本批连续做完队列全部卡） |
| **Claude** | **ZA6 → ZA7 → ZA8 → ZA9 → ZA10**（ZA7 后串行 ZA8/ZA9；一卡一 PR；同样连续跑完，不等 Run 点击） |
| **Codex** | **ZA11 → ZA12 → ZA13 → ZA14 → ZA15**（ZA12 按需优先「无需迁移」；ZA13/ZA14 可在 ZA11 后；一卡一 PR；连续跑完） |

## 延后（本波不做）

- H5 全文与 `web/src/mobile/**` 改造
- 会签/或签/加签转交/子流程/监听器等高级 BPM
- 完整商业化自由画布（对齐线全家桶、多选、撤销栈、组件市场、磁力参考线产品化）
- 规则引擎 / 脚本沙箱 / 复杂 AND-OR 条件树
- Flowable 运行时引擎改造、并行执行语义重写
- 支付、CI 复活、真 Keycloak / 外网终态宣称

## 工作树路径

| 人 | 前缀 | worktree |
|---|---|---|
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| Codex | `codex/` | `D:\Projects\NexusOne-codex` |
| NexusOne | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

## 修订记录

- 2026-09-26：ZA 波初稿。Eric 选 Z deepen；三轴=对齐/吸附 · 条件 DSL · 运行时并行语义；承接 Z1–Z15 已合；不做 H5 / 商业化画布全家桶 / 高级 BPM；Snap/DSL/Parallel 均薄片定义；Flyway 仅证明必须且 outOfOrder off。

### 本地执行纪律（Eric-Desktop · 强制）

1. 改动文件只落本 worktree；禁止写桌面/`Downloads`/裸克隆/他人目录。
2. 自己跑终端/构建/测试/git；**禁止等 Eric 点 Run / Always Allow**——批内连续做完队列全部卡，一卡一 PR，卡与卡之间不停顿等批准。
3. 自己开 PR / 能合则 squash 合 main；不是只交半截补丁。
4. 禁止 junction；裸克隆只 fetch。
5. **Cursor 专项**：接踢后必须连续完成 ZA1→ZA5 全队列；不得在第一卡结束后停下来等 Eric 再点 Run。