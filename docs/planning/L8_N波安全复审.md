# L8 N 波安全复审（2026-09-25）

- 基线：`main@6ce27a32`（含 K0e #398、L1 #400、L6 #399）
- 范围：N 波与安全相关的合入内容。包括 N10 IdP 密钥写入/轮转/测试连接（含 K0d）、N9/N11 元数据与页面、N4/N20 服务状态探活、N6 平台审计检索、N17 自用 API 目录。
- 方法：读代码，核对卡片「验收」和默认约束（密钥只经 SecretPort / 响应永不回显 / 写审计），并补测试。
- 本卡已修的小问题见 §2；较大的只列清单，见 §3。

## 1. 核对结果

| # | 检查点 | 结论 | 证据 |
|---|---|---|---|
| 1 | IdP 密钥只经 SecretPort 加密存储 | ✅ | `IdpSecretService` → `SecretPort.put`；产品 bean 是 `LocalThenEnvSecretPort.durableSystem`（`platform.secret_ciphertext`，JDBC 持久化） |
| 2 | 响应永不回显明文 | ✅ | `SecretStatus` / `SecretWriteResult` / `ConnectionTestResult` 只含 configured/version/时间戳/结果；`IdpConfigurationController` 无明文字段 |
| 3 | 审计记录不含密钥 | ✅ | 写入/轮转只记 `secret_version`；测试连接只记 `SUCCEEDED/FAILED`；删除只记 `operation=delete` |
| 4 | 日志不打印密钥 | ✅ | `src/main` 中没有任何 logger 调用带 secret/password/token/credential；测试连接吞掉适配器和 HTTP 诊断信息 |
| 5 | 明文生命周期 | ✅ | 请求体进来后转为 `byte[]`，用完 `Arrays.fill` 清零；K0d 之后测试器的表单编码不再生成 String 副本（请求 JSON 反序列化产生的 `String` 无法避免，已知） |
| 6 | 管理员 + MFA | ✅ | 每个操作都经 `AdminPrivilege.holdsAdminRole(..., AdminMfaPolicy)`；provider 按 `tenantId` 查询，跨租户返回 404 |
| 7 | 测试连接防内网（SSRF） | ✅（本卡补强，见 §2） | 只允许 https、无 userinfo/query/fragment；discovery 的 issuer 必须等于配置值；token 端点必须同源；不跟随重定向；响应上限 1 MiB；超时 3s/5s；**TCP 对端在 TLS 前再校验一次**（`PublicPeerSslSocketFactory`，可防 DNS rebinding）；返回的错误不含地址 |
| 8 | 前端只写不回显 | ✅ | `IdentityIntegrationsView.vue`：`type="password"`、`autocomplete="new-password"`，提交后关闭对话框；有测试覆盖 |
| 9 | N6 审计检索权限 | ✅ | `requirePlatformContext("audit.events.search", AUDIT_ADMIN)`；SQL 参数化；只查只读列 |
| 10 | N4 服务状态探活 | ✅ | `requirePlatformContext(..., SYSTEM_ADMIN)`；探活目标来自配置，不接受用户输入；统一超时 `nexus.platform.status.probe-timeout`（默认 2s，N20） |
| 11 | N17 API 目录 | ✅ | 迁移契约测试证明没有 secret 列；只读 |
| 12 | **密钥重启后是否丢失** | ⚠️ **默认配置下会丢失**，见 §3-A | `nexus.crypto.key-source` 默认 `ephemeral` |

## 2. 本卡小修（同一个 PR）

**测试连接地址过滤补强**：`OidcClientCredentialsTester.isNonPublic`。

原先的实现漏掉了下面这些情况：
- IPv4：`0.0.0.0/8`（只拦了 `0.0.0.0`）、`192.0.0.0/24`、`198.18.0.0/15`、`240.0.0.0/4`（含 `255.255.255.255`）。
- 嵌入 IPv4 的 IPv6：NAT64 `64:ff9b::/96` 现在取内嵌 v4 再判断（例如 `64:ff9b::a9fe:a9fe` 在 NAT64 网络里可以访问 `169.254.169.254` 元数据服务）；6to4 `2002::/16`、IPv4-compatible `::a.b.c.d` 同样取内嵌 v4 再判断。
- 直接拒绝：本地 NAT64 `64:ff9b:1::/48`（内嵌位置取决于前缀长度，无法可靠解析）和 Teredo `2001:0::/32`。

新增测试：`treatsSpecialPurposeIpv4AsNonPublic`、`checksTheIpv4InsideNat64SixToFourAndCompatibleIpv6`，同时断言对应的公网地址仍然放行（没有误伤）。`OidcClientCredentialsTesterTest` 7/7，`IdpSecretServiceTest` 5/5。

## 3. 清单（不在本卡修）

### A. 默认临时主密钥 → 持久化密钥在重启后不可读（高，转 **L12**）

- `CryptoConfiguration`：只要没设置 `NEXUS_CRYPTO_KEY_SOURCE=env`，每次启动都会随机生成主密钥（`Keyring.ephemeral`），而密文照常写入 `platform.secret_ciphertext`。
- 临时密钥的 `key_id` 默认也是 `local`，所以 `JdbcEncryptedSecretAdapter` 的 key 元数据校验会通过，要到 AEAD 解密才失败（`CRYPTO_INTEGRITY`）。结果是：IdP 测试连接变成 `FAILED`，其它经 SecretPort 读取的密钥也读不出来，**没有任何启动期提示**。
- 审计哈希链用的是不带密钥的 digest，**不受影响**。
- 建议（L12）：非 local/demo 环境要求 `key-source=env`，否则启动失败；即使在 local 下，如果库里已有别的密钥写下的密文，也应该在启动时响亮告警。运维上，`NEXUS_CRYPTO_MASTER_KEY` 必须进入密钥清单（L10），并写进备份/恢复步骤（L11）：只恢复数据库、没有主密钥，密文就无法解密。

### B. 请求体中的密钥 `String`（低，已知）

- `SecretBody(String clientSecret)` 由 Jackson 反序列化得到，明文会以不可清零的 `String` 形式留在堆里，直到被 GC。要彻底消除，需要自定义 `byte[]`/`char[]` 反序列化器，改动面超出小修范围。
- 当前的缓解措施：服务层立刻转成 `byte[]` 并在用完后清零；不写日志、不写审计。

### C. 出站代理下的对端校验（低）

- 如果 JVM 配置了 `https.proxyHost`，对端校验看到的是代理地址（通常是内网地址），测试连接会**失败，但方向是安全的**。上线环境如果必须走出站代理，需要单独设计（例如在代理侧做 ACL），不要为此放宽校验。

### D. 首次校验与建立连接之间的解析差（低，已覆盖）

- `safeHttpsPublicOrigin` 在连接前解析一次，建立连接后又由 `PublicPeerSslSocketFactory` 对实际对端再校验一次，所以 DNS rebinding 已被第二道校验拦住。代价是 TCP SYN 会先发到内网地址才被关闭，可以接受。

## 4. 结论

N 波的安全约束（只经 SecretPort / 不回显 / 写审计 / 管理员 + MFA / 跨租户 404 / 测试连接防内网）在代码层都成立；本卡补强了地址过滤的边界。**上线前必须处理的是 §3-A**：没有显式配置主密钥时，所有持久化密钥会在重启后失效。已交给 L12 做启动期强制。
