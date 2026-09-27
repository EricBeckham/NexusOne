# AUTH6F5 · 平台只读列表修复：合入顺序与验收

本文只写流程与勾选项，不改产品代码，不代表已部署或已验收。演示机不热修（jar / SPA / class / env / 库），不写口令、token、client secret。

根因见 [AUTH6F1](AUTH6F1-平台只读列表拒绝定位.md)：前端两条平台只读列表以 `auth: false` 调用，不带 Bearer 与 `X-Nexus-Context-Id`，AUTH6 桥因此恒拒。

## 1. 合入顺序

| 顺序 | 卡 | PR | 内容 | 备注 |
|---|---|---|---|---|
| 1 | AUTH6F1 | #735 | 定位说明（docs） | 独立 |
| 2 | AUTH6F2 | #736 | 拒绝 body 增加 `reason`（后端，放行 / 拒绝结果不变） | 独立 |
| 3 | AUTH6F3 | #737 | 真实 IAM + 权限链路测试 | **叠在 F2 上**；F2 合入后 GitHub 自动改基到 `main` |
| 4 | AUTH6F4 | #738 | 两条列表带凭据调用 + 拒绝文案（前端） | 独立；**真正修复在这张** |
| 5 | AUTH6F5 | 本卡 | 验收与顺序（docs） | 独立 |

F4 单独合入即可让列表恢复；F2 让仍被拒时能看出原因。建议 F1 → F2 → F3 → F4 → F5 依次合，全部合完再打一次包。

## 2. 合入后重新部署（提醒）

- [ ] 记录包含 F2 与 F4 的 `main` 发布 SHA：`________`。
- [ ] 后端 jar 与 SPA **都**从这个 SHA 构建。只换 jar 不修前端；只换 SPA 看不到 `reason`。
- [ ] SPA 按 [PKG1](../engineering/runbooks/pkg1-spa-keycloak-public-env.md) 构建：注入 Keycloak 公开 VITE 变量，上传前检查 `keycloakPublic-*.js`。
- [ ] 演示机 env 不需要改：`NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED=true` 保持；`NEXUS_P1_DEMO_CONTEXT_ENABLED` / `NEXUS_DEMO_ENABLED` 保持 false。
- [ ] 只部署、不热修；部署后按第 3 节复验。

## 3. 验收（环境所有者勾选）

前提：平台运营 `nexus-demo-platform` 的身份与职责已按 DEMO-U2 / ID1 / ID7 就绪（见 [TC5 §0.1](TC5-三人登录TenantContext验收清单.md)）。

1. [ ] 硬刷新后，用 `nexus-demo-platform` 经 Keycloak 登录，顶栏租户为平台租户、岗位为「平台运营」。
2. [ ] **登录后立即**打开「平台操作」→ 租户目录 `/p1/tenants`：列表返回，至少可见 `demo_acme`；不再出现「租户目录暂时无法加载」。
3. [ ] 打开注册审订 `/p1/tenant-registrations`：列表返回；无待审时为诚实空态。
4. [ ] 浏览器开发者工具「网络」面板中，`GET /api/v1/p1/tenants` 的请求头带 `Authorization` 与 `X-Nexus-Context-Id`（只核对**是否存在**，不截取、不记录值）。
5. [ ] 林晓（`nexus-demo-tenant-admin`）、周然（`nexus-demo-member`）打开 `/p1/tenants` 被拒，页面文案为「当前所在租户不是平台租户；只有平台运营可以查看。」（`not_platform_context`）。

## 4. 仍被拒时怎么读

页面错误行末尾会带 `（AUTH6.PLATFORM_READ_DENIED · <reason>）`。按 reason 处理，**都不在演示机热修**：

| reason | 含义 | 处理 |
|---|---|---|
| `missing_credentials` | 请求没带 Bearer / context | 部署的 SPA 不含 F4，或未重新构建；按第 2 节重新打包部署 |
| `session_invalid` | token 过期或 context 失效 | 退出后重新登录，登录后立即复验（SPA 不刷新 access token） |
| `session_forbidden` | context 与登录账号不一致 | 退出重登；仍出现则记卡点 |
| `mfa_required` | 平台管理员 MFA 未满足 | 按 MFA 口径（MFA2）核对，不在演示机改开关绕过 |
| `not_platform_context` | 当前不在平台租户 | 用平台运营账号登录；平台运营仍出现则核对 P1 平台租户 id 与 U3 `…0001` 一致 |
| `duty_set` | 职责不恰好是 `platform_system_admin` | 核对 DEMO-U2 是否成功指派 `…0222`；是否经部门 / 岗位另持安全或审计管理员（ID7 复用了原有 `platform_ops` 部门） |
| `permission_denied` | 无 `tenant:list` | 核对 DEMO-U2 职责安装是否完整 |
| `snapshot_unavailable` | 权限快照不可用 | 稍后重试；持续出现记卡点 |

记录只写发布 SHA、身份标签、页面路径、结论、reason 与证据编号；不记录 token、context id、person id 原值。卡点经 GitHub 开卡 → 合入 `main` → 重新部署后复验。

结论：________　执行人：________　日期：________　发布 SHA：________
