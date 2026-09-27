# DEMO-U7 三人演示验收清单

> **预备 ≠ 验收。** 本清单由环境所有者在**已合入 `main` 的构建**上逐项勾选；代理不代勾、不登录、不操作演示机。不记录口令、token、cookie、OTP、client secret 或完整回跳 URL。

操作剧本：[`DEMO-U-三人演示路径与操作说明.md`](DEMO-U-三人演示路径与操作说明.md)（DEMO-U1）。身份：[`demo-u3-identity-packs.md`](../engineering/runbooks/demo-u3-identity-packs.md)；Keycloak 用户：[`demo-u4-keycloak-three-users.md`](../engineering/runbooks/demo-u4-keycloak-three-users.md)；权限分异：[`demo-u5-acme-role-split.md`](../engineering/runbooks/demo-u5-acme-role-split.md)；平台职责：[`demo-u2-platform-duty-bootstrap.md`](../engineering/runbooks/demo-u2-platform-duty-bootstrap.md)；平台只读列表：[`auth6-platform-p1-read-context.md`](../engineering/runbooks/auth6-platform-p1-read-context.md)。

## 0. 发布与纪律（先勾）

- [ ] 记录**发布 SHA**（`main`）：`________________`。本次构建包含 ID1 / ID3（#720）、DEMO-U2（#703）、DEMO-U3（#704）、DEMO-U5（#705）、DEMO-U6（#707）及 AUTH1–AUTH6。
- [ ] 部署产物即该 SHA 的构建；**没有**在演示机热修 jar / SPA / class，**没有**用 env 改产品语义，**没有**手改数据库或权限表。
- [ ] `NEXUS_DEMO_ENABLED`、`NEXUS_P1_DEMO_CONTEXT_ENABLED` 为 `false`（或未设置）。
- [ ] 一次性窗口已按 DEMO-U2 执行过一次：`NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED=true` 重启一次（安装平台职责、对齐三人身份、按 DEMO-U5 安装 `demo_acme` 的 `tenant_admin` / `tenant_member`），之后已设回 `false` 并再次重启。
- [ ] **ID1 身份插入后（发布 SHA 含 #720 `07198104` 和 ID7 #723）**，按 [TC5 §0.1](TC5-三人登录TenantContext验收清单.md) 勾完：
  - 开窗前三人邮箱孤儿已清；
  - U2 窗口那次启动成功，无 `ID1 identity conflict`、无 `DuplicateKey` / `uk_iam_department_code`、无 `ID7 department code is unavailable`；
  - person `…0222` 存在并挂在 `platform_system_admin`；
  - 三人各恰好 1 条 ACTIVE membership；
  - 三人登录均进首页，不再缺 TenantContext。

  纯 #720 包在已有 `platform_ops` 部门的演示库上会在部门插入处失败，见 [TC4](../engineering/runbooks/tc4-demo-u2-after-keycloak-checklist.md#部门-code-冲突id7)。重新部署含 ID7 的包、再跑 U2 与登录验收之前，不宣称演示机已修好。
- [ ] 平台只读列表开关 `NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED=true`（AUTH6）由部署配置明确设置。
- [ ] Keycloak 三个用户已按 DEMO-U4 创建 / 核对，邮箱与 DEMO-U3 冻结表一致。

任一项不满足：停止，记卡点，不进入后续步骤，不在演示机「先修一下」。

## 1. 硬刷新

- [ ] 每个演示浏览器 / 隐身窗口打开演示地址后执行**硬刷新**（Windows `Ctrl + Shift + R` 或 `Ctrl + F5`；macOS `Cmd + Shift + R`）。
- [ ] 未登录时打开 `/` 或 `/p4` → 进入 `/login`（AUTH3）。
- [ ] 登录页主按钮是 Keycloak 入口，不是本地 stub。
- [ ] `/login` **不出现**「登录服务暂未配置」。出现即 SPA 打包漏了 Keycloak 公开变量，按 [PKG1](../engineering/runbooks/pkg1-spa-keycloak-public-env.md) 重新打包部署；不记成 U2 / TenantContext 问题。

## 2. 三人分别登录（各自独立会话）

每人一个独立隐身窗口 / 浏览器配置文件；同一窗口换人前先「退出」，并确认后退回不到上一位的页面。

| 身份 | 登录（Username 标签） | 登录后应看到（用户菜单） | 勾选 |
|---|---|---|---|
| 平台运营 | `nexus-demo-platform` | 租户 = 平台租户名称；岗位 = 平台运营 | [ ] |
| 租户管理（林晓） | `nexus-demo-tenant-admin` | 租户 = `demo_acme` 的**名称**（如「演示租户 · 星河科技」），不是 UUID（DEMO-U6）；岗位 = 租户管理 | [ ] |
| 一般用户（周然） | `nexus-demo-member` | 租户 = `demo_acme` 名称；岗位 = 一般用户 | [ ] |

- [ ] 三人都经 Keycloak 登录并回跳成功；没有出现「缺少 TenantContext」页（出现即记卡点：person / membership 未准备）。含 ID1 的构建做过 U2 窗口后，这一页**不应再出现**；仍出现时先核对 §0 的 ID1 项（窗口是否真的成功执行、孤儿是否已清），不在演示机手补身份。
- [ ] 用户菜单与切换下拉中**没有**只显示一串 UUID 的租户。
- [ ] **产品范围（PV，发布 SHA 含 PV2–PV4）**：平台运营的产品切换只有星域 / 星权 / 星市；林晓只有星权 / 星构 / 星市（星联仅在租户开通后出现）；周然登录后直接进入星构工作台，没有产品切换。逐项见 [PV5](PV5-三类用户产品范围验收.md)。

## 3. 平台运营开平台页

- [ ] 打开顶栏「平台操作」后进入 **租户目录** `/p1/tenants`：列表返回，至少可见 `demo_acme`。
- [ ] 进入 **租户注册审订** `/p1/tenant-registrations`：列表返回（无待审时为诚实空态，不是报错）。
- [ ] 平台运营**不做**导出；若误点导出得到 `no_grant`，属设计如此（导出归租户管理），不记为失败、也不作为导出演示。

## 4. 租户管理导出成功

- [ ] 组织：`/p2/org`、`/p2/persons`、`/p2/positions` 均可打开并显示演示数据。
- [ ] 报表 `/p3/reports`：查询可用，「导出权限」显示允许。
- [ ] 导出向导 `/p3/exports/wizard`：执行导出，得到导出结果（文件或导出任务完成）。
- [ ] 若此处出现「未获授权（no_grant）」：核对 §0 的发布 SHA 是否含 DEMO-U5（#705）以及一次性窗口是否执行过；记卡点，**不**手工授权、**不**改 SQL。
- [ ] 打开 `/p1/tenants` 被拒绝（林晓没有平台职责）。

## 5. 一般用户：业务可用，导出 / 管理 `no_grant`

- [ ] 星构工作台（待办 / 我的申请 / 通知）可用。若工作台读取也被拒，记「成员最小授权缺失」，不与下面的期望失败混记。
- [ ] 导出：`/p3/exports/wizard`（或报表导出）显示「未获授权（no_grant）」（`P2.PERM.0003`）。
- [ ] 管理：组织 / 岗位等管理操作被服务端拒绝（`no_grant` 或 403），页面为诚实错误态，不是空白或假数据。
- [ ] 周然直接输入 `/p2/org`、`/p1/tenants`、`/p4` 等地址：前端显示「无权访问该产品」；服务端对应接口仍为 `no_grant` / 403（PV3 只是导航，不替代服务端拒绝）。
- [ ] 周然保存 / 发布流程定义被拒（`no_grant`，PV4）；填表提交、发起 / 撤回申请、审批待办仍可用。
- [ ] 平台：`/p1/tenants` 被拒绝；周然无平台租户成员资格、无平台职责。

## 6. 收尾

- [ ] 三人分别「退出」→ `/login`；浏览器后退不回到已退出用户的页面。
- [ ] 结果记录只含：发布 SHA、身份标签、页面 / 接口路径、结论（通过 / 卡点）、脱敏错误类别、证据编号。
- [ ] 所有卡点经 GitHub 开卡、合入 `main`、重新部署后再复验；**禁止演示机热修**（不 SSH 改包 / env / 库，不打开 demo 开关，不用 stub token 或演示身份头冒充）。

结论（Go / No-Go）：________　执行人：________　日期：________　发布 SHA：________
