# VERIFY-P：空库平台主路径复验清单（V0.1）

> 日期：2026-09-26（Asia/Shanghai）。Worktree：`D:\Projects\NexusOne-nexusone`。  
> 目标路径：**表单模板 → 校验 → 发布 → 绑产品定义 → 配置向导就绪**。  
> **不做**：发明 Keycloak、真支付、改产品代码（本卡 docs-only）。

## 1. 环境与阻塞

| 项 | 状态 | 说明 |
|---|---|---|
| Docker / 一次性 PG 集群 | **本会话阻塞** | `docker` 不在 PATH；未能本机再起空库。 |
| 先前空库证据 | 可引用 | `docs/planning/NexusOne-J3后空库复验记账.md`（2026-09-25）：空库 Flyway→705，平台 create→validate→publish→bind **通过**。 |
| IdP | stub | 禁止本卡虚构真实 Keycloak；平台头 demo context 与 J3 记账一致。 |
| Vite / 浏览器 | 未强制 | 清单以 API + 路由为准；UI 可对照。 |

**结论（本会话）**：不能声称「今日重新跑通空库」；下列清单为**可复验步骤 + 已知断点**，供有 PG 的演示机执行。

## 2. 主路径步骤（平台）

| # | 步骤 | 路由 / API | 通过判据 | 已知断点 / 诚实备注 |
|---|---|---|---|---|
| P1 | 平台身份 | 平台 demo context / stub | 能调 `/api/v1/p1/platform/form-templates` | 无会话 → 401；非 Keycloak |
| P2 | 新建模板草稿 | `/p1/form-templates/new` · `POST …/form-templates` | 返回 `DRAFT` | Instant JDBC 类问题若复现 → 记断点（H11 曾见） |
| P3 | 校验 | 编辑器「校验」· `POST …/validate` | `valid=true` → `VALIDATED` | 失败须可锚定字段（CF-F3） |
| P4 | 发布 | 「发布」· `POST …/publish` | 得到 `version_id` / `PUBLISHED` | 未 VALIDATED → 409 `publish requires VALIDATED` |
| P5 | 绑产品定义 | `/p4/product-definitions` · `POST …/product-definitions` | 绑已发布 form version + 已发布 process key/version | 流程需租户会话读草稿；种子 `demoSimpleApproval` 可直绑（J3） |
| P6 | 绑 SKU | `PUT …/skus/{code}` | 200 | 选用未占用 SKU |
| P7 | 向导就绪 | `/p4/product-config-wizard` · `GET …/{id}/config-readiness` | 四步 form/process/sku/publish 诚实态 | PC6/PC7：未就绪勿假绿 |

## 3. 对照入口

- 表单：`/p1/form-templates`
- 流程：`/p3/workflow`（租户）
- 产品：`/p4/product-definitions`、`/p4/product-config-wizard`
- 矩阵：`docs/planning/CF01_配置台四轴入口矩阵_V0.1.md`

## 4. 本卡判定

| 项 | 判定 |
|---|---|
| 清单完备 | ✅ |
| 本机会话空库重跑 | ❌ 环境阻塞（无 Docker） |
| 引用历史空库通过 | ✅ J3 记账平台路径 |

## 5. 修订

- 2026-09-26：VERIFY-P V0.1 初稿。