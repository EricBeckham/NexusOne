# AUTH6H1 · 令牌频繁过期 + 权益 / 配额 / 产品定义页：诊断与验收

基线 `main@7357dbfa`（含 AUTH6G 与 #746）。只写诊断、合入顺序与勾选项，不代表已部署或已验收。演示机不热修，不改 `NEXUS_*` env；不记录 token、context id、person id 原值。

## 1. 现象（演示机，平台运营）

| 页面 | 报错 |
|---|---|
| 任意平台页，登录几分钟后 | 「登录已过期或会话已失效」`AUTH6.PLATFORM_READ_DENIED · session_invalid` |
| 权益与配额 · 权益投影 | `platform context required for tenant.entitlement.get` |
| 权益与配额 · 配额用量 | `tenant context does not match target tenant` |
| P4 平台配置 / 产品配置向导 | `platform context required for product-definition.list` |
| 平台审计 | `审计中心只对「审计管理员」开放……（duty_set）` —— **预期**，见 [AUTH6G1](AUTH6G1-平台只读页上下文缺口诊断.md) |

## 2. 诊断

### 2.1 权限「经常过期」：access token 从不续期

- 登录后 SPA 只持有 Keycloak access token，每次请求带 `Authorization: Bearer`，后端每次重新校验。
- realm 未配置 `accessTokenLifespan`，按 Keycloak 默认约 5 分钟。
- 修复前前后端均无续期逻辑，过期后所有带凭据的页面都会 401（`session_invalid`）。应用 TenantContext（8 小时）不是原因。
- LO2 / #746 已在服务端按 access token 指纹保存本次登录的 `refresh_token`，从未下发浏览器；续期基于它实现。

### 2.2 权益 / 配额 / 产品定义：与 AUTH6G 同一类缺口

| 页面 | 接口 | 守卫 | 缺口 |
|---|---|---|---|
| 权益投影 | `GET /api/v1/p1/tenants/{id}/entitlement` | `requirePlatformContext("tenant.entitlement.get", SYSTEM_ADMIN)` | 不在 AUTH6 桥白名单；前端 `auth: false` |
| 配额用量 | `GET /api/v1/p1/tenants/{id}/quotas` | `requireTenantOrPlatform(id, "quota.list")` | 同上。该守卫有平台上下文时只要求 SYSTEM_ADMIN，无平台上下文才比对租户 → 报「tenant context does not match」。**产品本已允许平台只读，只是没走桥**，不需要额外产品决策 |
| P4 平台配置 / 产品配置向导 | `GET /api/v1/p4/product-definitions`、`/{id}`、`/{id}/config-readiness` | `requirePlatformContext("product-definition.*", SYSTEM_ADMIN)` | 同上 |

## 3. 卡片与合入顺序

| 顺序 | 卡 | PR | 内容 | 基 |
|---|---|---|---|---|
| 1 | AUTH7R1 | #747 | 后端 `POST /api/v1/auth/refresh`：用服务端 refresh token 换新 access token（只回 access token，单次使用、按新 token 重新绑定） | main |
| 2 | AUTH7R2 | #748 | 前端：带凭据调用遇 401 → 续期一次并重放；并发共享一次续期；续期失败原样返回 | main |
| 3 | AUTH6H2 | #749 | 后端：桥精确加入权益、配额、产品定义列表 / 详情 / 就绪度 GET（SYSTEM_ADMIN） | main |
| 4 | AUTH6H3 | #750 | 前端：上述 helper 改用 `AUTHENTICATED_PLATFORM_READ`，错误文案接入 | main |
| 5 | AUTH6H1 | 本卡 | 诊断与验收（docs） | main |

五张互不叠加，可按上表顺序合入。

## 4. 部署

- [ ] 记录包含 R1、R2、H2、H3 的 `main` 发布 SHA：`________`。
- [ ] jar 与 SPA 从同一 SHA 构建；SPA 按 [PKG1](../engineering/runbooks/pkg1-spa-keycloak-public-env.md) 注入 Keycloak 公开变量并检查。
- [ ] env 不变；只部署，不热修。

## 5. 验收

### 5.1 续期（平台运营）

- [ ] 登录后打开租户目录，正常。
- [ ] **等待 6–10 分钟**（超过 access token 有效期，但在 Keycloak SSO 空闲时限内），不刷新页面，再切到租户关系 / 表单模板等平台页：正常加载，**不再**出现 `session_invalid`。
- [ ] 开发者工具「网络」面板中可见一次 `POST /api/v1/auth/refresh`（200）紧接着原请求重放成功；多页并发时只有一次 refresh。只确认请求存在，不记录请求头或响应体的值。
- [ ] 长时间空闲超过 Keycloak SSO 会话时限后：refresh 返回 401，页面提示「请退出后重新登录」——属预期（需重新登录），不是回归。

### 5.2 权益与配额、产品定义（平台运营）

- [ ] 权益与配额 `/p1/quotas`：选择一个租户（如 `demo_acme`），权益投影与配额用量均加载，不再出现 `platform context required` / `tenant context does not match`。
- [ ] P4 平台配置 / 产品定义 `/p4/product-definitions`：列表加载。
- [ ] 产品配置向导 `/p4/product-config-wizard`：产品定义下拉加载，选中后就绪度（配表单 → 配流程 → 绑 SKU → 发布）加载。
- [ ] 写操作（新建 / 保存产品定义、导入、绑定 SKU、导出、配额消耗）不在本波范围，若失败记为范围外。

### 5.3 非平台身份

- [ ] 林晓、周然直接打开 `/p1/quotas`、`/p4/product-definitions`：被拒，文案为「当前所在租户不是平台租户；只有平台运营可以查看。」（`not_platform_context`）。

## 6. 仍有问题时

- 仍频繁 `session_invalid` 且网络面板没有 `/api/v1/auth/refresh`：SPA 不含 R2，重新打包部署。
- `/api/v1/auth/refresh` 返回 401 `P2.IAM.REFRESH_UNAVAILABLE`：本次登录发生在含 R1 的 jar 部署之前，或 jar 重启过（服务端提示仅在进程内存中）——退出重新登录。
- 仍 `P1.CONTEXT.0001`：jar 不含 H2 / SPA 不含 H3，重新打包部署。
- 其他拒绝按 [AUTH6F5 §4](AUTH6F5-平台只读列表修复验收与合入顺序.md) 的 reason 表处理。

结论：________　执行人：________　日期：________　发布 SHA：________
