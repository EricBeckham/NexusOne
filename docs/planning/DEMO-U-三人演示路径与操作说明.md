# DEMO-U 三人演示路径与操作说明（DEMO-U1）

> **预备 ≠ 验收。** 本文是演示剧本，不是真机结论。演示机只部署已合入 `main` 的构建并按本文操作；不热修 jar / SPA / class / env，不在任何文档、截图、日志里出现口令、token、cookie、OTP 或 client secret。验收勾选见 DEMO-U7。

对照：

- 身份表：[`demo-u3-identity-packs.md`](../engineering/runbooks/demo-u3-identity-packs.md)（DEMO-U3 #704）；
- Keycloak 用户：[`demo-u4-keycloak-three-users.md`](../engineering/runbooks/demo-u4-keycloak-three-users.md)（DEMO-U4 #702）；
- 平台职责：[`demo-u2-platform-duty-bootstrap.md`](../engineering/runbooks/demo-u2-platform-duty-bootstrap.md)（DEMO-U2 #703）；
- 平台只读列表：[`auth6-platform-p1-read-context.md`](../engineering/runbooks/auth6-platform-p1-read-context.md)（AUTH6 #700）；
- 鉴权收口：[`AUTH-演示鉴权验收清单.md`](AUTH-演示鉴权验收清单.md)（AUTH1–AUTH5）。

## 1. 三个角色

| 角色 | Keycloak 登录（Username 标签） | 邮箱（IAM） | 租户 | 主职（顶栏「岗位」） | 演示什么 | 不演示 / 应失败 |
|---|---|---|---|---|---|---|
| **平台运营** | `nexus-demo-platform` | `platform.admin@demo.invalid` | 平台租户 `…0001` | 平台运营（`demo_platform_ops`） | P1 **租户目录**、**租户注册审订**列表 | **不演示报表 / 导出**；不进租户业务 |
| **租户管理（林晓）** | `nexus-demo-tenant-admin` | `lin.xiao@demo.invalid` | `demo_acme` `…0101` | 租户管理（`demo_tenant_admin`） | P2 **组织**（组织架构、用户目录、岗位任职）；P3 **报表 / 导出** | 没有平台职责，不进平台页 |
| **一般用户（周然）** | `nexus-demo-member` | `zhou.ran@demo.invalid` | `demo_acme` `…0101` | 一般用户（`demo_member`） | P3 **工作台**（待办、我的申请、通知等日常业务） | 导出、管理页 → **`no_grant`**；**无平台**（平台列表 401/403，平台页进不去） |

Keycloak 若配置「邮箱作为用户名」，登录框填邮箱；上表 Username 仍是清单标签。三人各用**独立浏览器会话**（不同浏览器 / 隐身窗口 / 浏览器配置文件），不共用 cookie，不代登录。

**导出只由租户管理演示，不由平台运营演示。** 导出属于租户侧资源（`ledger.export.csv` / 报表导出），平台职责（DEMO-U2）刻意不授予它；平台运营去点导出得到的 `no_grant` 是设计如此，不是故障，也不要在演示里拿它顶替租户导出。

## 2. 导出成功的前提（必须先核对）

**当前演示种子单独不会让租户管理导出成功——会是 `no_grant`（`P2.PERM.0003`）。** DEMO-U2 / U3 只准备了平台职责和三人身份、岗位，没有给 `demo_acme` 授予导出。

租户管理演示「导出成功」之前，必须满足二者之一，并在演示前核对：

1. **DEMO-U5 已合入 `main`** 且本次部署的构建包含它（记录发布 SHA）；或
2. 由受控 IAM / 权限管理员在 `demo_acme` 上**受控授予**租户管理（林晓）导出权限，并有运维记录编号。

两者都不满足：导出步骤按「前提缺失」记卡点，**不演示导出成功**，不在演示机手改 SQL / 权限表 / env 去“先跑通”。

一般用户（周然）的工作台同理：若 P3 工作台的读取（例如待办）也返回 `no_grant`，说明成员的最小业务授权尚未准备，记卡点，不当作「一般用户期望失败」混过去。

## 3. 开场准备（每位演示者各自一次）

1. 用已合入 `main` 的构建完成部署，记下**发布 SHA**。
2. 浏览器打开演示地址后**硬刷新**（Windows `Ctrl + Shift + R` / `Ctrl + F5`；macOS `Cmd + Shift + R`），确保拿到本次部署的 SPA，而不是缓存的旧包。
3. 若此前在该浏览器登录过别的身份：先在右上角点「退出」（AUTH3 会清会话并回到 `/login`），或直接使用新的隐身窗口。
4. 未登录时打开任意桌面页（如 `/`、`/p4`）应被送到 `/login`；这一步本身就是 AUTH3 的检查点。

## 4. Keycloak 登录（三人相同的步骤）

1. `/login` 页点 Keycloak 登录入口（issuer 已配置时是主按钮；不要用本地 stub 入口）。
2. 在 Keycloak 登录页输入该角色的登录名与口令（口令只经 IdP / 密钥交付渠道取得，不写在任何地方）；完成 IdP 要求的 required actions / MFA。
3. 回跳 `/callback` 后应进入应用首页（产品选择 `/`）。
   - 一个 ACTIVE 成员资格时，服务端直接签发租户上下文；
   - 多个 ACTIVE 时前端自动进入 `demo_acme`（AUTH5），右上角仍可手动切换；
   - 若出现「缺少 TenantContext」页：该身份的 person / membership 未准备好，记卡点，返回登录，不要换别人的账号顶替。
4. 打开右上角用户菜单核对：**用户名**、**租户**（当前租户）、**岗位**应分别是本人与上表主职；「平台操作」开关只对平台运营有意义。

## 5. 三条演示路径

### 5.1 平台运营（`nexus-demo-platform`）

前置（部署配置，由环境所有者设置，不由演示者临时改）：DEMO-U2 平台职责已安装一次；`NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED=true`（AUTH6）；所有 demo 开关保持 `false`。

1. 登录后确认租户为平台租户、岗位为「平台运营」。
2. 顶栏打开「平台操作」。
3. 进入 **星域 → 租户目录**（`/p1/tenants`）：列表返回并显示租户（至少可见 `demo_acme`）。
4. 进入 **租户注册审订**（`/p1/tenant-registrations`）：列表返回（没有待审时是诚实空态，不是报错）。
5. 本角色**不演示**报表 / 导出，也不进入租户业务页。
6. 右上角「退出」→ 回到 `/login`，浏览器后退不应回到平台页。

期望失败（可选演示）：无——平台运营的演示只有上面两张只读列表；注册详情、审批、写操作不在 AUTH6 桥接范围内。

### 5.2 租户管理（林晓，`nexus-demo-tenant-admin`）

1. 硬刷新后登录；确认租户 `demo_acme`、岗位「租户管理」。
2. **P2 组织**：
   - 组织架构（`/p2/org`）：可见演示部门；
   - 用户目录（`/p2/persons`）：可见林晓、周然；
   - 岗位任职（`/p2/positions`）：可见 `demo_tenant_admin`、`demo_member` 等岗位。
3. **P3 报表 / 导出**（先按 §2 确认前提）：
   - 报表（`/p3/reports`）：查询可用；页面上「导出权限」应显示允许；
   - 导出向导（`/p3/exports/wizard`）：选择台账 / 报表并执行导出，得到导出结果（文件或导出任务完成）。
   - **前提不满足时**：页面会显示「未获授权（no_grant）」。这时停在这里，按 §2 记卡点，不继续假装成功。
4. 平台页不属于本角色：直接打开 `/p1/tenants` 应被拒绝（无平台职责），不要为了演示给林晓挂平台职责。
5. 「退出」→ `/login`。

### 5.3 一般用户（周然，`nexus-demo-member`）

1. 硬刷新后登录；确认租户 `demo_acme`、岗位「一般用户」。
2. **P3 工作台**（业务可用）：进入星构首页 / 待办 / 我的申请 / 通知，正常浏览与办理本人的业务。
3. **期望失败（要演示给观众看）**：
   - 导出：打开导出向导（`/p3/exports/wizard`）或报表的导出 → 显示「未获授权（no_grant）」；
   - 管理：打开组织管理 / 岗位任职等管理页 → 服务端拒绝（`no_grant` 或 403），页面给出诚实错误态，而不是空白或假数据；
   - 平台：打开 `/p1/tenants` → 被拒绝；周然没有平台租户成员资格，也没有平台职责。
4. 「退出」→ `/login`。

## 6. 换人

- 同一浏览器换人：**必须先「退出」**，确认回到 `/login` 且后退不会回到上一位的页面，再登录下一位；更稳妥的做法是每人一个独立隐身窗口 / 浏览器配置文件。
- 不在同一会话里「切换租户」来冒充另一个人；租户切换只用于同一人拥有多个 ACTIVE 成员资格的情形。
- Keycloak 侧若保留了上一位的 IdP 会话（单点登录 cookie），会导致下一位直接以上一位身份回跳：此时在 Keycloak 登录页 / 账户页先登出，或改用新的隐身窗口。

## 7. 出问题时

只记：发布 SHA、角色标签、页面路径、接口路径、脱敏错误类别（如 `no_grant`、`P1.CONTEXT.0001`、缺少 TenantContext）、时间。不记 token、口令、cookie、完整回跳 URL 的 query。

不做：SSH 到演示机改包 / 改 env / 改库；打开 `NEXUS_DEMO_ENABLED` 或 `NEXUS_P1_DEMO_CONTEXT_ENABLED` 来“修”；用 stub token 或演示身份头冒充。修复一律经 GitHub 合入 `main` 后重新部署。
