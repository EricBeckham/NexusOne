# UI 美观审核截图目录

UI10 记账。V0.1 初稿为代码 + 规范对照；UI1–UI9 抛光已合 main 后，本目录复评主路径 6 页。

## 截图状态（诚实）

本机 Cursor worktree **未**启动完整 demo（无租户登录会话 / 未跑 `npm run dev` 对真实后端），**未能**生成 PNG。下表路径为约定文件名，待本地 demo 登录后补拍。

| 文件 | 页面 | 状态 |
|---|---|---|
| `01-p1-home.png` | `/p1` | **未截**：需登录后补 |
| `02-p2-home.png` | `/p2` | **未截**：需登录后补 |
| `03-form-studio-success.png` | `/p3/forms/:id` 提交成功态 | **未截**：需有运行版本表单后补 |
| `04-export-wizard.png` | `/p3/exports/wizard` | **未截**：需租户上下文后补 |
| `05-installations.png` | `/p4/installations` | **未截**：需租户上下文后补 |
| `06-product-config-wizard.png` | `/p4/product-config-wizard` | **未截**：需平台配置读口后补 |

补拍命令提示（本地）：`cd web && npm run dev`，登录后按上表路径截全页（含页眉与主内容，勿只截局部控件）。

## 复评（UI1–UI9 后 · 代码对照）

| 页 | 初评 | 复评 | 依据卡 |
|---|---|---|---|
| `/p1` 星域首页 | 假空 Todo/Timeline/Health | **合格**：诚实 `NxEmpty` + 下一步 | UI1 |
| `/p2` 星权首页 | 同上 | **合格**：诚实 `NxEmpty` + 下一步 | UI1 |
| `/p3/forms/:id` 填报成功 | `JSON.stringify` / trace 开发味 | **合格**：`NxResult` + Toast；JSON 仅 DEV | UI2 |
| `/p3/exports/wizard` | Banner/决策 JSON 露脸 | **合格**：Banner/JSON 默认关；`NxErrorState` | UI4 |
| `/p4/installations` | 列表未统一表组件 | **半合格→合格**：空态走 `NxDataTable`；有数据保留 Q8 卡片 | UI5 |
| `/p4/product-config-wizard` | 「未接读口」 | **合格**：页眉写明就绪态来自产品定义读口 | UI7 |

关联抛光（非上表 6 页，但同波）：UI3 流程硬色、UI6 包详情空态、UI8 审计筛选项、UI9 权限说明条。

## 修订

- 2026-09-26：UI10 建账；6 页截图诚实标注未拍；复评按已合 UI1–UI9 代码结论。
