# 演示机 `prod` 部署 Checklist

适用范围：NexusOne 演示 / 内测机，不是公网商用首发、HA 或合规验收。本文把 [L10 密钥与配置清单](../../planning/L10_密钥与配置清单.md) 和 [L12 生产 profile 硬化](../../planning/L12_生产profile硬化.md) 收成一次可照做的部署流程。

> 演示机使用 `prod` profile，**不是**本地 `demo` profile。本文只写变量名和检查方法，不包含任何真实密码、token、client secret 或主密钥。真实值必须由部署环境的密钥管理器或权限为仅服务账号可读的环境文件注入。

## 0. 发布信息

- [ ] 记录待发布的 Git SHA、构建时间和操作者；只部署已合入 `main` 的 SHA。
- [ ] 确认 JDK 21；构建机另需 Maven 3.9.x。
- [ ] 为应用创建非 root 服务账号；工作目录和 JAR 只允许发布账号写、运行账号读。
- [ ] PostgreSQL 使用 15.x；Keycloak、Redis、S3/MinIO、反向代理与应用之间走受控私网。
- [ ] 演示机已有 HTTPS 域名和证书；只由反向代理对外暴露 443，不直接暴露应用 `8080`、PostgreSQL、Redis、MinIO 管理端口。

构建产物：

```text
nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar
```

在受控构建机从目标 SHA 构建并保存校验和；不要在演示机上临时修改源码：

```bash
mvn -q -DskipTests package
sha256sum nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar
```

- [ ] 把 JAR 与 SHA-256 一起传入演示机，传输后重新计算并比对。
- [ ] 不把 `.env`、密钥文件、数据库 dump 或证书私钥打进 JAR / 镜像层 / 发布压缩包。

## 1. 外部依赖准备

### PostgreSQL

- [ ] 创建专用数据库、专用登录角色和独立口令；不要复用超级用户。
- [ ] 首次启动所用角色必须能连接数据库并让主 Flyway 创建 / 更新仓库所需 schema；迁移完成后再按运维策略收紧权限。
- [ ] 连接地址使用 `NEXUS_DB_URL`，账号使用 `NEXUS_DB_USER`，口令只通过 `NEXUS_DB_PASSWORD` 注入。
- [ ] 数据库只接受演示机私网来源；启用 TLS 时按 PostgreSQL JDBC 参数配置证书校验。
- [ ] 部署前确认有可恢复备份，并把 `NEXUS_CRYPTO_MASTER_KEY` 与数据库备份分开保管。恢复演练按 [L11](../../planning/L11_备份恢复步骤.md) 执行。

IAM / permission 默认复用主数据源，不需要重复口令。只有明确使用不同数据库时才设置：

| 可选变量 | 何时设置 |
| --- | --- |
| `NEXUS_IAM_JDBC_URL` / `NEXUS_IAM_JDBC_USERNAME` / `NEXUS_IAM_JDBC_PASSWORD` | IAM 使用独立数据源时 |
| `NEXUS_PERMISSION_JDBC_URL` / `NEXUS_PERMISSION_JDBC_USERNAME` / `NEXUS_PERMISSION_JDBC_PASSWORD` | permission 使用独立数据源时 |

### Keycloak

- [ ] 创建演示机专用 realm / confidential client；redirect URI 必须与演示机 HTTPS 回调完全一致。
- [ ] 注入 `NEXUS_IAM_KEYCLOAK_ISSUER`、`NEXUS_IAM_KEYCLOAK_CLIENT_ID`、`NEXUS_IAM_KEYCLOAK_CLIENT_SECRET`、`NEXUS_IAM_KEYCLOAK_REDIRECT_URI`。
- [ ] 必须使用 `NEXUS_IAM_KEYCLOAK_CLIENT_SECRET`；`NEXUS_KEYCLOAK_CLIENT_SECRET` 当前不是产品登录实际读取的变量。
- [ ] issuer 使用受信任的 HTTPS 地址；演示机能解析并访问 issuer，但不能绕过证书校验。
- [ ] 准备至少一个受控演示账号；不使用本地 stub token 代替 Keycloak 登录验收。

### Redis 与对象存储

- [ ] Redis 只在私网开放给应用；当前产品没有 Redis 口令配置，网络隔离是硬前提。
- [ ] 设置 `NEXUS_REDIS_HOST` / `NEXUS_REDIS_PORT`，并按部署选择 `NEXUS_CACHE_MODE`。
- [ ] 演示机需要持久文件时设置 `NEXUS_FILE_STORAGE=s3`、`S3_ENDPOINT`、`S3_TEST_BUCKET`，并通过密钥管理器注入 `S3_ACCESS_KEY` / `S3_SECRET_KEY`。
- [ ] 若暂时保留内存文件存储，必须记录 `ProductionProfileGuard` 告警并接受“重启后文件字节丢失”的已知限制。

## 2. 配置与密钥注入

### 必需变量

| 变量 | 要求 |
| --- | --- |
| `NEXUS_DB_URL` | 演示机 PostgreSQL JDBC URL |
| `NEXUS_DB_USER` | 专用数据库账号 |
| `NEXUS_DB_PASSWORD` | 从密钥管理器注入，不落仓库 |
| `NEXUS_CRYPTO_MASTER_KEY` | 当前环境独有且稳定的 hex 主密钥；INTERNATIONAL 至少 32 字节（64 hex） |
| `NEXUS_IAM_KEYCLOAK_ISSUER` | HTTPS issuer |
| `NEXUS_IAM_KEYCLOAK_CLIENT_ID` | 演示机 confidential client ID |
| `NEXUS_IAM_KEYCLOAK_CLIENT_SECRET` | 从密钥管理器注入 |
| `NEXUS_IAM_KEYCLOAK_REDIRECT_URI` | 演示机 HTTPS 回调地址 |

### 必须固定的非密钥配置

| 变量 / 参数 | 演示机值 |
| --- | --- |
| 启动参数 | `--spring.profiles.active=prod` |
| `NEXUS_CRYPTO_KEY_SOURCE` | `env`（`application-prod.yaml` 已固定；显式设置便于审计） |
| `NEXUS_IAM_IDP` | `keycloak` |
| `NEXUS_IAM_PERSISTENCE` | `jdbc` |
| `NEXUS_PERMISSION_PERSISTENCE` | `jdbc` |
| `NEXUS_DEMO_ENABLED` | `false` 或不设置 |
| `NEXUS_DEMO_ENVIRONMENT` | `prod` 或不设置（prod profile 固定为 `prod`） |
| `NEXUS_P1_DEMO_CONTEXT_ENABLED` | `false` 或不设置 |
| `NEXUS_P3_DEMO_CONTEXT_ENABLED` | `false` 或不设置 |
| `NEXUS_STUDIO_DEMO_CONTEXT` | `false` 或不设置 |
| `NEXUS_STUDIO_SEED_DEMO_METADATA` | `false` 或不设置（prod profile 固定关闭） |

- [ ] `SPRING_PROFILES_ACTIVE` 不包含 `demo`；推荐不设置该变量，直接在不可变启动命令中指定 `prod`。
- [ ] `NEXUS_PLATFORM_BOOTSTRAP_TOKEN`（及别名）和 `NEXUS_FILE_BOOTSTRAP_TOKEN` 初始为空。确需初始化时只在短窗口注入，用完清空并重启。
- [ ] `NEXUS_CRYPTO_MASTER_KEY` 与数据库备份分开保管；没有主密钥的数据库恢复不能解密 `platform.secret_ciphertext`。
- [ ] 密钥环境文件权限为服务账号只读；备份、日志采集和故障工单排除该文件。
- [ ] 不在 shell 历史、命令行参数、systemd unit 正文或应用日志中写真实值。

## 3. 启动前预检

- [ ] `java -version` 显示 JDK 21。
- [ ] JAR SHA-256 与发布记录一致。
- [ ] 演示机能连接 PostgreSQL；目标数据库名、账号和 TLS 策略正确。
- [ ] Keycloak discovery / issuer 地址通过 HTTPS 可达，redirect URI 已登记。
- [ ] 如使用 S3：目标桶已创建，服务账号仅拥有所需桶权限。
- [ ] 如使用 Redis：端口只在私网可达，未暴露公网。
- [ ] 端口 `NEXUS_HTTP_PORT`（默认 8080）只监听在反向代理可访问的受控接口或由主机防火墙限制。
- [ ] 没有同时设置 `prod,demo`，没有任何 demo 开关为 `true`。

启动命令保持稳定，密钥由服务管理器注入：

```bash
java -jar nexus-bootstrap-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

`ProductionProfileGuard` 会在 Web 服务接收连接前拒绝以下配置：demo profile / demo 开关、ephemeral crypto、stub IAM、memory IAM / permission、公开 health 详情。不要通过移除守卫来“修复”启动失败。

## 4. 首次启动与验收

### 日志检查

- [ ] 进程保持运行，退出码不是 1。
- [ ] Flyway 成功校验并迁移；不设置 `outOfOrder=true`，不跳过 validation。
- [ ] 没有 `Production profile rejected`、demo enabled、ephemeral key、stub IAM 或 memory persistence 错误。
- [ ] IAM 与 permission 实际为 JDBC 仓储。
- [ ] 如日志警告文件存储不是 S3或引导 token 仍设置，记录接受人和清理时间。
- [ ] 日志中没有数据库口令、主密钥、Keycloak client secret、S3 secret 或 bootstrap token。

### HTTP / 登录检查

- [ ] 反向代理的 HTTPS 首页可达，HTTP 强制跳转 HTTPS。
- [ ] `/actuator/health` 可用于探活，但匿名响应不包含数据库 / Redis 组件详情。
- [ ] `/actuator/info` 不公开环境变量；`metrics` 访问按 P9 / 网关策略限制。
- [ ] 从 Web 发起 Keycloak 登录并回跳成功；确认当前不是 stub 登录。
- [ ] 用受控账号建立 tenant context，执行一次只读页面访问。
- [ ] 本卡只验收部署与启动；P1 未合或 P5 未完成时，不宣称简单审批 E2E 已通。

### 重启检查

- [ ] 记录当前主密钥版本，停止并重新启动同一构建。
- [ ] 重启后数据库身份 / 权限仍可读，已保存的托管密钥仍可解密。
- [ ] Keycloak 登录仍成功；若失败，先核对 issuer、redirect URI 和 client secret 注入，不打印 secret。

## 5. 本地 demo 与演示机 prod 差异

| 项 | 本地 demo | 演示机 prod |
| --- | --- | --- |
| Spring profile | `demo` 或 `NEXUS_DEMO_ENABLED=true` | 只允许 `prod`；`demo` 同时激活会拒绝启动 |
| 数据库 | 只允许回环地址，可删库重建 | 专用 PostgreSQL 15，私网访问，必须备份 |
| 演示种子 | P1–P5 / K20 可写明确标记的演示行 | `nexus.demo.enabled=false`，不自动播种 |
| IAM | 可用 stub 做本地开发 | `keycloak`，HTTPS confidential client |
| IAM / permission 存储 | 可用 `memory` | 必须 `jdbc` |
| crypto | 默认 ephemeral，仅适合本地短进程 | `env` + 稳定 `NEXUS_CRYPTO_MASTER_KEY` |
| demo identity headers | 本地按需显式启用 | 全部固定关闭 |
| 文件 | 可用 memory | 推荐 S3/MinIO；memory 必须接受丢失告警 |
| health / info | 开发默认可显示详情 | health `when-authorized`，info env 关闭 |
| 登录账号 | stub token 或本地 IdP | 受控 Keycloak 演示账号 |
| 清理 | 可以清理种子或重建本机库 | 按备份/恢复与变更流程处理，不运行 demo 清理脚本 |

## 6. 失败处理与回退

1. 保存脱敏后的启动错误、发布 SHA、profile、变量**名称**和外部依赖状态；不要保存变量值。
2. 守卫失败时按错误逐项修正配置；禁止关闭 `ProductionProfileGuard`、改用 stub / memory 或打开 demo 绕过。
3. Flyway 失败时停止发布，保留数据库现场；不开 `outOfOrder`，不手改 schema history。
4. 新构建失败时回退到上一已验证 JAR；数据库已经执行不可逆迁移时，先按迁移说明和 L11 恢复流程评估，不能只换 JAR。
5. 密钥注入失败时撤销本次服务启动，检查密钥管理器权限；禁止把真实值临时写入仓库 `.env`。

## 7. P6 本机验收记录

- 基线：`main@a994668`。
- 自动化路径：`NexusOneProdProfileBootTest` 使用 `prod` profile 和空 PostgreSQL 15 启动完整 `NexusOneApplication`，验证 JDBC IAM / permission、demo 关闭、health 详情收紧，以及 env 主密钥加密写入 / 读回。
- 本机结果：2026-09-25 在 Eric-Desktop 运行 `ProductionProfileGuardTest`（6）与 `NexusOneProdProfileBootTest`（2），共 8 个测试，0 failure / 0 error / 0 skipped。空 PostgreSQL 15 从无 schema 迁移到 V705，`prod` profile 完整上下文启动成功；测试按设计告警文件存储仍是 memory。测试关闭嵌入式 PostgreSQL 后 Surefire 等待 Spring shutdown hook 超时并清理 fork，但 Maven 最终退出码为 0，测试报告全部通过。
- 真实干净演示机卡点：仓库不持有真实 `NEXUS_DB_PASSWORD`、`NEXUS_CRYPTO_MASTER_KEY`、Keycloak client secret、DNS / TLS 证书，也没有获授权的目标主机。因此本卡不对外部演示机执行部署；按本清单部署前必须由环境所有者提供这些外部资源。此处是明确外部卡点，不是让 Eric 手工代跑本 PR 的测试。
