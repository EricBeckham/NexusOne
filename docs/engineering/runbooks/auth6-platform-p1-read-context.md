# AUTH6 · 真实平台上下文到 P1 只读列表桥接

选择真实 IAM TenantContext → P1 IsolationSnapshot 桥接，不新增演示 profile、不信任主机/IP 或 demo 身份头。**本机契约测试 ≠ 演示机验收**；本卡未连接、修改或部署演示机。

## 启用 / 关闭

- 默认关闭：`NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED=false`（配置 `nexus.p1.platform-read-context.enabled`）。
- 仅需要真实平台只读目录时设为 `true` 后重启。可用于 prod；不是开启 demo context。`NEXUS_P1_DEMO_CONTEXT_ENABLED` 必须保持 `false`，其它 demo 开关亦按 prod 守卫保持关闭。不能以此绕过 IdP/MFA/权限初始化。
- 关闭：删除该变量或设为 `false` 后重启；恢复原有 P1 隔离校验，不回退到 demo 头。
- 前置：真实 IAM/permission 持久化、受控平台租户 membership、服务端签发 context；平台租户须匹配 `P1Properties.platformTenant.id`（默认平台租户），不可仅凭客户端 tenant_id。系统管理员职责与 `tenant:list` 授权须由受控管理员配置，桥接不种角色/授权。

## 安全边界

仅桥接 `GET /api/v1/p1/tenants` 和 `GET /api/v1/p1/tenant-registrations`（含列表 status 过滤）。注册审订列表沿用 P1 的 SYSTEM_ADMIN 职责，并额外要求现有 `tenant:list` 权限；不新增权限目录语义。

每次重新认证 Bearer，通过 `SessionService.loadContext` 校验 context 归属、有效期、吊销状态与既有 MFA 要求；必须属于配置的平台租户且有 person。权限快照的展开职责必须恰为 `platform_system_admin`，再检查 `tenant:list`（DENY/不可用快照均不放行）。切入平台租户本身不构成管理员授权。

拒绝 `X-Tenant-Id`；demo actor/reason 头不能替代认证。reason 为服务端固定的只读操作理由，不接受客户端跨租户授权理由。临时快照只含 SYSTEM_ADMIN，不含 ALL_DEMO；请求完成或异常后恢复/清除上下文。响应仅固定拒绝信息，无 token、cookie 或上下文详情。

POST/PUT/DELETE、租户详情、注册详情/approve/reject、其它平台接口均不桥接；本卡不解锁写操作。无凭据/无有效 context 通常 401，非平台租户、无职责或无权限 403，客户端 tenant header 400；关闭时仍可能原有 `P1.CONTEXT.0001`。

## 验收

本机 MockMvc：真实控制器 → 真实列表 service → ThreadLocalIsolationGuard，demo 关闭，两列表返回 200；IAM/权限及存储边界使用 mock（非真实 Keycloak/数据库验收）。覆盖 token/context 拒绝、普通租户、member/security/audit 职责、职责冲突、权限拒绝、关闭/写接口不绑定、异常清理。

另使用真实 SessionService + 内存 IAM + stub IdP 验证签发、跨身份归属拒绝、吊销和过期；权限边界仍为 mock。既有 MFA/session 与 prod 配置拒绝测试独立回归。stub IdP 不代表真实 Keycloak 验收。

部署后由授权运维仅部署与测试：使用真实登录、平台切换后的 Bearer/context 请求两列表，核 200 与实际数据；普通租户或无职责账号应 403；关闭开关应不再桥接；同时确认 prod demo context 仍为 false。不把空列表契约测试当作已完成真实数据或真机验收，不在文档/日志保存凭据。
