# Z5 Z 波打勾记账

日期：2026-09-26。基线：`origin/main@5da0fd82`（含 Z4 #548）。Docs-only。

## 结论

Z 波 **Cursor / Claude / Codex 卡均已合入**（Z1–Z4、Z6–Z15）；本卡为收口记账。表单侧：`layout {x,y,w,h}` util → 自由画布壳 → `options.layout` 持久化（无新列，Z13 已证 schema 足够）。流程侧：exclusive/parallel 网关 FE + BE 校验薄片、扇出布局、条件边、Studio 只读预览与契约测已齐。

不宣称：完整商业化自由画布、对齐线/多选/吸附全家桶、高级 BPM 全家桶、H5、运行时引擎改造、外网 / Keycloak / `prod`。

## 合入对照（Z1–Z15）

| 卡 | 状态 | 链接 |
|---|---|---|
| Z1 | merged | https://github.com/EricBeckham/NexusOne/pull/535 |
| Z2 | merged | https://github.com/EricBeckham/NexusOne/pull/546 |
| Z3 | merged | https://github.com/EricBeckham/NexusOne/pull/547 |
| Z4 | merged | https://github.com/EricBeckham/NexusOne/pull/548 |
| Z5 | this | 本文件 |
| Z6 | merged | https://github.com/EricBeckham/NexusOne/pull/537 |
| Z7 | merged | https://github.com/EricBeckham/NexusOne/pull/539 |
| Z8 | merged | https://github.com/EricBeckham/NexusOne/pull/541 |
| Z9 | merged | https://github.com/EricBeckham/NexusOne/pull/543 |
| Z10 | merged | https://github.com/EricBeckham/NexusOne/pull/545 |
| Z11 | merged | https://github.com/EricBeckham/NexusOne/pull/536 |
| Z12 | merged | https://github.com/EricBeckham/NexusOne/pull/538 |
| Z13 | merged | https://github.com/EricBeckham/NexusOne/pull/540 |
| Z14 | merged | https://github.com/EricBeckham/NexusOne/pull/544 |
| Z15 | merged | https://github.com/EricBeckham/NexusOne/pull/542 |

任务卡原文：`docs/planning/WEB自由画布与分支并行任务卡_Z波_V0.1.md`（#534）。  
差距盘点：`docs/planning/Z1-自由画布与分支并行差距盘点.md`。  
Schema 核查：`docs/planning/Z13-布局与网关Schema核查记账.md`。

## 勾选

- [x] Z1 自由画布 + 分支/并行差距盘点
- [x] Z2 `layout {x,y,w,h}` util + 单测
- [x] Z3 `FormTemplateEditorView` 自由画布壳 MVP
- [x] Z4 自由布局读写进 `options.layout`（无新列）
- [x] Z5 本记账
- [x] Z6 exclusiveGateway 增删薄片
- [x] Z7 分支边条件编辑
- [x] Z8 gateway 扇出布局
- [x] Z9 parallelGateway fork/join 薄片
- [x] Z10 非顺序流校验 UX（S 反馈壳）
- [x] Z11 BE exclusive + 条件流校验
- [x] Z12 BE parallelGateway 校验/保存
- [x] Z13 schema 证明无需迁移
- [x] Z14 FormStudio 只读布局预览
- [x] Z15 分支/并行草稿 validate 契约测

## 截图 / 文案摘录路径

目录：`docs/planning/screenshots/z-wave/`（见同目录 `README.md`）。本卡未提交 PNG。

| 文件 | 内容 |
|---|---|
| `01-template-free-board.png` | 表单模板「自由」模式网格壳（Z3） |
| `02-template-list-fallback.png` | 切回「列表」DnD 回退（Z3） |
| `03-options-layout-roundtrip.md` | 保存/重开 `options.layout` 摘录（Z4；可选） |
| `04-process-exclusive-gateway.png` | 流程草稿排他网关（Z6/Z7） |
| `05-process-parallel-gateway.png` | 并行 fork/join 块（Z9） |
| `06-studio-layout-preview.png` | Studio 只读布局预览（Z14） |

## 建议下一波

Z 薄 MVP 已齐；若产品要加深：表单对齐/吸附、条件 DSL、运行时并行语义另开波次。本波不开 H5、不做完整商业化画布。
