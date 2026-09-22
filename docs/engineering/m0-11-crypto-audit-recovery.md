# M0-11 审计、密码、Secret 与恢复契约

本工作包把 P0-10 已在 Java 21 上验证的国际套件 / SM2·SM3·SM4 路径产品化为 `CryptoPort` / `SecretPort`，并交付 append-only 审计采集模型、哈希链 API、与 M0-01 Trace ID 对齐的关联助手，以及 PostgreSQL 整库 / 对象恢复的**干跑接口与 runbook**。

`poc/p0-10` 仍是参考，不是产品验收。本 PR **不**声称 G4 / G6 / G17 或目标 Linux/ARM Profile 通过。

## 运行组合

| 项 | 值 |
|---|---|
| JDK | 21 |
| Spring Boot | **4.1.1** |
| Flyway | **13.7.0**，本包窗口 **V500+**（本包占用 **V501**；**V500** 是 M0-09 执行通道） |
| PostgreSQL | **15.x**（本机验证 15.19）。不是目标 Profile |
| Provider | Bouncy Castle `bcprov-jdk18on:1.85.2`（与 P0-10 相同 JCA 路径）。**不是** GM/T 资质模块 |

凭据只来自环境变量。`NEXUS_CRYPTO_MASTER_KEY` 不得写入 Git、Issue 或日志。

## 交付

### 1. CryptoPort / SecretPort

模块 `nexus-crypto-core`。业务代码只调用 `CryptoPort`，不得直接使用 JCE / Bouncy Castle。

| 能力 | 国际套件 | 商用密码套件 |
|---|---|---|
| AEAD | AES-256-GCM | SM4-GCM |
| 摘要 | SHA-256 | SM3 |
| MAC | HMAC-SHA256 | HMAC-SM3 |
| 签名 | SHA256withECDSA P-256 | SM3withSM2 |

密文信封 `NXC1` 携带套件、`keyId`、版本、12 字节 nonce。另一套件无法解密。`Keyring.rotate()` 保留旧版本以便解开历史密文。

`SecretPort` 首期实现是 `LocalEncryptedSecretAdapter`（进程内 envelope，走 CryptoPort）。不要求 OpenBao / 云 KMS / 国密密码机。

配置（`nexus-bootstrap`）：

```bash
export NEXUS_CRYPTO_SUITE=INTERNATIONAL   # 或 GM
export NEXUS_CRYPTO_KEY_SOURCE=ephemeral  # 本地/测试默认；生产用 env
export NEXUS_CRYPTO_KEY_ID=local
export NEXUS_CRYPTO_KEY_VERSION=1
# 仅 key-source=env 时需要：
export NEXUS_CRYPTO_MASTER_KEY=   # hex，长度至少套件密钥长；勿提交
```

`GET /api/v1/meta/runtime` 回 `crypto_suite` / `crypto_key_id` / `crypto_key_version`，**不**回密钥。

固定向量见 `one.nexus.crypto.FixedVectors` 与 `FixedVectorTest`（SHA-256 / SM3 / HMAC-SHA256 RFC 4231 / NIST 全零 AES-256-GCM / GM/T 0002 SM4-ECB / GM/T 0003 SM2 样例公钥 / 在 BC 1.85.2 + Java 21 上冻结的 SM4-GCM 与 HMAC-SM3）。

### 2. 审计采集与哈希链

模块 `nexus-audit-core`。共享形状是 `nexus-common` 的 `AuditEvent` + `AuditCategory`：

`LOGIN` / `PERMISSION` / `ADMIN` / `DATA` / `APPROVAL` / `GUARD_REJECT`

工厂：`AuditCaptures`。写入前 `SensitiveRedactor` 去掉 password/token/secret 等。拒绝路径用 `appendNewTransaction`。

哈希链：`AuditHashChain` 按事件链接 `SHA-256` 或 `SM3`（随 CryptoPort 套件）。测试双：`InMemoryHashChainedAuditPort`。PostgreSQL 表：

- `platform.audit_record`（append-only，`REVOKE UPDATE, DELETE`）
- `platform.audit_chain_meta`

Flyway `V501__audit_hash_chain.sql`（V500 已由 M0-09 占用）。P1 的 `platform.audit_event` **仍是 staging**，本 PR 不把 P1/P2/隔离内核改接到新端口。

`nexus.audit.persistence=jdbc`（product default so `platform.audit_record` is written）or `memory`. Override with `NEXUS_AUDIT_PERSISTENCE`.

`JdbcHashChainedAuditPort.append` joins an ambient Spring transaction through `DataSourceUtils` on the shared DataSource (same pattern as M0-09 `JdbcOutboxStore`). The chain-head `SELECT ... FOR UPDATE`, the `audit_record` INSERT, and the `audit_chain_meta` head update do **not** commit on a private connection. A business rollback therefore rolls the hash-chain writes back with the business row. Trade-off: the single-row chain lock is held until that transaction ends, so concurrent `append` callers serialize. That keeps `chain_index` gapless and append-only; a shorter private commit would re-introduce chain/business divergence. `appendNewTransaction` still commits privately so guard-reject can survive rollback, and must not be called from a TX that already holds the lock via `append`.

G18 submit/approve/withdraw use `append`. The product close is [g18-business-path.md](../g18-business-path.md); this section only records that `append` joins the business transaction.

### 3. Trace 关联（兼容 M0-01）

`TraceIds` 增加 `current()`、`logFields()`、`correlationOrTrace()`，仍使用 `X-Trace-Id`、MDC `traceId`/`trace_id`、W3C `traceparent`。

`AuditTrace.correlate(port, traceId)` 按同一 trace 取出登录/权限/管理/数据/守卫拒绝事件。敏感字段不会进入 logFields。

业务模块尚未全部接线时，全路径“一条真实请求的全链追溯”不能验收。

### 4. 恢复

`RecoveryPort` / `DryRunRecoveryPort`：只描述或探测 `pg_dump --version`，**不执行** dump/restore，不碰对象存储。

Runbook：

- [postgres-whole-db-restore.md](./runbooks/postgres-whole-db-restore.md)
- [object-store-restore.md](./runbooks/object-store-restore.md)

`profileAccepted` 恒为 false。

## 明确延期（诚实清单）

| 项 | 原因 |
|---|---|
| 业务路径端到端哈希链（登录→权限→管理→数据→审批→守卫拒绝落在同一条真实请求上） | G18 的数据与审批事件已关闭（见 `docs/g18-business-path.md`）。本行是 G4：登录、权限、管理、守卫拒绝仍未全部接线 |
| 并发 `append` 把链锁持有到业务 TX 结束 | 与同 TX 回滚原子性的取舍；未改成更短的私有提交 |
| 全路径哈希校验作为产品门禁 | 内存与 JDBC 测试可验证**已写入**的链，不能代表真实业务闭环 |
| P1 `GET /tenants/{id}/audit-events` 切换到 M0-11 | P1 staging 表仍在；切换会改 M0-03 行为，留给接线 PR |
| 把 `IsolationAuditSink` 接到 `AuditPort` | `isolation-kernel` 现为独立 reactor；本包提供 `GuardRejectCapture` 而不依赖该模块 |
| G4 审计覆盖每个用户 / 热 90 天 + 归档 365 天 | 无业务写入与归档 Job |
| G6 整库与对象恢复演练、RPO/RTO | 仅 dry-run / 文档；未在独立实例上 restore 并校验 |
| G17 等保与国密交付 | 无三权分立产品路径、无国密 TLS 双证书、无合格 GM/T Provider、无密码机 |
| 国密 TLS（GM/T 0024）、Nginx 双证书 | P0-10 已声明未验 |
| RSA 签名、口令哈希（Keycloak PBKDF2-SM3） | 国际套件签名走 ECDSA P-256，与 P0-10 一致 |
| KMS / OpenBao / 国密密码机 SecretPort 适配器 | ARCH-007：首期不得强制独立 Secret 平台。签名密钥仍为进程内 ephemeral |
| 目标 Linux/ARM/离线包/SBOM Profile | 本机 JAR + PG 15.x 证据，不是 Profile 验收 |

## 测试

```bash
mvn -pl nexus-common,nexus-crypto-core,nexus-audit-core,nexus-bootstrap -am test
```

JDBC 审计测试默认库 `nexus_m0_11_test`（用户 `ubuntu`，peer）。没有 PostgreSQL 15 时 `JdbcAuditChainTest` 跳过；连上非 15.x 则失败。`JdbcAuditChainTransactionTest` 使用 Zonky embedded PostgreSQL 15，不依赖本机监听。

本机记录见 [m0-11-EVIDENCE.md](./m0-11-EVIDENCE.md)。
