# M0-01 工程底座

本文件是 M0-01 的模块边界、错误码约定和本机启动口径。需求/技术基准仍以仓库根目录 V1.1 文档为准。`poc/` 只作参考，不作为产品验收。

## 已锁定的运行组合（本工作包）

| 项 | 值 | 说明 |
|---|---|---|
| JDK | 21 | OpenJDK 兼容发行版 |
| Spring Boot | **4.1.1** | 2026-08-20 GA；当前稳定 minor，写入 `pom.xml` parent |
| Flyway | **13.7.0** | P0 冻结版本，覆盖 Boot BOM |
| PostgreSQL | 15.x | P0 本机收口为 15.18；应用只要求 15 |
| 运行方式 | 本机 JAR/进程 | **不使用 Docker / Compose** |
| 凭据 | 环境变量 | `NEXUS_DB_URL` / `NEXUS_DB_USER` / `NEXUS_DB_PASSWORD`，仓库不提交密码 |

未宣称：目标 Linux/ARM Profile、安装/升级/回滚/离线包、SBOM、GaussDB、容量阈值、1000 租户上限。S0+S1 采集脚本与本机 x86_64 / aarch64 SKIP 矩阵见 [linux-arm-profile-EVIDENCE.md](./linux-arm-profile-EVIDENCE.md)；安装/升级/回滚/离线/SBOM leftover 见 [linux-arm-profile-lifecycle-EVIDENCE.md](./linux-arm-profile-lifecycle-EVIDENCE.md)；**不得把该证据包当作 Profile 验收关闭。**

Flowable 7.1.0 在 P0 与 Spring Boot 3.3 PoC 组合验证。M0-08 在 `nexus-p3-studio` 以 `flowable-spring` `SpringProcessEngineConfiguration` 证明 **7.1.0 可与 Boot 4.1.1 同 JVM 进程内运行**；产品不使用 7.1.0 Boot starter（仍引用 Boot 3 Kafka auto-config）。官方 Boot 4 线为 Flowable 8.0.x，本工作包不改 P0 钉扎。

## 模块地图

Maven 多模块对应技术框架 §2 建议结构。业务模块禁止跨模块直查对方私有表。

```
nexusone (pom)
├─ nexus-common              共享契约（本包定义，全体依赖）
├─ nexus-bootstrap           可运行 JAR：Flyway、健康、日志、Trace、/api/v1
├─ nexus-data-core           M0-02：Query AST / Guard（现为 QueryExecutor 端口）
├─ nexus-permission-core     M0-05
├─ nexus-expression-core     受限表达式（后续）
├─ nexus-event-core          M0-09 Outbox
├─ nexus-task-core           M0-09 Quartz / Task Queue
├─ nexus-cache-core          M0-10 Redis 3.2 CachePort
├─ nexus-file-core           M0-10
├─ nexus-notification-core   M0-10
├─ nexus-audit-core          M0-11
├─ nexus-crypto-core         M0-11 CryptoPort / SecretPort
├─ nexus-p1-tenant           M0-03
├─ nexus-p2-iam              M0-04
├─ nexus-p3-studio           M0-06/07 台账与表单；M0-08 顺序审批 / 待办
├─ nexus-p4-market           M0 范围外，仅占位
└─ nexus-p5-connect          M0 范围外，仅占位
```

Java 包前缀：`one.nexus.*`。`nexus-bootstrap` 扫描 `one.nexus`，后续模块放入对应包即可被单体装配，不必改启动类。

### 给并行工作包的插接约定

| 工作包 | 依赖 | 不要改 |
|---|---|---|
| M0-02 | `TenantContext` / `PlatformContext` / `RequestContext` / `QueryExecutor` | 不要在业务模块开 JDBC |
| M0-03 | 新增 Flyway **V100–V199** 于 `nexus-bootstrap/src/main/resources/db/migration` 或本模块 `db/migration` 并加入 `spring.flyway.locations` | 不要建 `business_data` 台账表 |
| M0-04 | 契约 `Actor`；Flyway **V200–V299** 写入 `iam` schema | 不要把 Keycloak 表放进 Flyway |
| M0-06 | `platform.table_registry`；动态 DDL 只走 SchemaService | **禁止** Flyway 管理 `l_*` / `a_*` |
| M0-07 | `LedgerMetadataPort`（逻辑 ledger/field code）；Flyway **V530–V549** 表单元数据 | 不要绑定物理列；不要占用 V500/V501/V510/V520 |
| M0-08 | 引擎自管 `flowable` schema | 不要把 Flowable 表写入 Flyway |
| M0-09 | `DomainEvent` / `OutboxMessage`；Flyway 可加静态 Outbox 表 | 版本号避开 001–099 |
| M0-11 | `CryptoPort` / `AuditPort`；Flyway **V501** 写入 `platform.audit_record`（窗口 V500+；V500 已由 M0-09 占用） | 不要改 P1 staging `platform.audit_event`；不要声称 G6/Profile |

Flyway 版本窗：

- `V001–V099`：M0-01 平台静态底座（已占用 001–003）
- `V100–V199`：M0-03 P1
- `V200–V299`：M0-04 P2
- `V300–V399`：M0-05 权限静态表
- `V400–V499`：M0-06 P3 **台账/字段元数据**（不是台账物理表）
- `V500+`：其余静态表（M0-09 **V500** 执行通道；M0-11 **V501** 审计；M0-10 **V510** 文件 / **V520** 通知；M0-07 **V530–V549** 表单元数据；**M0-08 V550–V599** 流程元数据）

历史表：`platform.flyway_schema_history`。

## 公共契约（`nexus-common`）

开始写产品代码前约定的跨模块类型（实施任务拆解 §3）：

- `TenantContext(tenantId, tenantCode)` — 仅服务端签发
- `PlatformContext(path, actor, reason, traceId)` — 白名单路径，必须审计
- `Actor(subjectId, type, displayName)`
- `RequestContext` — HTTP/Job/Outbox 入口设置与清理
- `ErrorCode` / `NexusException` / `ApiError`
- `UuidV7`、`Utc`
- `TraceIds`（`X-Trace-Id`，兼容 W3C `traceparent`）
- 占位：`PermissionDecision`、`PolicySnapshot`、`DomainEvent`、`FileRef`、`OutboxMessage`
- M0-11：`AuditEvent` / `AuditCategory`；实现在 `nexus-crypto-core`、`nexus-audit-core`

ID：对外主键 UUIDv7，不用数据库自增。时间：UTC `timestamptz`。

## API 与错误码

- 路径前缀：`/api/v{n}/...`。向后兼容不升版本。
- 失败响应固定四字段：`code` / `message` / `trace_id` / `details`。
- 错误码：`产品.模块.编号`，编号四位十进制。

本骨架已登记：

| code | HTTP | 含义 |
|---|---|---|
| `CORE.API.0001` | 400 | 请求不合法 |
| `CORE.API.0002` | 404 | 资源不存在 |
| `CORE.API.0003` | 409 | 冲突 |
| `CORE.API.0099` | 500 | 未分类内部错误 |
| `CORE.CTX.0001` | 403 | 缺少 TenantContext |
| `CORE.CTX.0002` | 403 | 非法 PlatformContext |
| `CORE.CTX.0003` | 403 | 缺少 Actor |

产品模块从 `P1.TENANT.0001`、`P2.IAM.0001`、`P3.STUDIO.0001`、`CORE.GUARD.0001` 起自行编号，**不得重用已发布编号**。

成功读模型本阶段不强制包一层 envelope。健康检查走 Actuator：`/actuator/health`、`/actuator/health/liveness`、`/actuator/health/readiness`。运行指纹：`GET /api/v1/meta/runtime`。

## 可观测性

- 控制台日志：Spring Boot ECS JSON（`logging.structured.format.console=ecs`）
- Trace ID：`TraceIdFilter` 写入 MDC `traceId` / `trace_id`、响应头 `X-Trace-Id`、错误体 `trace_id`
- Health / Info / Metrics：Actuator（不绑定外部 APM；OpenTelemetry 导出器留给后续，不作为启动依赖）

## 本机启动

创建空库后（账号不要把密码写进 Git）：

```bash
export NEXUS_DB_URL='jdbc:postgresql://127.0.0.1:5432/nexusone'
export NEXUS_DB_USER='nexus'
export NEXUS_DB_PASSWORD='...'   # 本机环境变量
mvn -q -DskipTests package
java -jar nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar
```

或 `scripts/m0-01-smoke.sh` / `scripts/m0-01-smoke.ps1`。

验证：

- `GET /actuator/health` → `UP`，含 db
- `GET /api/v1/meta/runtime` 带回 `schema_manager=flyway_static_only` 与 `trace_id`
- `platform.flyway_schema_history` 五条成功 **SQL** 迁移（V001–V003 底座 + V100–V101 P1；另有一条 Flyway schema creation 记录）
- `business_data` 下无表

## 明确不在本 PR 范围

- M0-02 隔离实现、CI 禁 JDBC 门禁
- M0-03 租户生命周期 / 平台租户 / Quota
- M0-04 Keycloak 与人员组织
- Vue 3 壳（不阻塞后端 JAR 退出条件，交给 M0-12）
- Compose / systemd 安装包、升级回滚、离线、SBOM
- Redis、MinIO、Quartz、Flowable 运行时
- GaussDB、信创 OS/CPU 矩阵
- 容量阈值冻结
