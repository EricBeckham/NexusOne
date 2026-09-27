# WEB 演示鉴权收口任务卡（AUTH 波）V0.1

- **基线**：`origin/main` @ `51ea9be1`（**AUTH2 #697**）。其下已有 **AUTH1 #696**（`16c2129a`）、**AUTH3 #695**（`58b543fd`，logout 与桌面路由要 token）。起点曾核对接近 `0b4acc0e`（DL-L4 #694）。
- **性质**：本文件是规划。产品行为只经 GitHub 合入 `main`。本卡不改 Keycloak 密钥处理，不写 secret。
- **验收正文**：[`AUTH-演示鉴权验收清单.md`](AUTH-演示鉴权验收清单.md)。

## 1. 演示机纪律

演示机只做**部署**和**测试**。产品改动只走 GitHub。

- 只部署已经合入 `main` 的构建。
- 禁止在演示机上热修产品：不要替换 jar、SPA、class，也不要靠改 env 改产品行为。
- 合入 `main` 之后的构建，覆盖 2026-09-27 的 SessionService 热补丁。热补丁不再作为演示机上的产品来源。
- 本波文档与实现都不登录、不改那台演示机。部署与勾选由环境所有者在拿到 `main` 制品后做。
- 本地 stub / 单测绿不等于 Keycloak 真机已验收。不要在 prod 路径推荐打开 `NEXUS_DEMO_ENABLED`。

## 2. 已合入与验收路径

验收顺序固定为：**登录 → 存 token → 切换 → 退出 → 岗位 → 平台页**（AUTH1–6）。

| 步骤 | 已合入的实现 | 说明 |
|---|---|---|
| 登录 | AUTH1 #696 | `SessionService.SessionView.accessToken`，JSON 名 `access_token`。只在 `loginWithCode` / `loginWithAccessToken` 成功路径填入刚校验过的 token。`switchTenant` / `current` 可为 null（`non_null` 时响应可省略该字段）。 |
| 存 token | AUTH2 #697 | `applySession` / PKCE：响应有 `access_token` 或 `accessToken` 才写入会话存储。没有该字段时不清除已保存的 token。 |
| 切换 | AUTH2 #697 | `POST /api/v1/auth/tenant-switch` 在已有 token 时带 `Authorization: Bearer`。无 token 时失败文案可读（含「没有 access token」）。会话读取与退出走同一套默认带 Bearer 的客户端。 |
| 退出 | AUTH3 #695 | 仍持有 token 时尽量调用服务端退出；无论服务端是否成功，都清本地会话。桌面路由没有真实 access token 时回到登录页。仅有 accountId / 显示名不算已登录。 |
| 岗位 | 验收步骤，无单独 AUTH4 PR | 用已合入 `main` 的构建打开岗位任职 `/p2/positions`。列表请求走默认鉴权，应带上已保存的 Bearer。不要在演示机上改这一页。 |
| 平台页 | 验收步骤，无单独 AUTH5/AUTH6 PR | 顶栏「平台操作」与平台页（如 `/p1`、租户目录 `/p1/tenants`）须在已登录（有 token）后打开。若干平台目录接口当前显式 `auth: false`，本清单不把这一点当成演示机热修项。 |

## 3. 硬禁

- 禁止 H5 改动；桌面鉴权与 `/m/login` 各自的规则保持 AUTH3 已写明的边界。
- 禁止假支付、假绿升级。
- 禁止在前端、日志、PR 正文写入真实 secret。Keycloak client secret 的读取方式不在本波修改。
- 禁止 SSH 或任何方式改演示机来“先把登录跑通”。
- 本波文档 PR 不夹带产品代码。
