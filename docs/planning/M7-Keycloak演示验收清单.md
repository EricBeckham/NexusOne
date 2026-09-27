# Keycloak 演示验收清单（M7）

日期：2026-09-26。依据：[demo-host-prod-deployment.md](../engineering/runbooks/demo-host-prod-deployment.md)（P6）、[L10_密钥与配置清单](L10_密钥与配置清单.md)、[demo-host-prod.env.template](../engineering/runbooks/demo-host-prod.env.template)（M6）。文档-only；**无密钥、无产品代码变更**。

本清单用于演示 / 内测机上的 **真实 Keycloak** 验收准备。完成本清单 ≠ 已在外网部署；本机 agent **不能**代做下方「外部卡点」。

## 1. 必做项（环境所有者 / 运维）

DL 真机分册：[真机演示 runbook](../engineering/runbooks/demo-host-realhost-launch.md)；本地 stub 单独看 [本地 runbook](../engineering/runbooks/demo-simple-approval.md)。预备 ≠ 验收，真机不复用 demo seed/reset。

演示前由 Eric 使用 [DL-X5 离线 Go/No-Go 一页纸](DL-X5-离线Go-NoGo一页纸.md) 核对 E1–E7 与 T-30；勾选和签名均留给环境所有者，不由 agent 代签。

### DPO 预备索引（不勾真机验收）

- 本地 [Keycloak compose / realm stub](../../deploy/keycloak/README.md)（DPO-X1）：client 默认禁用、REPLACE_* 占位；不是演示机部署。
- [DPO 卡正文](WEB演示部署打印星市加深任务卡_DPO波_V0.1.md)：L1 为后端配置守卫；C1 为公开 issuer 登录入口；C2 为只读部署 presence 状态。代码合入/本地绿都不能替代本清单 §1.5 真机登录。
- DPO-X3 可跳过探活：`scripts/demo-host/keycloak-smoke.ps1`；变量 `NEXUS_DEMO_KEYCLOAK_SMOKE_URL`。未设时 exit 0 + skipped 是“未执行”，不是验收通过；即使 HTTPS 200 也仅探活，不证明 token/租户登录。
- [prod 部署步骤](../engineering/runbooks/demo-host-prod-deployment.md) 与本清单双向对照。§2 的主机、DNS/证书、密钥仓、IdP 管理权和受控账号仍为外部阻塞，未关闭。

状态：预备 ≠ 已部署 ≠ Keycloak 真机已验收；本次仅补链接，不勾选 §1。

### 1.1 Realm 与 Client

- [ ] 创建**演示机专用** Keycloak realm（勿与本地开发 stub / 其它环境共用）。
- [ ] 创建 **confidential** client（服务端换 token）；记下 client ID（仅 ID，不入仓）。
- [ ] Redirect URI 与演示机 HTTPS 回调**完全一致**（协议 / 主机 / 路径；无尾斜杠漂移）。
- [ ] 如有 Web Origins / CORS / post-logout redirect，与演示机前端源一致。
- [ ] Valid redirect 列表中**没有** `http://localhost` / stub 开发回调残留（除非明确隔离的临时窗口并计划删除）。

### 1.2 应用侧变量（名称核对；值走密钥管理器）

| 变量 | 验收要求 |
|---|---|
| `NEXUS_IAM_IDP` | 必须为 `keycloak`（禁止 `stub`） |
| `NEXUS_IAM_PERSISTENCE` / `NEXUS_PERMISSION_PERSISTENCE` | `jdbc` |
| `NEXUS_IAM_KEYCLOAK_ISSUER` | 受信任 **HTTPS** issuer（含 `/realms/{realm}`） |
| `NEXUS_IAM_KEYCLOAK_CLIENT_ID` | 上述 confidential client ID |
| `NEXUS_IAM_KEYCLOAK_CLIENT_SECRET` | 从密钥管理器注入；**必须用此名**（`NEXUS_KEYCLOAK_CLIENT_SECRET` 当前**不会**被产品登录读取，见 L10） |
| `NEXUS_IAM_KEYCLOAK_REDIRECT_URI` | 与 Keycloak 登记的 redirect **逐字一致** |
| 前端 `VITE_NEXUS_KEYCLOAK_ISSUER`（若构建进 SPA） | 仅公开 issuer；**禁止**打进任何 secret |

- [ ] 启动参数 `--spring.profiles.active=prod`；`NEXUS_DEMO_ENABLED` 与全部 demo-context 开关为 `false` / 未设。
- [ ] 密钥不进仓库、不进 JAR、不进 shell 历史 / systemd unit 正文 / 应用日志。

### 1.3 网络与证书

- [ ] 演示机可解析并访问 issuer；**不得**关闭证书校验以“先通再说”。
- [ ] Keycloak 与应用、反向代理之间走受控私网；管理端口不对公网。
- [ ] 浏览器经反向代理 HTTPS 打开演示站；HTTP→HTTPS 强制跳转已开。

### 1.4 受控演示账号

三人创建及邮箱/subject 对应见 [DEMO-U4 Keycloak 三人 runbook](../engineering/runbooks/demo-u4-keycloak-three-users.md)；须先冻结 DEMO-U3 邮箱表，用户名不代替 IAM 权限，本文不代签真实用户验收。

- [ ] 至少一个**受控**演示用户（非本地 stub token）；口令/身份源由 IdP 管理，不写入本仓库。
- [ ] 账号具备完成一次「登录 → 建立 tenant context → 只读打开主路径页」的最小权限。
- [ ] 验收记录只写账号**显示名 / subject 类型**，不写口令。

### 1.5 登录验收步骤（真机时勾选）

- [ ] Web 发起登录跳到 Keycloak，而非 stub 登录页 / stub token 注入。
- [ ] 授权后回跳演示机 redirect，会话建立成功。
- [ ] `/actuator/health` 探活可用；匿名响应无组件详情泄露。
- [ ] 重启同一 JAR + 同一注入后，Keycloak 登录仍成功（先核 issuer / redirect / secret 注入，**不打印 secret**）。
- [ ] 失败时只保存脱敏错误、发布 SHA、profile、变量**名称**与依赖状态。

## 2. 本机 agent 无法代做的外部卡点

下列项需要 Eric / 环境所有者提供资源或授权；Cursor / Claude / NexusOne agent **不能**在本机代完成，也**不得**因此宣称 Keycloak 已验收：

| 卡点 | 为什么本机代不了 |
|---|---|
| 获授权的外网 / 演示机主机 | 无目标主机与 SSH/发布权限 |
| DNS 与公网 / 内网 HTTPS 证书 | 无域名所有权与证书私钥 |
| 密钥管理器中的真实 `NEXUS_IAM_KEYCLOAK_CLIENT_SECRET`、`NEXUS_CRYPTO_MASTER_KEY`、`NEXUS_DB_PASSWORD` | 仓库与 agent 会话禁止持有真实 secret |
| 演示机上的 Keycloak 实例与 realm 管理控制台操作 | 无 IdP 管理权限 |
| 受控演示账号的真实身份源开通 | 需人事 / IdP 管理员 |
| JAR 传到演示机并比对 SHA-256 后的真机启动 | 传输与启动属运维动作；本机仅有 M6 构建脚本 |
| 与真实 Keycloak 的端到端浏览器登录 | 缺主机 + 真 IdP；本机 stub 路径不代替 |

## 3. 与本地 demo / stub 的边界

| 项 | 本机 demo（已绿双主路径） | 本清单（演示机 Keycloak） |
|---|---|---|
| IdP | stub / 种子身份 | 真实 Keycloak confidential client |
| demo 开关 | 可开（本地） | 必须关 |
| 登录验收 | stub token / 平台 demo 头 | 浏览器 Keycloak 登录 + 回跳 |
| 结论可写 | 「本机 demo 主路径通过」（见 [I10](I10-上线门禁复验小结.md)） | 仅当 §1 真机勾选完成后才可写「演示机 Keycloak 登录通过」 |

**当前状态（开清单时）**：§1 未在外网真机执行；§2 卡点全部仍开放。勿将本文件或 M6 模板的存在解释为「已部署 / 已验收 Keycloak」。
