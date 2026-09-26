# ZA5 ZA 波打勾记账

日期：2026-09-26。基线：`origin/main`（含 ZA4 #556）。Docs-only。

## 结论

ZA 波 **Cursor 轴（对齐/吸附）已合入**（ZA1–ZA4）；本卡收口记账。Snap = 薄边对齐 + 网格，常量步长，**无需**新元数据/列（ZA4）。  
Claude 轴已见 ZA6/ZA7 合入；ZA8–ZA10 与 Codex 轴（ZA11–ZA15）收口时按 PR 表勾选。规划卡 #550 若仍 OPEN，以合入后路径为准。

不宣称：对齐线全家桶、多选、撤销栈、组件市场、规则引擎、Flowable 运行时改造、H5、外网 / Keycloak / `prod`。

## 合入对照（ZA1–ZA15）

| 卡 | 状态 | 链接 |
|---|---|---|
| ZA1 | merged | https://github.com/EricBeckham/NexusOne/pull/551 |
| ZA2 | merged | https://github.com/EricBeckham/NexusOne/pull/553 |
| ZA3 | merged | https://github.com/EricBeckham/NexusOne/pull/555 |
| ZA4 | merged | https://github.com/EricBeckham/NexusOne/pull/556 |
| ZA5 | this | 本文件 |
| ZA6 | merged | https://github.com/EricBeckham/NexusOne/pull/552 |
| ZA7 | merged | https://github.com/EricBeckham/NexusOne/pull/554 |
| ZA8 | pending | 条件 DSL 校验 UX |
| ZA9 | pending | 选中边条件摘要/面板薄整理 |
| ZA10 | pending | Claude 轴打勾记账 |
| ZA11 | pending | BE parallelGateway 语义校验加深 |
| ZA12 | pending | schema/无需迁移专卡（ZA4 已证吸附侧无需） |
| ZA13 | pending | 并行只读预览/说明 |
| ZA14 | pending | validate 契约测加深 |
| ZA15 | pending | Codex 轴打勾记账 |

任务卡原文：`docs/planning/WEB自由画布加深任务卡_ZA波_V0.1.md`（#550）。  
差距盘点：`docs/planning/ZA1-对齐吸附差距盘点.md`。  
吸附持久化：`docs/planning/ZA4-吸附无需持久化记账.md`。

## 勾选

- [x] ZA1 对齐/吸附差距盘点
- [x] ZA2 snap util（边 + 网格）+ 单测
- [x] ZA3 FormTemplateEditorView 薄接线
- [x] ZA4 无需持久化记账
- [x] ZA5 本记账
- [x] ZA6 条件 DSL 差距盘点
- [x] ZA7 薄结构化条件编辑器
- [ ] ZA8 条件校验 UX
- [ ] ZA9 条件面板薄整理
- [ ] ZA10 Claude 轴记账
- [ ] ZA11 BE 并行语义校验
- [ ] ZA12 schema / 无需迁移
- [ ] ZA13 并行只读说明
- [ ] ZA14 validate 契约加深
- [ ] ZA15 Codex 轴记账

## 截图 / 文案摘录路径

目录：`docs/planning/screenshots/za-wave/`（见同目录 `README.md`）。本卡未提交 PNG。

| 文件 | 内容 |
|---|---|
| `01-free-snap-edge.png` | 自由画布拖近邻边吸附（ZA3） |
| `02-free-snap-grid.png` | 格点取整可见（ZA3） |
| `03-list-fallback.png` | 列表 DnD 回退仍可用 |
| `copy-za4-no-persist.md` | ZA4「无需持久化」摘录（可选） |

## 建议下一波

先等 Claude / Codex 轴合入后更新本表勾选；加深商业化画布或高级 BPM 另开波。本波不开 H5。
