# P0-03：Keycloak + PostgreSQL 验证资产

对应任务：[GitHub Issue #3](https://github.com/EricBeckham/NexusOne/issues/3)。执行范围仅为 PostgreSQL。这里固定**候选验证组合** Keycloak `26.5.2` + PostgreSQL `17`；在矩阵和恢复测试完成前，不把它称为通过验收的正式版本组合。

## 运行条件

- Docker Engine 与 Docker Compose v2，能拉取 `quay.io/keycloak/keycloak:26.5.2` 和 `postgres:17`。
- PowerShell 7 用于 `smoke.ps1`。端口 `127.0.0.1:8080`、`127.0.0.1:9000` 空闲。
- 此 Compose 是本机 PoC：`start-dev`、明文 HTTP、演示用数据库超级用户。不得作为生产部署配置。

## 启动与自动 smoke

在本目录运行：

```powershell
Copy-Item .env.example .env
# 在 .env 中将两个占位值换成不同的、随机生成的本机密码。
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d
./smoke.ps1
```

`smoke.ps1` 等待 `http://localhost:9000/health/ready` 返回 `UP`，随后检查 `nexus-poc` Realm 的 OIDC Discovery issuer、authorization/token/JWKS 端点。只有脚本打印 `PASS` 的检查才算执行。运行时请保存 `docker compose ps`、`docker compose images`、`docker compose logs --no-color keycloak` 的输出，日志先脱敏。`docker compose config` 会展开密码，请只用 `--quiet` 验证，不要把完整配置输出到工单。

登录 smoke 可在管理员控制台 `http://localhost:8080/admin/` 的 `nexus-poc` Realm 创建**本地临时用户**、设置非临时密码后运行：

```powershell
$env:P0_03_SMOKE_USERNAME = 'your-local-test-user'
$env:P0_03_SMOKE_PASSWORD = '<local-test-password>'
./smoke.ps1 -Login
Remove-Item Env:P0_03_SMOKE_PASSWORD
```

此路径使用专门的 `p0-smoke` 公共客户端和 Direct Access Grant，仅检查凭据登录、Access/ID Token 与 UserInfo；它**不能**代替 Web/H5 所需的 Authorization Code + PKCE 验证。`nexus-web-poc` 客户端只启用标准流程，强制 S256，预置的本地回调地址是 `http://127.0.0.1:8081/callback`，需要额外提供回调服务并完成浏览器授权码往返。不要将 `p0-smoke` 客户端带入产品环境。

## 需要补做的验收

| 项目 | 操作与证据 | 当前状态 |
|---|---|---|
| PostgreSQL 建库与持久化 | 首次启动，`docker compose exec postgres psql -U keycloak -d keycloak -c 'select current_database(), version();'`；重启 Keycloak 后 Realm 和用户仍在 | 未运行 |
| OIDC 授权码 + PKCE | 浏览器以 `nexus-web-poc` 完成 S256 授权码往返，校验 state、nonce、token、刷新和登出 | 未运行 |
| SAML | 建 SAML Client 和测试 SP，记录断言、签名、单点登出 | 未运行 |
| Organizations 与租户隔离 | 单 Realm 启用 Organizations，建立两个组织与成员，验证邀请、跨组织访问和各组织 IdP 绑定；按目标组织规模测 P95 | 未运行 |
| Federation | 连测试 LDAP/AD 或上游 IdP，验证同步、停用与断连行为 | 未运行 |
| Session / 强制下线 | 多会话登录、刷新、撤销后检查 Token 和业务黑名单收敛 | 未运行 |
| MFA | 管理员启用 OTP 必选，检查注册、挑战、恢复与重置；短信 MFA 属于另行实现的 SPI | 未运行 |
| 升级与回滚 | 先从选定旧版建库和备份，再升级至候选版，记录迁移日志与数据校验；回滚须通过恢复旧版数据库备份和旧镜像演练，不直接让旧程序读升级后 Schema | 未运行 |
| 备份恢复 | 停写并执行 `pg_dump -Fc`，在**新的空卷/新实例** `pg_restore`，核对 Realm、用户、客户端与登录；记录 RPO/RTO | 未运行 |
| 目标部署矩阵 | 在确定的 Linux/CPU/JDK 与 PostgreSQL Profile 逐项重跑，并记录镜像 digest、许可证和来源 | 未运行 |

备份命令示例（仅示意，需在隔离恢复环境演练；重定向产生的文件已被本目录 `.gitignore` 排除）：

```powershell
docker compose --env-file .env exec -T postgres pg_dump -U keycloak -d keycloak -Fc > ./keycloak.dump
```

停止本 PoC 使用 `docker compose --env-file .env down`。`down -v` 会删除数据库卷，只在已确认备份且明确要销毁本地测试数据时使用。

## 边界与设计记录

- Keycloak 负责凭据、认证协议、会话和 MFA；Nexus P2 持有 Person/Organization/Employment/Role/Policy 主数据。Keycloak Organization 成员关系需要与 P2 Employment 对账。服务端按 P2 成员关系签发租户上下文，绝不能从客户端自报 `tenant_id` 直接信任。
- Keycloak 使用独立数据库；业务模块不读取 Keycloak 内部表。此 PoC 的 `keycloak` 数据库用户是官方 PostgreSQL 镜像初始化的超级用户，正式部署需改为独立、最小权限账号并验证迁移所需权限。
- 首次导入 `nexus-poc` Realm 只用于空数据库；改 JSON 后重启容器不会覆盖已有 Realm，后续变更应走管理 API/迁移流程。
- 未运行的测试和未确定的生产版本、镜像 digest、许可证、目标 OS/CPU 均保留为待验收，不推断为通过。

## 文档依据

- [Keycloak 26.5.2 容器与 PostgreSQL 配置](https://github.com/keycloak/keycloak/blob/26.5.2/docs/guides/server/containers.adoc)
- [Keycloak 26.5.2 容器快速启动](https://github.com/keycloak/keycloak/blob/26.5.2/docs/guides/getting-started/templates/start-keycloak-container.adoc)
- [PostgreSQL 官方镜像环境变量](https://hub.docker.com/_/postgres)
- [Docker Compose 插值与配置验证](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)
- [Docker Compose 健康依赖示例](https://docs.docker.com/compose/gettingstarted/)

上述来源由 `ctx7` 查询到，具体环境仍须按本任务矩阵实测。
