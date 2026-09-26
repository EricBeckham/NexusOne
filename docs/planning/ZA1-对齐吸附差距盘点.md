# ZA1 对齐 / 吸附差距盘点

对照：Z2–Z4 自由布局薄 MVP（`layout {x,y,w,h}` + 自由画布壳 + `options.layout`）；任务卡 `docs/planning/WEB自由画布加深任务卡_ZA波_V0.1.md`（#550）。  
基线：`origin/main@873daf3a`（含 Z5 #549）。范围：桌面 WEB；**不含** `web/src/mobile/**`。  
本卡 **docs-only**，不写实现。点名真实文件与 ZA2–ZA5 建议验收句。

## 总览

| 主题 | 已有（Z 合入后） | 缺口（→ ZA） | 建议落点 |
|---|---|---|---|
| 布局模型 | `fieldLayout.js`：`normalizeLayout` / `withNormalizedLayout` / `wireOptions` | 无吸附算法 | ZA2 util |
| 自由画布 | `FormTemplateEditorView` 列表↔自由切换；`freeCellFromEvent` 按 12 列 + 56px 行 **floor 取整** | 拖放到格点，**无**对兄弟边对齐；无可调网格步长/吸附阈值 | ZA3 接线 |
| 持久化 | `options.layout {x,y,w,h}` 内嵌 jsonb（Z4/Z13：无需新列） | 吸附本身是放置瞬间计算；若需「网格步长/开关」才谈元数据 | ZA4（优先无需） |
| Studio 预览 | `FormStudioView` 只读消费 `options.layout`（Z14） | 不涉及拖拽吸附 | 本轴不改 |

---

## 现状文件

| 文件 | 角色 |
|---|---|
| `web/src/views/tenants/fieldLayout.js` | 栅格整数 `layout` 规范化与 wire；**无** snap |
| `web/src/views/tenants/fieldLayout.test.js` | 缺省/非法值/wire 往返 |
| `web/src/views/tenants/FormTemplateEditorView.vue` | `freeCellFromEvent`：`floor(client / colW)`、`floor(client / 56)`；`placeAt` 直接写 x/y |
| `web/src/views/tenants/FormTemplateEditorView.test.js` | 自由壳切换、layout round-trip；**无**吸附断言 |
| `web/src/views/studio/FormStudioView.vue` | 只读网格预览；无拖放 |

### 差距（相对「薄边对齐 + 网格吸附」）

1. **无共享 snap util**：给定拖拽坐标、移动块 `{w,h}`、兄弟框列表、网格步长、阈值 → 应返回吸附后 `{x,y}`（及可选命中边信息）；当前逻辑散落在 `freeCellFromEvent` 的硬编码 floor。
2. **无边对齐**：放置时不比对其他字段的左/右/上/下边；阈值外不吸附（产品未定义）。
3. **网格仅隐含**：12 列宽 + 行高 56px 写死在视图；步长不可测、不可单测隔离。
4. **无吸附偏好元数据**：若产品只要默认步长常量，**无需**持久化；若要「每模板网格步长/开关」，优先 JSON 内嵌，否则 **停交 ZA12**。
5. **硬禁未破**：尚无对齐辅助线全家桶、多选、撤销栈、组件市场——ZA 轴须继续守住。

### 建议验收句（ZA2–ZA5）

- **ZA2**：`snapToGrid` / `snapToEdges`（或合并 `snapLayout`）纯函数；命中边、未命中、网格取整、阈值外不吸附有单测；**不改**拖放 UI / 不画辅助线。
- **ZA3**：自由模式拖放调用 snap util（边 + 网格）；坐标取整可见即可；列表 DnD 回退不变；禁止对齐线产品化。
- **ZA4**：若仅用常量步长/默认开 → 短记账「无需持久化」；若需偏好则 JSON 内嵌；必须新列则停并点名 ZA12。
- **ZA5**：`ZA5-ZA波打勾记账.md`：ZA1–ZA15 勾选 + PR + 截图路径。

---

## 明确不做（本轴 / 本波）

- 对齐辅助线全家桶、磁力参考线产品化、多选框、撤销栈、组件市场
- H5（`web/src/mobile/**`）
- 高级 BPM；条件 DSL / 并行语义（Claude / Codex 轴）
- 私自 Flyway（仅 ZA12）

## 建议三人边界（Cursor 视角）

| 轴 | 卡 | 交界 |
|---|---|---|
| Cursor 对齐/吸附 | ZA1–ZA5 | 放置瞬间 snap；元数据优先不增 |
| Claude 条件 DSL | ZA6–ZA10 | 不碰表单画布 |
| Codex 并行语义 | ZA11–ZA15 | ZA4 若误触 schema → ZA12 |
