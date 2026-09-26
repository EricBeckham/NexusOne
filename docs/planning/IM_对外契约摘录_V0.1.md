# 星枢 NexusOne · 产品配置包对外契约摘录（IM）V0.1

> 摘自 IM 波；供外部系统只读对接。完整 schema 见 `docs/planning/fixtures/product-config-1.0/`。

## 包格式

- `manifest.format` = `nexusone.product-config`
- `manifest.formatVersion` = `1.0`
- `manifest.checksum` = `sha256:<hex>`（对去掉 checksum 后的规范 JSON）

必填顶层键：`manifest` · `formTemplates` · `processDefinitions` · `productDefinition` · `skuBindings`  
可选：`printTemplates`（可空数组）· `printSupport`（如 `config+preview-later`）

## HTTP（平台管理员）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/p4/product-definitions/{id}/export` | 下载 format 1.0 JSON 包 |
| POST | `/api/v1/p4/product-definitions/import` | body: `{ packageBody, conflictStrategy }`；头 `Idempotency-Key` 可选；返回 `{ importJobId, status }` |
| GET | `/api/v1/p4/product-definitions/import-jobs/{id}` | 作业状态 / 结果计数 |

`conflictStrategy`：`skip`（默认）| `new-version` | `fail`  
**不**静默覆盖已发布产品定义。

## 非目标

真支付、完整打印设计器、开通/安装实例、租户运行时数据。