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

部署 master key 通过环境变量注入。`NEXUS_CRYPTO_MASTER_KEY` 不得写入 Git、Issue、数据库或日志。

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

`SecretPort` 通过 `CryptoPort` envelope 加密；产品默认使用 `JdbcEncryptedSecretAdapter` 持久化密文，并由 `LocalThenEnvSecretPort` 在本地密文与只读 `EnvSecretAdapter` 间回退。不要求 OpenBao / 云 KMS / 国密密码机。跨进程解密要求部署复用稳定的 master key；ephemeral key 不支持重启恢复。S0 接线见 [s0-secret-port.md](./s0-secret-port.md)。

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

`LOGIN` / `PERMISSION` / `ADMIN` / `DATA` / `APPROVAL` / `GUARD_REJECT` / `EXPORT`

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
| 业务路径端到端哈希链（登录→权限→管理→数据→审批→守卫拒绝落在同一条真实请求上） | G18 的数据与审批已关闭。G4 把登录、权限变更、管理、守卫拒绝接到同一条 `AuditPort` 链（见 [g4-audit-coverage.md](./g4-audit-coverage.md)）。仍不是一条真实浏览器请求上的全链 |
| 并发 `append` 把链锁持有到业务 TX 结束 | 与同 TX 回滚原子性的取舍；未改成更短的私有提交 |
| 全路径哈希校验作为产品门禁 | 内存与 JDBC 测试可验证**已写入**的链，不能代表真实业务闭环 |
| P1 `GET /tenants/{id}/audit-events` 切换到 M0-11 | P1 staging 表仍在；切换会改 M0-03 行为，留给接线 PR |
| 把 `IsolationAuditSink` 接到 `AuditPort` | Product `AuditPortIsolationSink` persists `ISOLATION_REJECT` with `appendNewTransaction` (G1) and `EXPORT_ACCEPTED` with `append`. Kernel still only emits `IsolationAuditEvent`. Same-TX `append` joining is unchanged. |
| G4 审计覆盖每个用户 / 热 90 天 + 归档 365 天 | 登录/权限/管理/守卫拒绝已接线（见 [g4-audit-coverage.md](./g4-audit-coverage.md)）。Export accepted + 90-day copy Job + 365-day same-PG archive purge: [g4-export-audit-archive.md](../g4-export-audit-archive.md)。Object-store cold copy: [g4-object-cold-archive.md](../g4-object-cold-archive.md)。Hot rows are not deleted. |
| G6 整库与对象恢复演练、RPO/RTO | 产品 `RecoveryPort` 仍为 dry-run。S1 环境级演练（专用 PG 库 + 本地 MinIO）见 [g6-recovery-drill-EVIDENCE.md](./g6-recovery-drill-EVIDENCE.md)。单租户 leftover 运维演练见 [g6-single-tenant-logical-restore-EVIDENCE.md](./g6-single-tenant-logical-restore-EVIDENCE.md)。dry-run 契约 leftover 见 [g6-recovery-port-dry-run.md](../g6-recovery-port-dry-run.md)。仍 dry-run 诚实 leftover 见 [g6-recovery-port-honesty.md](../g6-recovery-port-honesty.md)（#135 dry-run 不是产品 HA / 分钟级 RPO / 交付级 HA）。dry-run / 非产品 HA 诚实 PASS/SKIP leftover 见 [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md)（扩展 #135/#164，不宣称产品 G6 / RecoveryPort / HA 关闭）。分钟级 RPO / 产品 HA / 交付级 HA / Profile / G17 仍不宣称 |
| G17 等保与国密交付 | 三权分立见 [g17-separation-of-duties.md](../g17-separation-of-duties.md)；管理员 MFA 见 [g17-admin-mfa.md](../g17-admin-mfa.md)；本地国密 TLS 双证书切片见 [g17-gm-tls-dual-cert.md](../g17-gm-tls-dual-cert.md)；Nginx/Tongsuo 或本机双栈 leftover 见 [g17-gm-tls-nginx-dual-stack.md](../g17-gm-tls-nginx-dual-stack.md)。仍无合格 GM/T Provider、无密码机、无等保/涉密交付 |
| 国密 TLS（GM/T 0024）、Nginx 双证书 | 本仓库证明 RFC 8998 TLS 1.3 SM2/SM3/SM4 + 双证书材料加载，以及本机国际/国密双栈探针。GM/T 0024 协议双证书与 Nginx 国密发行版仍未在本机验（无 Tongsuo 时诚实 SKIP） |
| RSA 签名、口令哈希（Keycloak PBKDF2-SM3） | 国际套件签名走 ECDSA P-256，与 P0-10 一致 |
| KMS / OpenBao / 国密密码机 SecretPort 适配器 | ARCH-007：首期不得强制独立 Secret 平台。签名密钥仍为进程内 ephemeral |
| 目标 Linux/ARM/离线包/SBOM Profile | 本机 JAR + PG 15.x 证据，不是 Profile 验收 |

## 测试

```bash
mvn -pl nexus-common,nexus-crypto-core,nexus-audit-core,nexus-bootstrap -am test
```

JDBC 审计测试默认库 `nexus_m0_11_test`（用户 `ubuntu`，peer）。没有 PostgreSQL 15 时 `JdbcAuditChainTest` 跳过；连上非 15.x 则失败。`JdbcAuditChainTransactionTest` 使用 Zonky embedded PostgreSQL 15，不依赖本机监听。

本机记录见 [m0-11-EVIDENCE.md](./m0-11-EVIDENCE.md)。
