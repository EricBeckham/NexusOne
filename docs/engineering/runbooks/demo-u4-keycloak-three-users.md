# DEMO-U4 · Keycloak 三人用户创建与 IAM 映射

> **预备 ≠ 验收。** 本文仅操作步骤与字段名；未连接演示机或 IdP，未创建真实用户。口令、client secret、token、cookie、OTP 种子和恢复码不得进入仓库、PR、日志、截图或命令历史。

## 1. 操作前核对（不满足即停）

由获授权 IdP 管理员在专用演示 realm 操作；应用部署/测试人员不在演示机改源码、SQL、业务配置或借用管理员会话。本册不运行本地 compose/stub 导入，不删除/重建已有 realm 或用户。

先按 [M7](../../planning/M7-Keycloak演示验收清单.md) 核对 HTTPS issuer、confidential client、精确 redirect URI、受控账号与 MFA 要求。client secret 仅由密钥仓注入 `NEXUS_IAM_KEYCLOAK_CLIENT_SECRET`，本册不读取或轮换它。prod 的所有 demo seed/context 开关保持关闭。

**邮箱冻结门禁：** 本文编写时未找到 DEMO-U3 最终 PR 邮箱表。下表 `EMAIL_*` 是字段占位，**不得按此字面值创建用户**。操作前必须取得 DEMO-U3 已合 PR 的最终邮箱表与 IAM 账号准备证据，在受控运维记录核对；若 U3 未完成、邮箱未冻结或 IAM 绑定冲突，只记阻塞，不猜邮箱。三人邮箱必须不同、为受控地址，不能复用他人/生产账号。

| 身份标签 / 建议 Username | Keycloak Email 字段来源 | IAM 对应 | 应用范围（由 U3 准备，不由 IdP 用户名授予） |
| --- | --- | --- | --- |
| `nexus-demo-platform` | `EMAIL_PLATFORM`：U3 平台用户最终邮箱 | `iam_global_account.email` | 平台租户 person / membership / 系统管理员职责及授权 |
| `nexus-demo-tenant-admin` | `EMAIL_TENANT_ADMIN`：U3 租户管理员最终邮箱 | `iam_global_account.email` | 指定演示租户 person / membership / 租户管理员权限 |
| `nexus-demo-member` | `EMAIL_MEMBER`：U3 成员最终邮箱 | `iam_global_account.email` | 指定演示租户 person / membership / 最小成员权限 |

若 realm 配置 Email as username，Username 按最终邮箱填写，保留上述身份标签用于清单；不能为适配表格而改变 realm 全局登录策略。Username / First name / Last name 仅作登录或显示标识，不作为应用授权依据。

## 2. 在 Keycloak 创建 / 核对三人（每人分别执行）

步骤参考 [Keycloak 官方管理指南：用户与凭据](https://www.keycloak.org/docs/latest/server_admin/)；按目标实例版本核对界面名称。

1. 进入获授权管理控制台，选中专用演示 realm（不是 master）。在 Users 搜索 Username 与 Email，先确认没有重复或被其他身份占用；已有正确用户只核对，不重建其 User ID。
2. 无用户时选 Create user，填 Username、Email、First name / Last name；Email 必须与冻结的 U3/IAM 邮箱逐项一致，无首尾空白。核 Enabled 为开启，保存。
3. Email verified 只能在完成邮箱所有权验证或获授权核验后确认；否则通过 Verify Email 流程完成。不得为了让登录“绿”而把未验证邮箱标为已验证。SMTP/邮箱验证不可用即记阻塞。
4. 在 Credentials / Set password 由授权人员安全设置初始口令，启用 Temporary，使本人首次登录更改；优先通过受控邮件完成 Update Password 流程。口令只走 IdP/密钥交付渠道，不提供复制口令命令或密码表。
5. 按既有 realm 策略完成 Required user actions（Verify Email、Update Password；要求 MFA 时含 Configure OTP 或已批准方式）。管理员须满足应用已有 MFA 检查，不关闭策略绕过。OTP/恢复码由本人保管。
6. 不给三人 `realm-management` / Keycloak 管理员权限，也不把 platform/tenant-admin 标签映射成 IdP 管理权。仅保留已批准的最小 IdP 权限；NexusOne 应用职责在 IAM/permission 中管理。
7. 在受控运维记录核对 User ID（后续 token 的 `sub`）、Email、Username、Enabled 与验证状态；仓库只记身份标签、检查结果和证据编号，不提交真实身份目录导出。

## 3. 邮箱与 IAM account 的对应规则

源码依据：[AccountReconciler](../../../nexus-p2-iam/src/main/java/one/nexus/p2/account/AccountReconciler.java)、[JdbcIdentityRepository](../../../nexus-p2-iam/src/main/java/one/nexus/p2/persist/JdbcIdentityRepository.java)、[SessionService](../../../nexus-p2-iam/src/main/java/one/nexus/p2/session/SessionService.java)。

- Keycloak claims 的 `sub` 对应 IAM `keycloak_user_id`，`email` 对应 IAM `email`；`preferred_username` / `name` 只供用户名/显示名。应用登录请求包含 `openid profile email`，须保证实际收到 email claim；不在日志打印 token 来核对。
- 账号对齐优先查 `keycloak_user_id`；未知 subject 才由 AccountReconciler 尝试按邮箱匹配现有账号，无匹配则创建新 global account。JDBC 邮箱匹配忽略大小写，但仍应逐字核对批准地址、不依赖大小写差异区分三人。
- 邮箱兜底可能更新现有账号的 subject 绑定，**不是无风险自动合并**。若现有 `keycloak_user_id` 与目标 User ID 不一致、邮箱重复/被占用、realm 不同或用户曾重建：停止登录试错，交 IAM/IdP 负责人核实并走已批准修复；不手改 SQL、不清空绑定、不重复建同邮箱账号。
- Keycloak 用户创建不等于应用账号、person、租户 membership、职责/权限准备完成。应用 context 签发仍需要对应租户 person 与有效 membership；不能仅凭 Email 或 Username 进入平台。U3 应用侧缺项由该卡负责人解决，本册只指导 IdP 操作。
- **U4 之后必须做 DEMO-U2 一次性，否则「缺少 TenantContext」是预期卡点。** 只建 Keycloak 用户就登录时，IAM 会按邮箱新建 0 person / 0 membership 的账号，服务端不签发 `context_id`。含 ID1（#720）的构建里，U2 窗口会插入 / 对齐三套固定身份，无需 `NEXUS_DEMO_ENABLED`（演示机仍禁止打开）；但窗口前仍须先清孤儿邮箱。步骤与冲突见 [TC4](tc4-demo-u2-after-keycloak-checklist.md) 与 [ID1 / ID3](id1-u2-identity-bootstrap.md)。

## 4. 部署后只测试，不代签

- [ ] 三个受控浏览器会话分别正常登录、完成 required actions；不共用 cookie，不 impersonate，不用 stub token。
- [ ] 通过现有受控账号/会话查看方式核 IAM account 与冻结邮箱、subject 的对应；不将敏感响应或授权头粘入证据。
- [ ] 平台用户能切入平台上下文并按 [AUTH6](auth6-platform-p1-read-context.md) 读租户目录/注册审订列表（该开关须由部署配置明确启用，demo context 仍关闭）。
- [ ] 租户管理员和成员各自取得指定演示租户上下文及批准的最小权限；成员不可越权读平台列表，租户管理员不等同平台系统管理员。
- [ ] 缺邮箱 claim / 新建错账号 / 无 person 或 membership / MFA 拒绝 / 权限拒绝时停止，记脱敏错误类别、发布 SHA、身份标签与证据编号，交对应负责人；不改演示机代码或降低安全策略。

本卡仅文档校验；上述勾选留给真实环境执行人。结果记录：________　执行人：________　日期：________。
