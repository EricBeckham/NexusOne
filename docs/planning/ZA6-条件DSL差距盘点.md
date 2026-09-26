# ZA6 条件 DSL 差距盘点（docs-only）

- 卡片：`docs/planning/WEB自由画布加深任务卡_ZA波_V0.1.md` · ZA6（Claude）
- 基线：`main@873daf3a`（Z1–Z15 已合；Z7 条件边为薄字符串编辑）
- 范围：流转分支条件（`sequenceFlow.condition`）从「自由字符串」加深到**薄 DSL**（单个「变量 · 运算符 · 值」比较）。**不是**规则引擎、脚本、表达式语言全家桶或 AND/OR 条件树。
- 本卡只写清单，不改运行时代码。

## 1. 现状（真实文件）

| 位置 | 现状 | 备注 |
|---|---|---|
| `web/src/views/studio/ProcessViewerView.vue` | 选中连线 → 「连线属性」面板；仅当源节点是 `exclusiveGateway` 时给一个 `<input v-model="selectedFlow.condition">`，占位 `例如 ${amount > 1000}`；其他连线显示「只有从条件分支出发的连线可以设置条件」，并行分支连线显示「并行分支的连线不设条件」 | Z7 / Z9 |
| 同上 · 图 | 带条件的边为虚线，拐点处显示截断到 18 字的**原始表达式**（如 `${amount > 1000}`）；「相关连线」列表显示 `条件 ${…}` / `无条件` | Z7 / Z8 |
| 同上 · 校验 | 服务端错误按 `flow_id` 贴边、汇总「连线 A → B：…」；排他网关所有出线都无条件时给非阻断提示「运行时会始终走第一条分支」 | Z10 |
| `web/src/views/studio/processDraft.js` | `parseDraftXml` 读 `<conditionExpression>` 文本（trim）→ `flow.condition`；`serializeDraftXml` 非空时写 `<conditionExpression xsi:type="bpmn:tFormalExpression">…</conditionExpression>`（XML 转义），空串/`null` 不写 | 往返已测 |
| `web/src/views/studio/sequentialFlow.js` | 任一连线带条件即判为非顺序流（走流程图编辑） | — |
| `nexus-p3-studio/.../flow/ProcessDefinitionValidator.java` | 条件相关只有：`FLOW_CONDITION_EMPTY`（空 `<conditionExpression>`）、`PARALLEL_FLOW_CONDITION_UNSUPPORTED`（并行网关出线带条件）；**不解析表达式本身** | Z11 / Z12，归 Codex |
| `nexus-p3-studio/.../flow/SequentialApprovalService.java` | 启动时只设流程变量 `assignee`、`initiator`、`assigneeType`、`outcome=""`；办理时 `taskService.complete(…, Map.of("outcome", outcome))`，`outcome` ∈ `APPROVE` / `REJECT` | 决定「条件能引用什么」 |

## 2. 关键差距

1. **条件引用的变量在运行时不一定存在。** 申请表单的字段值**没有**作为流程变量传入（`start()` 只设上表四个变量）。Z7 的占位 `${amount > 1000}` 会误导：这样的条件在 Flowable 求值时找不到 `amount`。真实可用的变量只有：
   - `outcome`：上一个审批任务的结果，`APPROVE`（同意）/ `REJECT`（驳回）——最有用，可做「同意走 A、驳回走 B」；
   - `assigneeType`：发起时审批人类型（枚举名）；
   - `initiator` / `assignee`：人员 UUID，作为条件实用性低。
   → FE 薄 DSL 必须把**这些真实变量**做成可选项；自由输入的变量名要明确提示「运行时需要存在该变量」。把表单字段值传成流程变量属于**后端能力**，不在 Claude FE 轴（见 §6 交界）。
2. **只有原始字符串编辑。** 管理员必须手写 `${outcome == 'APPROVE'}` 这样的 JUEL；无变量选择、无运算符约束、引号/数字写法全靠手。
3. **校验只到「空」。** FE 不检查语法；BE 只查空 `conditionExpression`，不解析表达式。缺运算符、缺值、数字比较却填了文字、引号不配对都要到运行时才暴露。
4. **展示只有原文。** 图上标签和「相关连线」都是 `${…}` 原文，业务人员难读；没有「审批结果 = 同意」这样的人话摘要。
5. **默认分支不可表达。** BPMN 的网关 `default` 出线 FE 不读不写；目前靠「无条件出线」近似，Z10 只能提示「会走第一条分支」。（写 `default` 属于结构语义，本波只记账，不做。）

## 3. 薄 DSL 边界（本波定义）

**做（薄）**：单个比较，形如 `${变量 运算符 值}`。

| 要素 | 取值 |
|---|---|
| 变量 | 目录内：`outcome`（审批结果）、`assigneeType`（审批人类型）；或「其他变量」自由输入（标识符 `[A-Za-z_][A-Za-z0-9_]*`，带运行时存在性提示） |
| 运算符 | `==` 等于、`!=` 不等于；数值另有 `>` `>=` `<` `<=` |
| 值 | 文本（单引号字面量，内部 `'` 转义）、数字、`true`/`false`；目录变量给枚举下拉（如 同意 `APPROVE` / 驳回 `REJECT`） |
| 空条件 | 仍允许，表示无条件（与 Z7 一致） |

**互转规则**：结构 → 串固定格式 `${var op value}`（单空格）；串 → 结构用单条正则解析，容忍多余空格与 `"`/`'` 两种引号。**解析不了的串**（函数调用、`&&`/`||`、方法、三元等）归为「高级表达式」：原样保留、可在原文模式编辑，**绝不**改写或丢弃。

**不做（硬禁）**：AND/OR 条件组或树、函数/方法调用构造器、脚本、规则引擎、跨字段表达式、表达式求值/试算、服务端表达式解析（BE 归 Codex，且本波不要求）。

## 4. ZA7–ZA10 验收句

- **ZA7 FE 薄编辑器**
  - 选中排他网关出线：面板出现「变量 / 运算符 / 值」三件套 + 「原文」切换；选择后 `flow.condition` 即时变为 `${outcome == 'APPROVE'}` 等规范串；保存进 `conditionExpression`，重开后回填到三件套。
  - 已有串（含 Z7 手写的 `${amount > 1000}`）能解析回结构；解析不了的原样留在原文模式，保存不变。
  - 纯函数 `conditionDsl.js`（parse/format）有往返单测：目录变量、自由变量、数字/文本/布尔、引号与转义、多余空格、空串、高级表达式不丢失。
  - 清空 = 无条件（写回空）。
- **ZA8 校验 UX**
  - 不完整（缺变量/运算符/值）、非法（变量名不合法、数值运算符配非数字、未闭合引号）在面板内贴字段提示，边在图上标记；汇总列入 Z10 的「校验未通过 · N 处」风格或保存前提示，文案无开发腔；不改 BE。
  - 不完整条件不会被静默写成半截串（保存前拦住或保持上一个完整值并提示）。
- **ZA9 布局薄片（若需要）**
  - 连线面板在 280px 侧栏内不挤：三件套纵向排布、原文折叠；图上标签与「相关连线」显示人话摘要（「审批结果 = 同意」），原文放 `title`。
- **ZA10 记账**
  - `docs/planning/` 勾选 ZA6–ZA9 + PR 链接 + 与 Cursor（无交集）/Codex（BE 表达式校验、表单值入流程变量、`default` 出线）交界备注。

## 5. 风险与约束

- JUEL 在 Flowable 中对缺失变量求值会报错；FE 目录只列启动/办理时确定存在的变量，是降低该风险的最低成本做法。
- 数字比较对 `outcome` 这类字符串变量无意义：目录变量携带类型，运算符随类型收窄。
- 原文模式是逃生口，但会绕过薄 DSL 校验；ZA8 对原文只做「非空 + 形如 `${…}`」的轻校验。

## 6. 与其他轴的交界

| 事项 | 归属 | 本波 |
|---|---|---|
| 表达式语法的服务端校验（解析 JUEL、变量存在性） | Codex（BE） | 不在 ZA11–ZA15 列表内；记为后续候选 |
| 申请表单字段值写入流程变量（让「金额 > 1000」真正可用） | BE / 产品 | 后续波次；FE 变量目录届时扩展 |
| 网关 `default` 出线读写与校验 | FE + BE | 后续波次 |
| 对齐/吸附（ZA1–ZA5） | Cursor | 无交集 |
| 并行语义（ZA11–ZA15） | Codex | 并行网关出线不设条件，已由 Z9/Z12 约定，薄 DSL 不涉及 |
