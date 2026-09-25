# L12 生产 profile 硬化（2026-09-25）

可执行的演示机预检、启动、验收与回退步骤见 [P6 `prod` 部署 Checklist](../engineering/runbooks/demo-host-prod-deployment.md)。

- 基线：`main@2903e51f`（含 L11 #403）
- 前置：L6（demo seed 隔离）、L8（安全复审）、L10（密钥清单）、L11（备份恢复）。
- 结论：新增 opt-in 的 `prod` profile，并在启动时强制校验；修复了一个缺陷——单体里开启 IAM / 权限 JDBC 模式后**无法启动**。另外把两个引导令牌的比对改成常量时间。

## 1. 用法

```bash
java -jar nexus-bootstrap.jar --spring.profiles.active=prod
```

必须注入的变量（名称与含义见 [L10](./L10_密钥与配置清单.md)）：`NEXUS_DB_URL` / `NEXUS_DB_USER` / `NEXUS_DB_PASSWORD`、`NEXUS_CRYPTO_MASTER_KEY`、`NEXUS_IAM_KEYCLOAK_ISSUER` / `_CLIENT_ID` / `_CLIENT_SECRET` / `_REDIRECT_URI`。使用对象存储时再加 `NEXUS_FILE_STORAGE=s3`、`S3_*`。

**不带 `prod` profile 时，行为和原来一样**（本地开发、演示、测试不受影响），只多了一条 WARN：master key 模式是 ephemeral。

## 2. `application-prod.yaml` 设置的值

| 配置 | prod 值 | 原默认值 | 理由 |
|---|---|---|---|
| `nexus.demo.enabled` / `environment` | `false` / `prod` | `false` / `local` | 同时触发 `DemoProfileGate` 的生产拒绝逻辑 |
| `nexus.{p1,p3,studio,files,notify}.demo-context` | `false`（固定写死） | 部分可以用环境变量打开 | 开启后会信任演示用的身份请求头 |
| `nexus.studio.seed-demo-metadata` | `false` | `true` | 生产环境不需要内存元数据替身 |
| `nexus.crypto.key-source` | `env` | `ephemeral` | 否则重启后密文无法解密（见 L8 §3-A）；`env` 模式下缺少主密钥时原本就会启动失败 |
| `nexus.iam.idp` | `${NEXUS_IAM_IDP:keycloak}` | `stub` | — |
| `nexus.iam.persistence` / `nexus.permission.persistence` | `jdbc`（可以用环境变量覆盖，但守卫会拒绝 `memory`） | `memory` | 用内存存储时，身份和授权在重启后丢失 |
| `management.endpoint.health.show-details` / `show-components` | `when-authorized` | `always` | 不对匿名请求公开 DB、Redis 等组件细节 |
| `management.info.env.enabled` | `false` | `true` | 收紧 `/actuator/info` |
| `management.endpoints.web.exposure.include` | `health`（P9） | `health,info,metrics` | actuator 无鉴权，暴露即匿名 |

## 3. 启动守卫 `ProductionProfileGuard`

在创建单例 bean 时执行（Web 服务器开始接收连接之前）。只要有一条违规，就拒绝启动，并列出全部违规项。

**拒绝启动（仅在 `prod` 下检查）**：
- 同时激活了 `demo` profile
- `nexus.demo.enabled=true`，或任意 demo-context 开关为 `true`
- `nexus.crypto.key-source` 不是 `env`
- `nexus.iam.idp=stub`
- `nexus.iam.persistence` 或 `nexus.permission.persistence` 不是 `jdbc`
- `management.endpoint.health.show-details=always`

**只告警**：
- `nexus.files.storage` 不是 `s3`（文件字节在重启后丢失。内测可以接受，但要知情）
- 引导令牌仍然处于设置状态（初始化完成后应清空）
- Keycloak issuer 不是 https
- 不在 `prod` 下时：`key-source=ephemeral`

## 4. 顺带修复

1. **单体开启 IAM / 权限 JDBC 模式后无法启动**。`IdentityConfiguration` 和 `PermissionConfiguration` 在 `jdbc` 模式下会对 `classpath:db/migration` 再跑一遍自己的 Flyway（`defaultSchema=iam`）。可是单体的主 Flyway 已经建好了 `iam` 表，于是报错 `Found non-empty schema(s) "iam" but no schema history table`，**整个应用起不来**。这就是说，之前产品启动时身份只能放在内存里。
   - 修复：新增 `nexus.iam.jdbc.migrate` / `nexus.permission.jdbc.migrate`（默认 `true`，单独使用这两个模块时行为不变）。单体的 `application.yaml` 把它们设为 `false`，并让 `nexus.{iam,permission}.jdbc.*` 默认复用主数据源（`spring.datasource.*`）。两个 JDBC 仓库的 SQL 都带了 `iam.` schema 前缀，所以不依赖 search_path。
2. **引导令牌改为常量时间比对**：`IdentityAuthFilter`、`FileIsolationFilter` 从 `String.equals` 改成 `MessageDigest.isEqual`。令牌为空时入口保持关闭，这一语义不变。

## 5. 验证

- `ProductionProfileGuardTest`（6）：加固后的配置没有违规；基础默认值在 prod 下会被逐条拒绝；demo profile 和 demo 开关会被拒绝；配置类在有违规时抛出异常；三类告警；非 prod 下只报告 ephemeral。
- **`NexusOneProdProfileBootTest`（2）**：`NexusOneApplication` 以 `@ActiveProfiles("prod")` 启动，连接一个空的 PostgreSQL 15。断言守卫没有违规，身份和权限用的是 JDBC 仓库，demo 关闭，health 详情只对已授权请求开放，并且密钥在 env 主密钥下可以写入再读出。
- `NexusOneProductBootTest`（5）仍然通过：默认 profile 没有回归。
- 全量 `mvn test` 结果见 PR 说明。

## 6. 未在本卡处理（清单）

| 项 | 说明 |
|---|---|
| `nexus.permission.seed-tenant-member-grants` 默认 `true` | 会给租户成员下发默认授权，它属于产品语义，关闭后可能影响成员的默认可见性和演示路径（本卡没有验证），所以没有改。上线前由产品确认 |
| ~~actuator 端点本身是否需要鉴权~~ | **P9 已处理**：actuator 没有鉴权，所以 prod 下 `management.endpoints.web.exposure.include=health`，`metrics` / `info` 不再通过 HTTP 暴露（404）；守卫拒绝 health 以外的任何暴露。需要指标时通过内网抓取或另行加鉴权，不在本卡范围 |
| Redis 不支持口令 | 见 L10；必须部署在私有网络 |
| outbox dispatcher 没有环境变量开关 | 见 L11 §4-5 |
| 主密钥轮换 / 重新加密工具 | 见 L10、L11 |
