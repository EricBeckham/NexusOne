# AUTH6F1 · 平台只读列表在开关打开后仍被拒的定位

只读结论，不改行为。不含口令、token、client secret，没有接触演示机。

## 现象

演示机 `NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED=true`（文件和进程均已确认）。平台运营 `nexus-demo-platform` 登录后打开「租户目录」，页面显示「租户目录暂时无法加载」，错误码 `AUTH6.PLATFORM_READ_DENIED`。

## 谁在拒

`AUTH6.PLATFORM_READ_DENIED` 只由 `nexus-bootstrap/.../web/P1PlatformReadContextFilter.java` 写出。开关为 true 时，它接管且只接管两条 `GET`：`/api/v1/p1/tenants`、`/api/v1/p1/tenant-registrations`。所有拒绝都写同一个 body，没有区分原因，因此现场无法仅凭错误码判断是哪一支。

按执行顺序，拒绝分支如下：

| # | 条件 | HTTP |
|---|---|---|
| 1 | 请求带 `X-Tenant-Id` | 400 |
| 2 | 缺 `Authorization: Bearer …` 或缺 `X-Nexus-Context-Id` | **401** |
| 3 | access token 校验失败（含过期）/ context 不存在、已吊销、已过期、不属于该身份 / 管理员 MFA 未满足 | 401 或 403 |
| 4 | context 的租户不是 P1 平台租户（`nexus.p1.platform-tenant.id`，默认 `…0001`），或 context 没有 person | 403 |
| 5 | 展开后的平台职责集合不**恰好**等于 `{platform_system_admin}`（缺失，或同时持有安全 / 审计管理员） | 403 |
| 6 | `tenant:list` 授权检查不通过，或策略快照不可用 | 403 |

开关为 false 时过滤器不介入；请求落到 P1 控制器，没有平台上下文时返回 `P1.CONTEXT.0001`（演示上下文关闭时的既有行为）。

## 根因：前端从不带凭据调用这两条列表

`web/src/api/client.js`：

```js
listTenants: () => api("/api/v1/p1/tenants", { platform: true, auth: false }),
listTenantRegistrations: (status) => { … api(`/api/v1/p1/tenant-registrations${q}`, { platform: true, auth: false }) },
```

`auth: false` 时 `api()` 不附 `Authorization`，也不附 `X-Nexus-Context-Id`，只附演示头 `X-Nexus-Demo-Actor-Id` / `X-Nexus-Demo-Reason`。AUTH6 过滤器不认演示头（设计如此，见其测试 `demoHeadersCannotAuthorize`），于是**每一次**都命中分支 2 → 401 `AUTH6.PLATFORM_READ_DENIED`，与平台运营的身份、职责、授权是否就绪无关。

AUTH6（#700）只改了后端桥；前端这两处仍是演示上下文时代的写法，所以开关打开后页面只会从 `P1.CONTEXT.0001` 变成 `AUTH6.PLATFORM_READ_DENIED`，不会变好。

## 后端链路本身可用（本机证据）

本机探针（未提交）：真实 `IdentityModule`（stub IdP、内存库）登录平台租户 → 真实 `PermissionModule`（内存策略库）安装三权职责并把该 person 指派为 `platform_system_admin` → 用 Bearer + context 调过滤器 → **通过**（200，进入下游）。即分支 3–6 在数据齐备时不拒。现有 `P1PlatformReadContextFilterTest` 的正向用例全部 mock 权限层，未覆盖这条真实链路（AUTH6F3 补）。

## 修复后仍可能拒的数据前提（需在复验时排除）

前端带上凭据后，若仍被拒，只可能是分支 3–6：

- token 过期：SPA 不刷新 access token，Keycloak 默认有效期较短；登录后过久再打开会 401。重新登录后立即复验。
- 平台租户 id 不一致：P1 的 `nexus.p1.platform-tenant.id` 与 U3 平台租户 `…0001` 不一致 → 403。
- 职责不恰好：person `…0222` 未挂 `platform_system_admin`（U2 未成功），或经 person / 岗位 / 部门另外持有安全或审计管理员 → 403。ID7 复用了演示库原有的 `platform_ops` 部门，若该部门另挂了职责角色，也会落入这一支。
- `tenant:list` 未安装或快照不可用 → 403。

## 结论与卡片

- **AUTH6F4（前端）是必须的修复**：两条只读列表改为带凭据（Bearer + `X-Nexus-Context-Id`）调用，并把 `AUTH6.PLATFORM_READ_DENIED` 映射成可读的错误说明。
- **AUTH6F2（后端）做最小改进，不改放行 / 拒绝结果**：拒绝 body 增加不敏感的 `reason`（区分上表各支），便于现场一眼定位；不需要 migration。
- **AUTH6F3**：补真实 IAM + 权限链路的测试（开关关→拒、开关开 + 平台运营→过、开关开 + 非平台→拒）。
- **AUTH6F5**：验收步骤、合入顺序、合入后重新部署提醒。

不需要改演示机 env；`NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED=true` 保持即可。
