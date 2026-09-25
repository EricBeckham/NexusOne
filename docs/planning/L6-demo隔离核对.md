# L6 Demo 隔离核对

- 核对时间：2026-09-25（Asia/Shanghai）
- 基线：`main@871cb18`
- 范围：`nexus.demo` 配置、`DemoProfileGate`、P1–P5 演示种子、K20 简单审批产品种子及启停脚本。

## 结论

Demo seed 不会在默认配置中启用，且显式启用后只能写回环地址数据库。未发现需要修改 Flyway、生产业务逻辑或 `outOfOrder` 的泄漏。

本卡修正一处旧文案：早期 gate 日志和 `application-demo.yaml` 仍写着“本进程 / 本卡不插入业务行”，但 P1–P5 与 K20 后续已经在同一开关下增加种子。新文案明确说明开关打开后会写本机演示数据，避免运维误判；门禁行为不变。

## 核对矩阵

| 检查项 | 证据 | 结果 |
| --- | --- | --- |
| 默认关闭 | `application.yaml` 使用 `${NEXUS_DEMO_ENABLED:false}` | PASS |
| demo profile 非默认 | 仅 `application-demo.yaml` 把 `nexus.demo.enabled` 设为 `true`；默认配置未激活 `demo` | PASS |
| 生产标记拒绝 | `DemoProfileGate.isProduction` 拒绝 `prod` / `production` | PASS |
| 远程数据库拒绝 | 只允许 `127.0.0.1`、`localhost`、`[::1]`；空值、解析失败、远程 host 均拒绝 | PASS |
| 关闭时零 seed 写入 | P1、P2、P3、P4、P5、K20 runner 都先检查 `nexus.demo.enabled` 并直接返回 | PASS |
| 开启时二次门禁 | 每个 seed 在执行 SQL 前再次调用 `DemoProfileGate.assertAllowed` | PASS |
| 产品 seed 不自动订阅 | K20 只播种模板、流程、产品、版本和 SKU，不创建租户订阅 / 安装 | PASS |
| 启用脚本 | PowerShell / bash 脚本在设置变量前拒绝生产标记和非回环 DB | PASS |
| 清理脚本 | P1–P5 清理脚本要求开关仍开启、非生产标记和回环 DB | PASS |

## 边界

- `nexus.studio.seed-demo-metadata` 是无 JDBC 仓储时的既有内存元数据替身，不受 `nexus.demo` 控制，也不向产品数据库写演示行；本卡不改其默认值。
- 关闭开关只阻止后续播种，不自动删除已经写入的行。P1–P5 使用各自 guarded 清理脚本；K20 当前应使用可重建的本机数据库，详见 [简单审批产品演示 Runbook](../engineering/runbooks/demo-simple-approval.md)。
- L6 不建立或宣称生产 profile；更广的生产 profile 硬化属于 L12。
