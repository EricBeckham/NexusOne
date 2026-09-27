# AUTH6G6 · 平台只读四页：合入顺序与验收

只写流程与勾选项，不代表已部署或已验收。演示机不热修，不改任何 `NEXUS_*` env。不记录 token、context id、person id 原值。诊断见 [AUTH6G1](AUTH6G1-平台只读页上下文缺口诊断.md)。

## 1. 合入顺序

| 顺序 | 卡 | PR | 内容 | 基 |
|---|---|---|---|---|
| 1 | G1 | #740 | 诊断（docs） | main |
| 2 | G2 | #741 | 桥：关系 / 服务态 / 表单模板只读 GET（SYSTEM_ADMIN） | main |
| 3 | G3 | #742 | 桥：审计事件检索（AUDIT_ADMIN） | 叠在 G2 |
| 4 | G4 | #743 | 后端路由测试 | 叠在 G3 |
| 5 | G5 | #744 | 前端：关系 / 服务态 / 表单模板 helper 带凭据 | main |
| 6 | G6 | 本卡 | 前端：审计 helper、共享 `AUTHENTICATED_PLATFORM_READ`、测试、本文 | 叠在 G5 |

按 G1 → G2 → G3 → G4 → G5 → G6 依次合；叠加的 PR 在前一张合入后由 GitHub 自动改基到 `main`。

## 2. 部署

- [ ] 记录包含 G2、G3、G5、G6 的 `main` 发布 SHA：`________`。
- [ ] jar 与 SPA 从同一 SHA 构建；SPA 按 [PKG1](../engineering/runbooks/pkg1-spa-keycloak-public-env.md) 注入 Keycloak 公开变量并在上传前检查。
- [ ] env 不变：`NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED=true` 保持；演示开关保持 false。
- [ ] 只部署，不热修。

## 3. 验收（硬刷新后，登录后立即操作）

### 平台运营 `nexus-demo-platform`

- [ ] 租户关系 `/p1/relations`：关系树加载；点开一个租户，子节点 / 祖先加载。
- [ ] 平台服务态 `/p1/services`：服务状态加载。
- [ ] 表单模板 `/p1/form-templates`：列表加载；点开一个模板，编辑器读取成功。
- [ ] 平台审计中心 `/p1/audit`：**应被拒**，文案为「审计中心只对「审计管理员」开放（三权分立）……」，末尾 `（AUTH6.PLATFORM_READ_DENIED · duty_set）`。这是预期结果：平台运营只持系统管理员职责。审计列表需要单独的审计管理员身份，本波不提供。
- [ ] 以上四页都不再出现 `P1.CONTEXT.0001`。
- [ ] 开发者工具「网络」面板：上述 GET 请求头**存在** `Authorization` 与 `X-Nexus-Context-Id`，**不存在** `X-Tenant-Id`。只确认有无，不截取、不记录值。
- [ ] 写操作不在本波范围：改挂上级、新建 / 保存 / 发布表单模板、哈希链校验若失败，记为已知范围外，不当作本波回归。

### 林晓 `nexus-demo-tenant-admin`、周然 `nexus-demo-member`

- [ ] 直接打开 `/p1/relations`、`/p1/services`、`/p1/form-templates`、`/p1/audit`：均被拒，文案为「当前所在租户不是平台租户；只有平台运营可以查看。」（`not_platform_context`），不显示任何平台数据。

## 4. 仍被拒时

页面错误行末尾带 `（AUTH6.PLATFORM_READ_DENIED · <reason>）`，按 [AUTH6F5 §4](AUTH6F5-平台只读列表修复验收与合入顺序.md) 的表处理。仍出现 `P1.CONTEXT.0001`，说明部署的 jar 不含 G2 / G3，或 SPA 不含 G5 / G6：按第 2 节重新打包部署，不在演示机热修。

结论：________　执行人：________　日期：________　发布 SHA：________
