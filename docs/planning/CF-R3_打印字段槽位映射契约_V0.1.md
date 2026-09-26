# CF-R3：打印字段→槽位映射契约（BE / 静态，V0.1）

> 对应 `WEB平台配置能力提升_CF波_V0.1.md` **CF-R3**。  
> **优先零迁移**（本卡无 Flyway）。供 Cursor **CF-R1/R2** 壳与后续 Claude 设计器对齐；**不**做拖拽设计器，**不**改 PC 打印样例页路由归属。

## 1. 交付物

| 项 | 路径 |
|---|---|
| 静态契约 JSON | `nexus-p4-market/src/main/resources/print/cf-r3-print-mapping-contract.v1.json` |
| 规划侧副本 | `docs/planning/fixtures/cf-r3-print-mapping-contract.v1.json` |
| 只读 API | `GET /api/v1/p4/print-mapping/contract` |
| 产品映射读 | `GET /api/v1/p4/product-definitions/{id}/print-mapping` |
| 产品映射写（进程内） | `PUT /api/v1/p4/product-definitions/{id}/print-mapping` |

写路径使用 **JVM 内存**（同 IM5 import job 策略），重启丢失；持久化 / 设计器 later。

## 2. 纸张与方向枚举

- `paperSizes`: `A4` / `A5` / `LETTER`（含 mm）
- `orientations`: `PORTRAIT` / `LANDSCAPE`

与 PC11 内置样例默认 `A4` + `PORTRAIT` 一致（`PrintSample` / `builtin.approval-form.a4.v1`）。

## 3. 槽位与默认字段映射

见契约 JSON：`builtinTemplate.slots` + `defaultFieldMappings`。  
未映射字段落入可重复槽 `form.fields`。

## 4. 权限

与产品定义样例读一致：平台 `SYSTEM_ADMIN`（`print-mapping.contract` / `print-mapping.read` / `print-mapping.write`）。

## 5. 非目标

- 完整拖拽打印设计器（CF-RD* / later）
- 平行向导路由；改写 Cursor 深化的 PC 打印样例页
- Flyway 表；真实租户单据落库打印