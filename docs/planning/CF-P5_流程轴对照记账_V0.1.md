# CF-P5：流程轴对照记账（V0.1）

> 对应 `WEB平台配置能力提升_CF波_V0.1.md` **CF-P5**。  
> 本卡 **docs-only**。已合实现：**CF-P1** #626、**CF-P2** #627、**CF-P3** #628；共用壳 **CF02** `NxPropertyPanel`。  
> **CF-P4**（硬色/禁用态，可与 UI3 合并）归 Cursor — 本表记遗留，不抢做。

## 1. 范围与入口

| 面 | 路由 / 文件 | 本轴关注 |
|---|---|---|
| 流程设计器 | `/p3/workflow` · `ProcessViewerView` | 右侧属性分区、校验定位、发布确认诚实文案 |
| 共用属性壳 | `NxPropertyPanel` | CF-P1 接线；面板 `v-show` 保编辑态 DOM |

**不做**：会签/或签/加签转交/子流程产品化；重写画布；完整 BPM 引擎产品。

## 2. 属性分区对照（CF-P1）

| 检查项 | 期望 | 落地 | 勾选 |
|---|---|---|---|
| 分区 Tab | 节点基本 / 审批人 / 条件摘要 | `process-property-sections` + `PROP_TABS` | [x] |
| 审批人槽 | userTask 可编辑；其它节点诚实「无需指定」 | `#assignee` 槽 | [x] |
| 条件摘要 | 网关分支可点选定位连线 | `#condition` + `branch-summary` | [x] |
| 连线属性 | 选中 flow 时仍用原连线面板（非第二套向导） | `flow-properties` | [x] |

## 3. 校验定位对照（CF-P2）

| 检查项 | 期望 | 落地 | 勾选 |
|---|---|---|---|
| 列表可点 | 问题项点击选中节点/连线 | `validation-item` → `selectNode` / `selectFlow` | [x] |
| 自动定位 | 校验失败后聚焦首条可定位 finding | validateDraft else 分支 | [x] |
| 滚入视口 | 图节点带 `data-node-id`，select 后 scrollIntoView | CF-P2 | [x] |
| 条件问题 | 本地条件格式问题同样可点 | `condition-problem` | [x] |

## 4. 发布确认对照（CF-P3）

| 检查项 | 期望 | 落地 | 勾选 |
|---|---|---|---|
| 版本提示 | 展示当前已发布版本或「尚无」 | `process-publish-version-hint` | [x] |
| 产品引用 | BE 无计数则诚实「引用数暂不可用」 | `process-publish-ref-hint` | [x] |
| 运行中实例 | 明示不迁移 | 确认框正文既有句 | [x] |

## 5. UI3 / CF-P4 遗留（不本卡实施）

| 遗留 | 现状 | 归属 |
|---|---|---|
| 硬编码成功色/白底 | ProcessViewer 有 UI3 回归测「用 design tokens」；CF-P4 仍可扫残余硬色与禁用说明 | **Cursor CF-P4 / UI3** |
| 禁用态人话 | 发布按钮依赖 `canPublish`；文案深度归 CF-P4 | Cursor |
| 截图归档 | 本卡以对照表代替截图仓；演示机有图可后补链 | 可选 follow-up |

## 6. 卡级验收勾选

| 卡 | 交付 | PR | 勾选 |
|---|---|---|---|
| CF-P1 | 属性分区壳 | [#626](https://github.com/EricBeckham/NexusOne/pull/626) | [x] |
| CF-P2 | 校验点击定位 | [#627](https://github.com/EricBeckham/NexusOne/pull/627) | [x] |
| CF-P3 | 发布确认诚实提示 | [#628](https://github.com/EricBeckham/NexusOne/pull/628) | [x] |
| CF-P4 | 硬色/禁用收口 | — | [ ] Cursor |
| CF-P5 | 本对照记账 | （本 PR） | [x] |

## 7. 修订

- 2026-09-26：CF-P5 V0.1。对照 P1–P3；UI3/P4 记遗留。