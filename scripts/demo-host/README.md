# 演示机构建辅助（M6）

对照：[demo-host-prod-deployment.md](../../docs/engineering/runbooks/demo-host-prod-deployment.md)（P6）。

| 文件 | 用途 |
|---|---|
| [demo-host-prod.env.template](../../docs/engineering/runbooks/demo-host-prod.env.template) | 可填 env 占位符；**禁止**写入真实密钥后提交 |
| `build-bootstrap-jar-sha.ps1` / `.sh` | 本机构建 `nexus-bootstrap` JAR 并打印 SHA-256 |

**模板 ≠ 已部署。** 本目录脚本不连接外网主机、不注入密钥、不传输 JAR。
# DPO-X3 · 可跳过 Keycloak 探活

`pwsh -NoProfile -File scripts/demo-host/keycloak-smoke.ps1`：未设 `NEXUS_DEMO_KEYCLOAK_SMOKE_URL` 时 exit 0 + skipped（未执行，不是验收绿）。设置受控公开 HTTPS health/discovery 或登录入口 URL 后，200/302/303 仅表示探活/重定向响应；不证明登录成功、token 有效或 tenant context 已建。

只允许无 userinfo/query/fragment 的 HTTPS URL；不发凭据、不跟随重定向、不打印响应 body/Location/path、不开 TLS 绕过。失败只打印 host/status，exit 1。不默认接真机或 CI。启用前须由环境所有者选择非敏感公开端点；参数 `-TimeoutSeconds` 1–60，默认10。

本地契约：`pwsh -NoProfile -File scripts/demo-host/keycloak-smoke.tests.ps1`（未设、凭据 URL 拒绝、loopback 不可达三态；不访问真实 IdP）。对照 [M7](../../docs/planning/M7-Keycloak演示验收清单.md) 与 [prod runbook](../../docs/engineering/runbooks/demo-host-prod-deployment.md)。
