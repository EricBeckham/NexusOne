# ZA10 条件 DSL 轴打勾记账（Claude · ZA6–ZA9，docs-only）

- 卡片：`docs/planning/WEB自由画布加深任务卡_ZA波_V0.1.md` · ZA10（规划 PR #550，记账时尚未合入 main；本文按其卡面对照）
- 盘点：`docs/planning/ZA6-条件DSL差距盘点.md`
- 记账基线：`main@e250f015`（ZA9 合入后）
- 范围：条件 DSL 轴（薄分支条件）。对齐/吸附（Cursor ZA1–ZA5）、并行语义 BE（Codex ZA11–ZA15）只记交界。

## 1. 打勾

| 卡 | 验收要点（摘自 ZA6 §4） | 状态 | PR |
|---|---|---|---|
| ZA6 | 差距盘点：现状真实文件、关键差距（表单值不是流程变量）、薄 DSL 边界、ZA7–ZA10 验收句、交界 | ✅ | [#552](https://github.com/EricBeckham/NexusOne/pull/552) |
| ZA7 | 选中排他网关出线 → 「条件 / 原文」两种方式；变量（审批结果、审批人类型、其他变量）· 运算符 · 值 → 规范串 `${var op value}`；保存进 `conditionExpression`、重开回填；解析不了的串归「高级表达式」原样保留；清空 = 无条件；`conditionDsl.js` 往返单测 | ✅ | [#554](https://github.com/EricBeckham/NexusOne/pull/554) |
| ZA8 | 不完整 / 非法条件贴字段提示（`aria-invalid`）；不完整草稿不写入、提示「保存时仍使用…」；原文轻校验（`${…}`、非空、引号括号成对）；问题边标红 + 「条件需要修改 · N 处」可定位；保存前拦截 + 错误 Toast；修复 `${amount > 很多}` 被写入的缺陷；不改 BE | ✅ | [#558](https://github.com/EricBeckham/NexusOne/pull/558) |
| ZA9 | 连线面板条件行占满宽度（166px → 238px）；网关面板「分支条件」一览可点选；图上标签与「相关连线」用人话摘要（审批结果 等于 驳回），原文放 title | ✅ | [#559](https://github.com/EricBeckham/NexusOne/pull/559) |
| ZA10 | 本记账 | ✅ | 本 PR |

硬禁核对：无规则引擎、无脚本、无 AND/OR 条件组或树、无表达式求值、无 H5（未触 `web/src/mobile/**`）、无会签/或签/加签/转交/子流程、无 BE 改动、无 Flyway。

## 2. 验证摘录

- 单测：`web/src/views/studio/conditionDsl.test.js`（往返、容错、转义、高级表达式原样、不完整/类型不符拒绝、逐字段提示、原文轻校验、措辞）；`web/src/views/studio/ProcessViewerView.test.js`（ZA7 编辑器往返与保存、高级表达式不改写；ZA8 字段提示 / 保留上一个完整值 / 原文错误标边 / 保存拦截；ZA9 分支一览 / 宽编辑器 / 人话标签）。
- 命令（各卡 PR 摘录）：`cd web && npx vitest run` → ZA9 合入时 **128 files / 530 tests passed**；`npm run build` 通过。
- 本地实机（Eric-Desktop，一次性库 + stub 登录 林晓，1440×900，`/p3/workflow`）：
  - ZA7：新草稿 → 条件分支 → 分支出线 → 审批结果 / 等于 / 驳回 → `${outcome == 'REJECT'}` → 保存 → 从列表重开，编辑器回填 `outcome` / `REJECT`。
  - ZA8：`amount` / 大于 / 很多 → 「「大于」需要填写数字」+「保存时仍使用：无条件」；原文 `amount > 1` → 「条件需要写成 ${…} 的形式」、边标红、汇总条目；保存被拦截并 Toast，未发出请求。
  - ZA9：网关面板「分支 1 → 结束 · 无条件」「分支 2 → 分支审批 · 审批结果 等于 驳回」；边标签人话；编辑器宽 238px。
  - 截图工具在本机多次超时，版面以 DOM 量测记录于各 PR。

## 3. 交界备注

| 事项 | 对方 | 状态 / 建议 |
|---|---|---|
| 申请表单字段值写入流程变量（让「金额 > 1000」这类条件在运行时真正可用） | BE / 产品（后续波次） | 未开卡。现只有 `assignee` / `initiator` / `assigneeType` / `outcome`；FE 变量目录与「其他变量」提示已按此现状写明，届时扩展目录即可。 |
| 条件表达式的服务端语法 / 变量存在性校验 | Codex（BE） | 不在 ZA11–ZA15 卡面；FE 只做轻校验 + 保存拦截，服务端仍只查空条件与并行出线带条件（Z11/Z12）。 |
| 网关 `default` 出线读写 | FE + BE（后续） | 未做；Z10 的「运行时会始终走第一条分支」提示保留。 |
| 并行网关出线条件 | Codex ZA11–ZA15 | 薄 DSL 不在并行出线上提供编辑（Z9 约定），与 BE `PARALLEL_FLOW_CONDITION_UNSUPPORTED` 一致。ZA11–ZA15 记账时尚未合入 main。 |
| 对齐 / 吸附 | Cursor ZA1–ZA5 | 无交集（表单画布 vs 流程图）；ZA1–ZA4 已合（#551 #553 #555 #556）。 |
| 规划文档 | NexusOne #550 | 记账时仍为 open；ZA 波卡面以该 PR 分支内容为准。 |
