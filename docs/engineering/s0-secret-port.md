# S0 SecretPort（CORE-SEC-001 / ARCH-007）

G18 已关闭。本包是 S0 凭据访问，**不重做** P0 / M0-11 CryptoPort 或审计哈希链。

业务代码只通过 `SecretPort` 取凭据。产品默认先查 PostgreSQL 中由 `CryptoPort` 加密的密文，再回退到环境变量。**不引入 OpenBao，不引入云 KMS / 国密 KMS / 密码机。** 这些适配器仍是后置可选项，启动不得依赖它们。

## 运行组合

| 项 | 值 |
|---|---|
| JDK | 21 |
| Spring Boot | **4.1.1** |
| PostgreSQL | **15.x** |
| 运行 | 本机 JAR，无 Docker |

## API

`one.nexus.crypto.SecretPort`：`put` / `get` / `find` / `delete` / `suite` / `backend` / `getUtf8`。

| 实现 | 角色 |
|---|---|
| `JdbcEncryptedSecretAdapter` | 持久化 `CryptoPort` envelope；表中仅有密文、key id 与版本元数据 |
| `LocalEncryptedSecretAdapter` | 进程内 envelope（测试/临时场景，走 CryptoPort） |
| `EnvSecretAdapter` | 只读，从环境变量取值 |
| `LocalThenEnvSecretPort` | **产品默认**：先 JDBC 密文存储，再 env |

`put` 加密后 upsert `platform.secret_ciphertext`，版本原子递增；轮转覆盖当前 envelope，旧密文不留版本历史。`delete` 删除 JDBC 中对应的本地密文，但只删本地项，不删除同名环境变量回退值。环境适配器对写入返回 `CORE.SECRET.0003`。

跨进程重启读取需部署使用稳定且受保护的 `NEXUS_CRYPTO_MASTER_KEY`（`nexus.crypto.key-source=env`）；master key 不得写入数据库。临时/ephemeral CryptoPort key 只适用于本机开发或测试，重启后不能解密此前密文。数据库持久化的是应用层密文，不代表 OpenBao/KMS 或 HSM。

逻辑名见 `SecretNames`：

| 逻辑名 | 环境变量 |
|---|---|
| `datasource.password` | `NEXUS_DB_PASSWORD` |
| `s3.access-key` | `S3_ACCESS_KEY` |
| `s3.secret-key` | `S3_SECRET_KEY` |
| `iam.keycloak.client-secret` | `NEXUS_KEYCLOAK_CLIENT_SECRET` |
| `crypto.master-key` | `NEXUS_CRYPTO_MASTER_KEY` |
| `iam.platform-bootstrap-token` | `NEXUS_PLATFORM_BOOTSTRAP_TOKEN` |

也可用原始环境变量名作为别名。空白值视为不存在。

明文不得进入 Git、普通 Spring 配置或业务表。`GET /api/v1/meta/runtime` 回 `secret_backend=jdbc+env`、`secret_openbao=false`、`secret_kms=false`，**不**回密钥。

## 业务接线

File Service 在 `nexus.files.storage=s3` 时通过 `MinioObjectStorage.fromSecretPort` 取 `s3.access-key` / `s3.secret-key`。`FileProperties` 只保留 endpoint / bucket，不再绑定 access/secret key。

平台 DataSource 仍由 Spring Boot 在启动期绑定 `NEXUS_DB_*`（基础设施，不是业务 Connector）。业务侧凭据不得再读 `FileProperties` 或直接 `System.getenv`。

## 明确不在本包

| 项 | 原因 |
|---|---|
| OpenBao / 云 KMS / 国密密码机 Adapter | ARCH-007：首期不得强制独立 Secret 平台 |
| CORE-SEC-003 租户 `encrypted_payload` / `secret_ref` | S1 租户外部凭据，未做 |
| 把 JDBC DataSource 改成运行期 SecretPort | Spring 启动期绑定；本包不重做 M0-01 |
| OS `System.getenv()` rotate-without-restart | Java 21 进程启动快照；诚实 SKIP。可变 env map 下次 get 可见。见 [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md) |
| 重做 CryptoPort / 审计链 / G4 / G6 / G17 | P0/M0 已交付，本包不重开 |

## 测试

```bash
mvn -pl nexus-common,nexus-crypto-core,nexus-file-core test
mvn -pl nexus-bootstrap -am -DskipTests install
mvn -pl nexus-bootstrap test
```

`-am test` on bootstrap also runs `nexus-p1-tenant` `TenantModuleIT`, which requires `NEXUS_DB_PASSWORD` and a live PostgreSQL 15. That IT is M0-03, not this package. Evidence: [s0-secret-port-EVIDENCE.md](./s0-secret-port-EVIDENCE.md). Env/process rotate-without-restart leftover: [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md). SecretPort rotate / getenv / LocalThenEnv leftover honesty beyond #223: [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md).
