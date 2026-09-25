# H9 演示 SKU 绑定核查记账

日期：2026-09-25。基线：`origin/main@60f167313a11`（H0 #423）；执行分支：`codex/h9-sku-binding-evidence`。仅核查空库数据和调用结果，未改业务代码。

## 空库核查

在本 worktree 内用 JDK 21、Maven 3.9.11、隔离 Maven 仓库和临时目录，运行现有 `DemoSeedPostgresBootTest`。测试创建一次性 PostgreSQL 15.18 空库，设置 `nexus.demo.enabled=true`、`nexus.demo.environment=local`，完成 Flyway 与演示种子后执行以下断言；没有连接已有 `nexusone` 库。

| 核查项 | 查询或操作 | 结果 |
|---|---|---|
| 简单审批 SKU 绑定 | `SELECT count(*) FROM market.sku_product_binding WHERE sku_code = 'demo.simple-approval'` | **1** |
| 对应已发布定义 | `SELECT count(*) FROM market.product_definition WHERE id = '01900000-0000-7000-8000-000000000505' AND status = 'PUBLISHED'` | **1** |
| 种子幂等 | 重跑简单审批种子两次后再次检查上述两项 | 仍各 **1** |
| 真实编码订阅 | 林晓的 stub 身份与租户上下文，`POST /api/v1/p4/tenants/{tenantId}/subscriptions`，`sku_code=demo.simple-approval`、种子应用版本 ID | **201 Created** |

测试报告：`DemoSeedPostgresBootTest`，1 test、0 failures、0 errors、0 skipped；Maven 退出码 0。嵌入式数据库在测试结束时关闭。收尾时 Surefire 等待 fork 超过 30 秒并清理进程，Spring shutdown hook 因数据库先关闭出现 Hikari 连接警告；测试断言和 Maven 结果仍为通过。

## 对 G5 断点的解释边界

当前种子同时插入 `market.product_definition` 与 `market.sku_product_binding`。订阅服务按传入的 `sku_code` 查询该绑定；本次空库测试用真实编码可返回 201，因此 **空库缺少绑定行不是本次可复现的根因**。

G5 浏览器目录曾显示 `订阅开通 undefined`，其点击请求返回 `404 catalog SKU has no product definition`。当前目录模型为 `CatalogSku(sku, productDefinition)`，即 SKU 字段在 `sku.code` / `sku.name` 内；G5 所用前端读取顶层 `sku.code` / `sku.name`。这提供了请求编码可能未正确传入的证据，但 H9 未截获 G5 当时的请求体，**不能把推断当成已证实的请求值**。H2/H5 应核对目录 JSON 与浏览器实际提交的 `sku_code`，并做契约修正；H1 可据此避免把“缺绑定”当成已确认事实。

H9 不代表浏览器订阅主路径已通；G5 的后续发起、审批和我的申请仍未完成。
