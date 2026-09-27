# DL-X1 · 真机演示 Runbook

> **预备 ≠ 验收。** 本册只给真机操作步骤，E1–E7 未由环境所有者关闭前，不宣称已部署或 Keycloak 已验收。无真实 env 值、secret 或代签。

本机 stub 路径单独使用 [本地请假报销 runbook](demo-simple-approval.md)，不可将其 demo 开关、种子账号或 reset 命令复制到真机。真机用 `prod`，demo flags 全部 OFF。

## 1. 发布前门禁

- 对照 [DL 规划 §2.2](../../planning/WEB上线演示任务卡_DL波_V0.1.md)：E1 主机权限、E2 DNS/TLS、E3 vault、E4 realm/client/redirect、E5 受控账号、E6 SPA/回调反代、E7 制品与真机验收。逐项填写证据，不能用代码合入代替。
- 按 [M7 §1](../../planning/M7-Keycloak演示验收清单.md) 准备专用 confidential client、受控账号及 membership；凭据只在 IdP/vault，记录仅显示名与 subject 类型。
- 依 [prod 部署 checklist](demo-host-prod-deployment.md) 完成依赖、profile、DB、crypto master key、IAM/permission JDBC、证书与配置注入。只记录变量名/发布 SHA，不记录值。
- 发布 JAR 与 SHA-256 匹配已合 main 的 SHA；Flyway validation 不跳过，outOfOrder 不开启。任何门禁失败即停止，不开 demo seed、不关闭 TLS 校验补救。

## 2. 内容与登录

具体内容准备按 [Prod-safe 内容手册](demo-host-prod-safe-content.md) 执行；空列表/缺目录记卡点，不开自动 seed。

1. 保持 `NEXUS_DEMO_ENABLED` 和 demo-context 相关开关关闭；使用独立最小内容，不复用本机种子身份。
2. 由具名平台管理员准备租户、账号 membership、已发布产品/SKU 与最小审批表单/流程；内容不足时记断点，不把空态算成功。受控 bootstrap 需单独已合 API、强鉴权和审计，不能假设规划卡已实现。
3. 按 M7 §1.5 在浏览器走 Keycloak 登录、HTTPS 回跳与 tenant context；确认不是 stub token/demo header。回调异常核 issuer/redirect/反代路由，日志不得保存授权 code、token、cookie 或完整 URL query。
4. 换审批人账号确认同租户权限，不共享会话或借用管理员 token。

## 3. 真机点击路径与证据

登录 → 目录选择已发布产品 → 申请开通/审批激活（按产品实际权限）→ 发起申请 → 审批人待办填写意见并处理 → 发起人「我的申请」核对状态与意见。

每步记时间、发布 SHA、操作者显示名、租户标识类型、脱敏结果/断点；只记录实际发生的事实。HTTP 探活/重定向不是 post-login 业务路径验收。升级步骤无真实运维记录时仍 UNKNOWN；不制造 DONE 或假支付链路。

## 4. 失败与收尾

- 失败即暂停，保留脱敏错误类别；不要在真机运行本地 `scripts/demo/reset.ps1`。
- 清理/remoke 必须另遵守 allow 门闩、具名操作者、强鉴权与明确确认，不自动删库/重播种子。
- 只有环境所有者逐项完成 M7 §1.5 与 E1–E7 证据后，才能签署真机结论；本文件不会自动关闭外部阻塞。
