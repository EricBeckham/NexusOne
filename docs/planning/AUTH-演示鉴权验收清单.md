# AUTH 演示鉴权验收清单

日期：2026-09-27。任务卡：[WEB演示鉴权收口任务卡_AUTH波_V0.1.md](WEB演示鉴权收口任务卡_AUTH波_V0.1.md)。

本清单给环境所有者在**已合入 `main` 的构建**上勾选。代理不代勾，也不操作演示机。

## 0. 部署纪律（先读）

- [ ] 演示机只做部署和测试。产品改动只来自 GitHub 上已合入 `main` 的提交。
- [ ] 没有在演示机热修 jar、SPA、class，也没有用 env 改产品行为。
- [ ] 本次部署的构建晚于 AUTH1（#696）合入，因此覆盖 2026-09-27 的 SessionService 热补丁。热补丁不再当作产品来源。
- [ ] 记录发布 SHA（`main`），不记录 token、口令或 client secret。
- [ ] 未把本次勾选写成「Keycloak 生产已验收」。stub 单测通过只说明登录响应带 token，不能代替真机 IdP。

当前文档基线：`origin/main` @ `51ea9be1`（AUTH2 #697）。AUTH1 #696、AUTH3 #695 已在其历史中。

## 1. AUTH1–6 验收步骤

顺序：**登录 → 存 token → 切换 → 退出 → 岗位 → 平台页**。

### 1. 登录（AUTH1，#696）

- [ ] 使用授权码或 access token 登录成功。
- [ ] 成功响应含非空 `access_token`（Java 字段 `accessToken`）。值就是这次校验过的 token。
- [ ] `GET /api/v1/auth/session` 与租户切换响应可以没有 `access_token`（字段为 null 或被省略）。不要把缺字段当成登录失败。
- [ ] 本地证据：`SessionAccessTokenTest`（登录响应 JSON 含非空 `access_token`；current / switch 为 null）。

### 2. 存 token（AUTH2，#697）

- [ ] 登录或 PKCE 回跳之后，会话存储（`nexus.m012.session`）里的 `accessToken` 等于响应中的 `access_token` 或 `accessToken`。
- [ ] 随后的 session / current 响应若没有 token 字段，已保存的 token 仍在。
- [ ] 本地证据：`web/src/api/session-access-token.test.js`。

### 3. 切换（AUTH2，#697）

- [ ] 已保存 token 时，`POST /api/v1/auth/tenant-switch` 请求头为 `Authorization: Bearer <该 token>`。
- [ ] 会话里没有 token 时，切换失败，界面能读到原因（文案含「没有 access token」），而不是静默失败。
- [ ] 同一客户端上，刷新会话与退出在持有 token 时同样带 Bearer。

### 4. 退出（AUTH3，#695）

- [ ] 退出时，若本地还有 token，会请求服务端退出；服务端失败也要清掉本地会话并回到登录页。
- [ ] 退出后，浏览器后退不能回到仍显示该用户的桌面页。
- [ ] 桌面路由在没有真实 access token 时进入 `/login`。只残留 accountId、显示名或 contextId 不算已登录。

### 5. 岗位

仓库里没有单独的 AUTH4 PR。本步用已合入 `main` 的构建验收，不在演示机改页面。

- [ ] 登录并确认 token 已写入会话后，打开岗位任职 `/p2/positions`。
- [ ] 岗位列表请求（`GET /api/v1/positions`）带上 `Authorization: Bearer`。
- [ ] 无 token 时到不了该页（回到登录），而不是空白页假装已登录。

### 6. 平台页

仓库里没有 AUTH5 / AUTH6 PR。本步只确认已合入行为，不在演示机改接口开关。

- [ ] 有 token 时可以打开顶栏「平台操作」，并进入平台页（`/p1` 或租户目录 `/p1/tenants`）。
- [ ] 无 token 时这些桌面路由回到 `/login`。
- [ ] 已知：部分平台目录接口在客户端显式 `auth: false`，不会带 Bearer。这不是本次演示机要热修的项；若要改，另开合入 `main` 的卡。

## 2. 本清单不代替的事

- 不代替 M7 真机 Keycloak 勾选。
- 不授权任何人 SSH 到演示机改运行中的包或配置来修鉴权。
- 不记录、不粘贴 access token。失败记录只保留发布 SHA、接口路径和脱敏错误。
