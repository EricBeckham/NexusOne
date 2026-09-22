# M0-10 文件、缓存与站内通知

工作包：Redis CachePort、S3 File Service、站内通知模板 / Outbox 接入点。  
基线：Java 21、Spring Boot 4.1.1、PostgreSQL 15、Redis **3.2**。不依赖 Docker。

## 复用的契约

| 来源 | 本包如何使用 |
|---|---|
| `poc/p0-05` | TTL / `INCR` / `SET NX PX` + Lua 安全解锁 / 租户 Key。产品 Key 走隔离内核，不用 PoC 的 `{tenant}` hash tag。 |
| `poc/p0-06` | `file_id`、租户前缀、签名 URL 前二次授权（租户 / 前缀 / 所有者 / 资源权限）。拒绝时不得调用 S3。 |
| `modules/isolation-kernel` | **必须**调用 `CacheKeyGuard`、`FileObjectKeyGuard`（及 `SideEffectGuard`）。禁止自拼 Redis Key 或对象路径后直打后端。 |

隔离 Key / 对象路径（内核合同，本包不得改）：

```text
nexus:{env}:{tenant_id}|platform:{module}:{key}
tenants/{tenantId}/...
platform/...
```

文件相对路径沿用 P0-06 的业务分段：`{businessType}/{yyyy}/{mm}/{fileId}`，再由 `FileObjectKeyGuard` 加上 `tenants/{tenantId}/`。

## Redis 3.2 CachePort

端口：`CachePort` / `DistributedLockPort` / `CounterPort`（`nexus-common`）。  
实现：`nexus-cache-core`。`nexus.cache.mode=auto` 时本机无 Redis 则回落内存，测试与启动都不要求 Docker。

**3.2 支持并实际发出的命令：** `GET`、`SET`/`SETEX`（`EX`）、`SET NX PX`、`DEL`、`INCR`、`EXPIRE`（经 Lua）、`EVAL`、`PING`、`INFO server`。

**明确不支持 / 适配器不会发出：**

- `UNLINK`、`MEMORY *`、Modules（4.0+）
- Streams（5.0）
- ACL、原生 TLS、`HELLO` / client tracking（6.0）
- `GETEX` / `GETDEL` / `SET KEEPTTL|GET|EXAT|PXAT` / `COPY`（6.2）
- `FUNCTION` / `FCALL`、sharded pubsub（7.0）
- `HEXPIRE`（7.4）
- RedisJSON / RediSearch

锁：`SET NX PX` + 与 P0-05 相同的 compare-and-`DEL` Lua。错误 token 不能删掉新持有者。  
计数：`INCR` + 首次 `EXPIRE` 的 Lua（3.2 没有带 TTL 的 `INCR`）。  
Redis **不是**业务真相、审计或关键幂等键存储。

跨租户：以租户 A 访问 `nexus:{env}:{B}:...` 时，`CacheKeyGuard` 在 Redis 命令发出前抛 `P1.M02.014`，并写入 `IsolationAuditEvent` / 产品 `GUARD_REJECT`。

## File Service

- 元数据表 `files.file_object`（Flyway **V510**）。对象字节只在 `ObjectStoragePort`（内存或 MinIO 9.0.3，同 P0-06）。
- 上传 / 元数据 / 删除 / 签名下载。签名前再次校验租户、`FileObjectKeyGuard` 前缀、所有者、资源权限。
- 跨租户签名：`P1.M02.015`，`ObjectStoragePort.presignGet` 次数不变，守卫拒绝落 `IsolationAuditEvent`。
- 本机无 S3 时 `nexus.files.storage=memory`。设置 `S3_ENDPOINT` 等变量后可切 `s3`。

API：`POST/GET/DELETE /api/v1/files`，`POST /api/v1/files/{id}/signed-url`。

## 站内通知与 Outbox（M0-09 已合并）

- 模板：`approval.task.assigned|approved|rejected`（审批触发站内通知）。
- 通道：**仅 IN_APP**。`EMAIL` / `SMS` → `CORE.NOTIFY.0002`。
- 同步 API：`POST/GET /api/v1/notifications`，`POST /api/v1/notifications/{id}/read`。
- 投递入口：M0-09 `OutboxPort.append(OutboxAppendCommand)`，destination 固定为 `notify.inbox`。
- Handler：`InAppDeliveryHandler` 实现 `OutboxHandler`，由 `OutboxDispatcher` 在恢复租户上下文后调用。
- 产品 `nexus.execution.dispatcher-enabled=true`（`nexus-bootstrap` `application.yaml`）。G18 IT 仍关掉 dispatcher 只断言 PENDING；投递 SLO 见 `docs/m0-09-outbox-api.md` 与 `NexusOneOutboxDispatcherTest`。产品 G18 已关闭，记录在 `docs/g18-business-path.md`。

无 DataSource 的单测仍可用本模块的 `RecordingOutboxPort` / `ImmediateOutboxPort`（`nexus.notify.outbox=record|immediate`）。  
单体启动走 M0-09 `EventCoreConfiguration` **auto-config**（`@AutoConfiguration` after `DataSourceAutoConfiguration`）：`JdbcOutboxStore` 即 `OutboxPort`。不要把该类当成 component-scan `@Configuration` 再挂 `@ConditionalOnBean(DataSource)`——会在 DataSource 注册之前被跳过，`NotificationService` 启动失败。G18 IT 不得再单独声明 Outbox bean 来掩盖这一点。

## Flyway

- **V510** `files.*`，**V520** `notify.*`。
- V500 execution channel、V501 audit hash chain 已在 main；本包不占用 V500–V509。

## 测试

不要求 Docker、本机 Redis 或 S3。隔离与二次授权用内存 / Fake Redis 断言“拒绝前零次后端调用”。  
可选 live 钻：`LiveRedisIsolationDrillTest` / `LiveMinioIsolationDrillTest` / `LiveG1CacheFileDrillTest`（`./scripts/g1-live-redis-minio-drill.sh`）。服务不可达时 `assumeTrue` 跳过，不失败。见 [g1-live-redis-minio.md](./g1-live-redis-minio.md)。  
PostgreSQL 15 上的 Jdbc 仓库随 bootstrap Flyway 迁移；`NexusOnePostgresTest` 在库可达时要求版本化迁移 ≥ 10（含 V510 / V520）。
