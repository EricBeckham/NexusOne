# TC5 三人登录带 TenantContext 验收清单

> **预备 ≠ 验收。** 由环境所有者在**已合入 `main` 的构建**上逐项勾选；代理不代勾、不登录、不操作演示机。演示机不热修（jar / SPA / class / env / 库），产品改动只经 GitHub 合入 `main` 后重新部署。不记录口令、token、cookie、OTP、client secret、授权码或完整回跳 URL。

范围：LOGIN6（#714，产品 Keycloak 回跳走服务端换码）与 DEMO-U 系列之后，三个演示身份经 Keycloak 登录时**带着租户上下文**进入首页，顶栏用户 / 租户 / 岗位正确。整场演示的全流程勾选见 [`DEMO-U7-三人演示验收清单.md`](DEMO-U7-三人演示验收清单.md)，本清单只覆盖「登录 → 有 TenantContext → 首页 → 顶栏」这一段。

相关已合入：LOGIN6 #714、TC2 #716（授权码登录无 ACTIVE 成员资格即拒绝）、TC3 #715（回跳进不了租户时的诚实壳）、AUTH3 #695、AUTH4 #698、AUTH5 #701、DEMO-U2 #703、DEMO-U3 #704、DEMO-U5 #705、DEMO-U6 #707、AUTH6 #700。

## 0. 前提（先勾，不满足即停）

- [ ] 发布 SHA（`main`）：`________________`，晚于 TC2（#716）；要求 U2 窗口插入身份时须含 ID1 / ID3（#720）。
- [ ] 部署的就是该 SHA 的构建；未在演示机热修。
- [ ] `NEXUS_DEMO_ENABLED`、`NEXUS_P1_DEMO_CONTEXT_ENABLED` 为 `false` 或未设置。
- [ ] Keycloak 三个用户按 [DEMO-U4](../engineering/runbooks/demo-u4-keycloak-three-users.md) 创建 / 核对，邮箱与 [DEMO-U3](../engineering/runbooks/demo-u3-identity-packs.md) 冻结表一致。
- [ ] DEMO-U2 一次性窗口已执行过一次并关闭（`NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED=true` 重启一次 → 设回 `false` 再重启）。它负责：
  - 平台职责；
  - 三人身份与岗位对齐（DEMO-U3；含 ID1 的构建会先插入缺失的固定身份，见 §0.1）；
  - 同一账号不同时在两个租户 ACTIVE；
  - `demo_acme` 角色分异（DEMO-U5）。
- [ ] **仅平台运营需要**：`NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED=true`（AUTH6），由部署配置设置。没有它，平台运营能登录、能带平台租户上下文进首页，但租户目录 / 注册审订列表不会返回数据（`P1.CONTEXT.0001` 或 401 / 403）。

### 0.1 ID1 身份插入之后（发布 SHA 含 ID1 / ID3 #720 `07198104`）

含 ID1 的构建里，U2 窗口先插入 / 对齐三套固定身份，再装职责、再给 `…0222` 授权（顺序见 [ID1 / ID3](../engineering/runbooks/id1-u2-identity-bootstrap.md)）。这一过程不需要 `NEXUS_DEMO_ENABLED`，演示机仍禁止打开它。下列核对只读，走已批准的只读查询或管理视图；不手写 SQL 补行，不记录 subject、token 或凭证。

- [ ] 开窗口前已清孤儿：三个 U3 邮箱在 `iam_global_account`（含软删除行）上要么 0 行，要么就是固定 id `…0221` / `…0201` / `…0202`（见 [TC4](../engineering/runbooks/tc4-demo-u2-after-keycloak-checklist.md)）。
- [ ] U2 窗口那次启动成功完成，日志中没有 `ID1 identity conflict`，也没有其他拒启。失败的启动不算通过。
- [ ] person `…0222` 存在，归属账号 `…0221` 与平台租户 `…0001`。
- [ ] `…0222` 已挂到 `platform_system_admin`，没有指向不存在 person 的悬空授权。
- [ ] person `…0203` / `…0204` 存在，分别归属 `…0201` / `…0202` 与 `demo_acme` `…0101`。
- [ ] 三人**各恰好 1 条 ACTIVE membership**：平台运营只在 `…0001`，林晓、周然只在 `…0101`，其余为 SUSPENDED。
- [ ] 三人各有 1 条 ACTIVE PRIMARY 主职，岗位 code 分别是 `demo_platform_ops`、`demo_tenant_admin`、`demo_member`。
- [ ] 窗口已关闭（`NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED=false` 并重启），再启动不追加身份或授权。
- [ ] 随后按 §1 / §2 登录：三人**均进首页，不再出现缺 TenantContext**，既没有「还没有可进入的租户」壳，也没有「未能自动进入租户」壳。仍出现时记卡点，回到本节逐项核对，不在演示机手补。

## 1. 每位验收者的通用步骤

每人使用独立的隐身窗口 / 浏览器配置文件；同一窗口换人前先「退出」，并在 Keycloak 侧确认不是上一位的 SSO 会话。

1. [ ] 打开演示地址后**硬刷新**（Windows `Ctrl + Shift + R` 或 `Ctrl + F5`；macOS `Cmd + Shift + R`）。
2. [ ] 未登录直接打开 `/` 或 `/p4` → 被送到 `/login`（AUTH3）。
3. [ ] `/login` 点 Keycloak 登录（产品入口，不是开发者 stub 入口）→ Keycloak 登录页完成登录与 required actions / MFA。
4. [ ] 回到 `/callback` 时，页面**不出现**「本机找不到 PKCE 校验值」一类提示（LOGIN6：产品回跳由服务端换码）。
5. [ ] 自动落到**首页**（产品选择 `/`），不是停在 `/callback`，也不是「未能自动进入租户」/「还没有可进入的租户」壳。
6. [ ] 打开右上角用户菜单，核对三项（见 §2）；「租户」显示名称或 code，**不是 UUID**（DEMO-U6）；「岗位」是主职名称，不是「暂无」（AUTH4）。
7. [ ] 「刷新会话」后三项不变；硬刷新页面后仍在已登录状态、三项不变。
8. [ ] 「退出」→ `/login`；浏览器后退不回到刚才的页面。

## 2. 三人期望

| 身份 | Keycloak 登录（Username 标签） | 用户菜单「用户名」 | 「租户」 | 「岗位」 | 勾选 |
|---|---|---|---|---|---|
| 平台运营 | `nexus-demo-platform` | 平台管理员（或 IdP 显示名） | 平台租户的名称 / code | 平台运营 | [ ] |
| 租户管理（林晓） | `nexus-demo-tenant-admin` | 林晓 | `demo_acme` 的名称（如「演示租户 · 星河科技」）或 `demo_acme` | 租户管理 | [ ] |
| 一般用户（周然） | `nexus-demo-member` | 周然 | 同上 | 一般用户 | [ ] |

补充：

- [ ] 三人各自只有**一个** ACTIVE 成员资格（DEMO-U3 规则），因此服务端在登录时直接签发上下文，不需要前端自动切换；若用户菜单出现「切换租户」下拉，说明某账号在两个租户都是 ACTIVE，记卡点（回到 DEMO-U2 / U3 核对），不要手动切换后当作通过。
- [ ] **平台运营**：登录后打开顶栏「平台操作」进入 `/p1/tenants` 与 `/p1/tenant-registrations`，列表返回。前提是 §0 的 **DEMO-U2 已执行**（平台租户有 `platform_system_admin` 职责和 `tenant:list`）且 **AUTH6 开关已开**；任一缺失时列表不返回，这是前提缺失，不是 TenantContext 问题。
- [ ] 租户管理、一般用户打开 `/p1/tenants` 被拒绝（不属于平台租户、无平台职责）。

## 3. 反例（可选，用于确认拒绝是诚实的）

- [ ] 用一个在 IAM 中**没有任何 ACTIVE 成员资格**的受控测试账号登录：
  - 授权码登录被服务端拒绝（TC2，`P2.IAM.NO_ACTIVE_MEMBERSHIP`，403）；
  - 回跳页显示失败壳，没有进入首页，也没有显示别人的租户。
  - 注：当前回跳页对这个 403 显示通用「回调失败 · 换会话没有完成」文案，而不是 TC3 的「还没有可进入的租户」壳；两者都算正确拒绝，文案差异另记，不在演示机处理。
- [ ] 任何情况下页面与记录中都不出现授权码、token 或 `code_verifier`。

## 4. 记录

只记：发布 SHA、身份标签、步骤编号、结论（通过 / 卡点）、脱敏错误类别（如 `P2.IAM.NO_ACTIVE_MEMBERSHIP`、`P1.CONTEXT.0001`、`no_grant`）、证据编号。卡点经 GitHub 开卡 → 合入 `main` → 重新部署后复验；**不在演示机热修**，不打开 demo 开关，不用 stub token 或演示身份头冒充。

结论：________　执行人：________　日期：________　发布 SHA：________
