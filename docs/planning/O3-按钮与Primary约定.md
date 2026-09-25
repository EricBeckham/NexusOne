# O3 按钮高度与 Primary 约定

对照规范 §9。实现：`web/src/styles/tokens.css`。

## 高度

| Size | Token | 高度 |
|---|---|---:|
| Small | `--nx-control-height-sm` | 28px |
| Default | `--nx-control-height` | **36px**（规范 34/36） |
| Large | `--nx-control-height-lg` | 40px |

共享类：

- 兼容：`.primary` / `.ghost` / `.danger`（默认高度已钉 36px）
- 推荐新代码：`.nx-btn` + `.nx-btn--primary|default|danger|text`；尺寸 `.nx-btn--sm` / `.nx-btn--lg`（或 `.small` / `.large` 挂在旧类上）

## 一个操作区一个 Primary

1. **操作区**：同一视觉分组内的按钮簇（页头 actions、表单底栏、卡片 footer、对话框 footer、表格行操作除外）。
2. **规则**：每个操作区最多一个 `.primary` / `.nx-btn--primary`；其余用 `.ghost` / `.nx-btn--default` / `.nx-btn--text`；破坏性动作用 `.danger`。
3. **表格行**：优先 Text / ghost；超过三个动作收进 More（后续组件波）。
4. **已知待改**：已开通卡「发起」+「提交升级」双 Primary → **O11**（Claude）。本卡只钉高度与约定，不改业务布局。

## 使用示例

```html
<div class="actions">
  <button type="button" class="ghost">取消</button>
  <button type="submit" class="primary">保存</button>
</div>

<!-- 等价新类 -->
<button type="button" class="nx-btn nx-btn--default">取消</button>
<button type="submit" class="nx-btn nx-btn--primary">保存</button>
```
