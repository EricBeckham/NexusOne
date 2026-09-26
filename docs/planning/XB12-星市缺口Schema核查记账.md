# XB12 星市缺口 Schema / 契约核查记账（无需迁移）

- 卡片：`docs/planning/WEB星市缺口收口任务卡_XB波_V0.1.md` · XB12（Claude；本波唯一允许加 Flyway 的卡）
- 前序：`docs/planning/XB9-权益投影读模型盘点.md`
- 结论：**XB 波不需要迁移**。未新增迁移文件；全局最高版本仍为 **V705**（全仓 35 个），`outOfOrder` 未动。

## 逐项核查

| 卡 | 读什么 | 需要新列 / 新表？ |
|---|---|---|
| XB6 升级步骤 | `market.installation`（无步骤列）；步骤目录是代码常量，状态恒为 UNKNOWN（无记录） | 否 |
| XB7 升级 facts | `market.app_version`（只有 release notes）、回滚 = 既有版本切换 | 否 |
| XB10 权益摘要 | `market.subscription`（id / tenant_id / sku_code / status / started_at / ended_at）、`market.sku.entitlements jsonb`、`platform.entitlement_projection`（V100：`tenant_id` 主键、`source`、`subscription_id`、`projected_at`、`version`） | 否 |
| XB1–XB5 运营分析（Cursor） | 既有 list API | 否 |

## 契约测试

`nexus-p4-market/src/test/java/one/nexus/p4/market/XbWaveSchemaContractTest.java`（4 tests, 0 failures）：

1. V100 `platform.entitlement_projection` 仍是一租户一行，且有 XB10 读取的 `source / sku_code / subscription_id / projected_at / payload / version`，`source` 仅 `DEFAULT_PACK` / `SUBSCRIPTION`。
2. V600 `market.subscription`、`market.sku.entitlements jsonb`、`market.installation.version_id` 仍在，`market.installation` 无步骤列。
3. 全部模块迁移都没有 `upgrade_step* / upgrade_fact* / upgrade_run*` 存储；星市迁移（V600 / V601 / V705）与 `entitlement_projection` 没有价格、金额、发票、优惠券、计费、支付、费用列（P1 `quota_idempotency.amount` 是配额用量，不在检查范围）。
4. 全局最高迁移版本 = 705。

## 若将来确需迁移

例如真升级编排要持久化步骤、或权益改为按订阅多行投影：由专门 schema 卡新增，取下一全局版本 **V706**，`outOfOrder` 保持关闭，不夹带功能 UI；届时本契约测试会失败并提示更新。
