# ZA4 吸附元数据持久化结论

对照：ZA2 `fieldSnap.js` + ZA3 `FormTemplateEditorView` 薄接线；任务卡 `docs/planning/WEB自由画布加深任务卡_ZA波_V0.1.md`（#550）。  
基线：含 ZA3 合入后的 `origin/main`。本卡 **docs-only 短记账**。

## 结论

**无需持久化；无需新 DB 列；不停交 ZA12。**

吸附是放置瞬间的纯计算：

| 项 | 现状 | 是否要存 |
|---|---|---|
| 网格步长 | `snapLayout(..., { gridX: 1, gridY: 1 })` 常量 | 否（默认格点已与 `layout` 整数栅格一致） |
| 边对齐阈值 | `threshold: 1` 常量 | 否 |
| 开关 | 自由模式拖放即用；列表模式不走 snap | 否 |
| 字段坐标 | 仍只写 `options.layout {x,y,w,h}`（Z4 已齐） | 已有；snap 不增字段 |

若日后要「每模板网格步长 / 吸附开关」，优先模板 JSON / `options` 内嵌，再议 ZA12。本波薄片不引入该偏好。

## 验收摘录

- ZA2：`npx vitest run src/views/tenants/fieldSnap.test.js`
- ZA3：`npx vitest run src/views/tenants/FormTemplateEditorView.test.js`（含 sibling-edge snap 用例）

## 明确不做

- Flyway / 新列
- 对齐线全家桶偏好存储
- 改无关业务 API
