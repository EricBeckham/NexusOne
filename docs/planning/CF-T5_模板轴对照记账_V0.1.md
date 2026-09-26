# CF-T5：模板轴对照记账（V0.1）

> 对应 `WEB平台配置能力提升_CF波_V0.1.md` **CF-T5**。  
> 本卡 **docs-only**。已合实现卡：**CF-T1**（列表发布态徽章/密度）、**CF-T3**（编辑器顶栏版本/状态条）、**CF-T4**（互链文案统一）。  
> **CF-T2**（被产品引用计数）归 Claude，本表记为未合。

## 1. 范围与入口

| 面 | 路由 / 文件 | 本轴关注 |
|---|---|---|
| 模板列表 | `/p1/form-templates` · `FormTemplateListView` | 发布态、密度、空态 CTA、互链 |
| 模板编辑器 | `/p1/form-templates/:id` · `FormTemplateEditorView` | 顶栏发布态 + 运行版本；发布后下一步文案 |
| 互链词表 | `web/src/config/platformConfigLinks.js` | 列表 ↔ 产品定义 ↔ 配置向导 统一标签 |

**不做**：新建向导壳；模板市场；列表迁表（→ **DT1**）。

## 2. 列表对照（CF-T1 + CF-T4）

| 检查项 | 期望 | 落地 | 勾选 |
|---|---|---|---|
| 发布态可读 | 行内徽章；汇总「已发布 / 草稿」 | `template-status-*` + `template-list-summary` | [x] |
| 密度 | 紧凑表行，去开发味列名（「发布态」等人话） | CF-T1 已收口 | [x] |
| 空态 CTA | 指向配置向导 + 产品定义 | `template-empty-wizard` / `template-empty-definitions` | [x] |
| Header 互链 | 与词表一致：配置向导 / 产品定义 / 流程设计器 | `PLATFORM_CONFIG_LINKS` + 页眉 actions | [x] |
| 说明文案 | 平台模板 ≠ 租户台账 | List description 已写明 | [x] |

## 3. 编辑器顶栏对照（CF-T3）

| 检查项 | 期望 | 落地 | 勾选 |
|---|---|---|---|
| 发布态 | 人话：草稿 / 已校验 / 已发布 / 已归档 | `template-editor-statusbar` · `template-status` | [x] |
| 运行版本 | 展示 Running，不暗示可边改边写 Running | `template-running-version` | [x] |
| 脏态 | 去开发味；提示先保存再校验/发布 | `template-dirty` 文案 | [x] |
| 发布成功下一步 | 与 CF-T4 词表一致 | `NEXT_BIND_COPY` →「下一步：到产品定义绑定」 | [x] |

## 4. 互链文案矩阵（CF-T4）

| 源 → 目标 | 统一标签 | 勾选 |
|---|---|---|
| 模板列表 → 配置向导 | 配置向导（`PLATFORM_CONFIG_LINKS.wizard`） | [x] |
| 模板列表 → 产品定义 | 产品定义 | [x] |
| 模板列表 → 流程设计器 | 流程设计器 | [x] |
| 编辑器发布后 → 产品定义 | 下一步：到产品定义绑定 | [x] |
| 产品定义 / 向导 → 表单模板 | 表单模板（反向入口由 CF01/PC 既有链承接） | [x] |

**禁止复核**：未新建 `ProductConfigWizard*` 平行壳。

## 5. 卡级验收勾选

| 卡 | 交付 | 勾选 |
|---|---|---|
| **CF-T1** | 列表发布态徽章 + 密度 | [x] |
| **CF-T2** | 「被产品引用」只读计数 | [ ] Claude |
| **CF-T3** | 编辑器顶栏版本/状态条 | [x] |
| **CF-T4** | 向导/产品定义互链文案统一 | [x] |
| **CF-T5** | 本对照记账 | [x] |

## 6. 遗留

- CF-T2 引用计数（等 BE 薄读或诚实空）
- 列表 `NxDataTable` 迁移 → **DT1**（本轴记账不抢）

## 7. 修订

- 2026-09-26：CF-T5 初稿，勾选列表/顶栏/互链。
