# OIDC RP 发起登出（演示机桌面退出）

适用：桌面正式登录（`/login` → Keycloak）。不含 H5。不写口令、token、client secret。本文是设计与验收口径，不代表任何环境已部署。

## 1. 现状与缺口

- 历史 AUTH3（#695）的「退出」按账号吊销所有 TenantContext，导致同账号另一台电脑也收到 `tenant context is revoked`。现已改为仅吊销请求携带的当前 context（见下文），前端仍清本地身份并 `router.replace('/login')`。
- 前端 `p2.logout()` 发空 body；SPA 只存 `accessToken`，手里没有 `id_token` / `refresh_token`。服务端虽有 `KeycloakIdentityProvider.logout(refresh, idTokenHint)`（back-channel form POST），但拿不到这两个值。
- 浏览器侧没有跳 Keycloak 的 `end_session`，Keycloak 的 SSO cookie 仍在。
- 结果：退出后再点「使用 Keycloak 登录」，Keycloak 看到有效 SSO 会话，直接带 code 回跳，**看不到账号密码页**。换人演示时会静默登回上一个人。

## 2. 方案

**主路径：RP-initiated logout（浏览器跳转）。**

1. 登录换票时，服务端保存 IdP 返回的 `id_token`（及 `refresh_token`），按已验证 access token 的 SHA-256 指纹绑定。不能按账号或 subject 共用，否则后登录的浏览器会覆盖前一个浏览器的退出凭据。
2. `POST /api/v1/auth/logout`：验证当前 context 属于已认证账号及 subject → 仅吊销当前 context → best-effort back-channel → 返回 `end_session_url`（LO3）。只使用该 bearer 对应的服务端 hint，忽略旧请求 body 的 `refreshToken` / `idToken`（保留 wire 兼容，不视为会话所有权证明）。URL 由服务端拼：

   ```text
   {issuer}/protocol/openid-connect/logout
     ?client_id={client_id}
     &id_token_hint={服务端保存的 id_token}
     &post_logout_redirect_uri={前端 origin}/login
   ```

   `post_logout_redirect_uri` 只取配置的前端 origin（来自已配置的 Keycloak `redirect-uri` 的 origin）加 `/login`，不接受请求方传入。URL 里永远没有 client secret。
3. 前端拿到非空 `end_session_url` 就整页跳转过去；Keycloak 清 SSO cookie 后回到 `/login`（LO4）。

**补充：back-channel。** 服务端同时用保存的 `refresh_token` / `id_token` 调现有 `idp.logout`。失败只吞掉，不阻断退出，也不影响返回的 URL。

**降级：** 服务端没有 hint（进程重启、旧会话、非授权码登录）时，仍清应用会话，`end_session_url` 为空，前端回退到 AUTH3 行为（直接 `/login`）。这种情况下浏览器**可能仍有 SSO**，再登录会静默回跳；关闭浏览器或用隐身窗口可绕过。

## 3. token 存放纪律

- `id_token` / `refresh_token` **不**下发给 SPA，不进 `sessionStorage` / `localStorage`，不进登录 API 响应。
- 服务端内存保存，按已验证 bearer 的 SHA-256 指纹绑定，TTL 同 TenantContext；登出取用一次即丢弃，过期不可取用。指纹不下发、不记日志；不保存原始 access token。切租户仍使用同一 bearer，因此不会借用另一个浏览器的 hint。
- 当前实现为单进程内存；多实例部署需要换成共享会话存储（另开卡）。重启后 hint 丢失，按上面的降级处理。
- 日志、异常、审计、PR、截图都不打印 token 原文；`end_session_url` 只回给发起登出的那个已认证请求，前端跳转前不打进 console。

### 普通退出的边界

- JDBC 和内存仓库都按 `context_id + account_id` 定点撤销；重复撤销不改原时间。普通退出不调用账号级全量撤销；显式管理员失效机制不在本次改动内。
- 没有当前 context 时，清本地状态并可消费自己 bearer 的 IdP hint，但**绝不**回退到撤销账号全部 context。token-only 登录、重启或其它实例缺 hint 时也不借用同账号 hint。
- 本薄片没有持久化 session 分组、token 黑名单或迁移。只撤销本次携带的 context，不推断历史切租户遗留的全部 context；缺 header 不能证明哪些历史 context 属于本次登录。不得宣称实现全会话 token 撤销。
- 两个独立设备/浏览器配置文件可拥有独立 Keycloak SSO；同一浏览器配置文件的多个标签可能共享 IdP cookie，不承诺标签级 SSO 隔离。

## 4. 环境负责人手动项（Claude 不登演示机改）

在 Keycloak 管理台，产品 client（`NEXUS_KEYCLOAK_CLIENT_ID` 指向的那个）上：

- [ ] **Valid post logout redirect URIs** 登记前端 `/login`，例如 `https://nexus.haopan.net/login`。只登记确切地址，不用 `*` 通配。
- [ ] 确认 `redirect-uri`（`NEXUS_KEYCLOAK_REDIRECT_URI`）的 origin 就是上面这个前端 origin；服务端据此拼 `post_logout_redirect_uri`。
- [ ] 不改 realm 密码策略、不改登录主题；不把管理员口令写进仓库或聊天。

没有登记时，Keycloak 会拒绝跳回（显示 “Invalid redirect uri”），应用会话已清但 SSO 未清，属配置缺项，不在演示机热修。

## 5. 验收

同一浏览器、非隐身窗口：

- [ ] 用某账号经 Keycloak 登录进首页。
- [ ] 点右上角「退出」→ 浏览器经 Keycloak 登出页回到产品 `/login`。
- [ ] 再点「使用 Keycloak 登录」→ **出现用户名 / 密码表单**，不是静默回跳进首页。
- [ ] 换另一个演示账号登录，顶栏显示的是新账号。
- [ ] 全程地址栏、console、网络面板截图里不出现 client secret；证据不贴 `id_token_hint` 原值。

跨浏览器回归（使用两个独立浏览器配置文件或设备）：

- [ ] A、B 用同一账号分别登录，取得不同 context；B 可正常读取用户、角色与分配、流程设计器、发布中心。
- [ ] A 点退出；A context 已撤销，B 上述页面仍可读取，不出现 `tenant context is revoked`。
- [ ] B 再退出，仅 B context 被撤销；A/B 的 IdP 退出 hint 不串用。

代码验收（2026-09-27）：先在旧实现复现 A 退出导致 B context 被撤销的失败，再验证 session / MFA / `AuthLogoutApiTest` / P3 isolation 相关回归及 `LogoutContextPostgresTest`，共 42 项通过（0 failure / error / skip）。HTTP 测试显式用 memory + stub 并创建 ACTIVE membership；Postgres 使用 worktree 内一次性随机端口集群、显式 JDBC URL，测试后自动停止；未连接演示库或已有业务库。此证据不等于全仓测试或真实 Keycloak/演示机验收；本次不改前端、IdP 配置、演示机或 Flyway。

## 6. 卡片

| 卡 | 内容 |
|---|---|
| LO1 | 本文 |
| LO2 | 服务端在授权码换票后保存 `id_token` / `refresh_token`，不下发 SPA |
| LO3 | `POST /api/v1/auth/logout` 返回 `end_session_url`，back-channel best-effort |
| LO4 | 桌面「退出」拿到 URL 就整页跳转，否则回 `/login` |
| LO5 | 前端联邦登出回归测试 |
