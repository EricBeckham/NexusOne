# XB9 权益投影读模型盘点（docs-only）

- 卡片：`docs/planning/WEB星市缺口收口任务卡_XB波_V0.1.md` · XB9（Claude）
- 基线：`main`（XB8 #574 合入后）
- 问题：订阅表「权益状态」列写死「暂无投影读模型」（`web/src/views/market/P4ManagementView.vue` 订阅表行内 `<span class="nx-muted">暂无投影读模型</span>`）。本卡盘点已有读模型，给出 XB10 / XB12 走「复用」还是「新列」。

## 1. 已有的三块

| 位置 | 是什么 | 粒度 | 存储 |
|---|---|---|---|
| `nexus-p4-market/.../CommercialEntitlementService.java`（M20）+ `CommercialEntitlementController` `GET /api/v1/p4/commercial-entitlements`、`/{subscriptionId}` | 每个订阅一条只读视图：`subscription_id / sku_code / sku_name / subscription_status / app_id / installation_id / installation_status / lines[{key, enabled, quota}]`；类注释写明 **No prices** | **按订阅** | 无（订阅 + SKU + 安装现算） |
| `nexus-p1-tenant/.../V100__p1_tenant.sql` `platform.entitlement_projection`（P1 T2-A）+ `GET /tenants/{id}/entitlement` | 租户**当前生效**的权益包：`source`（`DEFAULT_PACK` / `SUBSCRIPTION`）、`sku_code`、`subscription_id`、`payload jsonb`、`version` | **按租户（`tenant_id` 主键，一租户一行）** | 有 |
| `nexus-p4-market/.../MarketService.subscribeAndInstall` | 订阅开通时把 SKU 权益写成 `EntitlementProjection.fromSubscription(tenantId, skuCode, subscriptionId, payload, now, version+1)`，**覆盖**该租户的投影行 | — | 写 V100 |

要点：
- M20 已经是「订阅 → 权益行」的读模型，订阅表缺的只是**接线**，不是新模型。
- V100 投影是**一租户一行**、最后一次订阅开通覆盖前一次；它的 `subscription_id` 正好说明「哪一条订阅的权益是当前在生效的投影」。
- 两者都**没有**价格、账单、支付字段（本波硬禁也不加）。

## 2. 订阅表「权益状态」该显示什么（XB10 / XB11 口径）

对每条订阅，由既有数据得出、不发明：

| 字段 | 来源 | 说明 |
|---|---|---|
| `enabled_count` / `total_count` | M20 `lines`（SKU 权益定义）的 enabled 计数 / 总数 | 「N/M 项启用」 |
| `projected` | V100 投影行 `source = SUBSCRIPTION` 且 `subscription_id` 等于本订阅 | 该订阅的权益是否就是租户当前生效的投影 |
| `projection_version` / `projected_at` | 同上投影行（仅 `projected = true` 时） | 可选展示 |
| 无投影行 / 投影指向别的订阅 / 订阅已取消 | — | 诚实显示：「未投影」/「已被后续订阅覆盖」/ 按订阅状态，不写「生效」 |

FE（XB11）：删除硬编码「暂无投影读模型」；有摘要显示「N/M 项启用 · 当前生效 / 未生效」，接口失败或无数据显示「暂无」。

## 3. 结论：复用，无需新列

- **XB10**：在既有 M20 读模型上加**摘要**（按订阅），读取 V100 投影行判断 `projected`；`CommercialEntitlementService` 增加对 P1 `TenantRepository.findProjection(tenantId)` 的只读依赖（`MarketService` 已依赖同一端口）。可挂在 commercial-entitlement 视图上，或给订阅列表一个薄的附属读口；**不加价格/支付字段**。
- **XB12**：预期「**无需迁移**」——摘要全部由 `market.subscription`、SKU 权益定义、`platform.entitlement_projection` 现列算出；以 contract 测试证明（V100 列、M20 视图无价格字段）。
- 已知边界（记账，不在本波改）：V100 一租户一行，多条 ACTIVE 订阅时只有最后开通的那条被投影；其他订阅的 `projected = false` 是真实状态，不是缺数据。改成按订阅多行投影属于权益模型变更，需另开卡并走迁移专卡。

## 4. 后续卡对照

| 卡 | 依据本盘点的做法 |
|---|---|
| XB10 | 复用 M20 + V100，加按订阅摘要（计数 + `projected` + 投影版本/时间） |
| XB11 | 订阅表「权益状态」列接 XB10；删字面量「暂无投影读模型」；无数据「暂无」 |
| XB12 | contract 证明无需迁移；不新增 Flyway |
| XB14 | 摘要形状契约测（snake_case 字段、无价格字段、`projected` 语义） |
