# DL-X5 · Eric 离线 Go / No-Go

> **预备 ≠ 验收。** 本页未勾选、未签署；本地 stub、探活绿或 skipped 均不能关闭真机门禁。只记脱敏证据，不写口令、token、cookie、密钥值。

环境：________　发布 SHA：________　制品 SHA-256：________　演示时间：________

## E1–E7（环境所有者逐项核实）

| 完成 | 门禁 | 脱敏证据 / 卡点（另附记录编号亦可） |
| --- | --- | --- |
| [ ] | E1 真机主机 / SSH / 云账号 | ________ |
| [ ] | E2 DNS + TLS（公网证书） | ________ |
| [ ] | E3 密钥仓（secrets vault）：仅核变量存在，不抄值 | ________ |
| [ ] | E4 Keycloak realm / client / redirect（M7 §1.1–1.2） | ________ |
| [ ] | E5 受控演示账号（仅显示名 / subject，不写口令） | ________ |
| [ ] | E6 反向代理（SPA + `/callback`） | ________ |
| [ ] | E7 JAR + SHA256 + M7 §1.5 真机登录验收 | ________ |

## T-30（演示前 30 分钟，逐项执行）

- [ ] 对照已合 main 发布 SHA，核对部署 JAR 的 SHA-256；不以本机构建替代真机制品证据。
- [ ] 核 profile 为 `prod`、demo seed/context flags OFF、无 stub 身份或鉴权绕过；依赖健康，TLS 有效。
- [ ] 用受控发起人 / 审批人分别走 Keycloak → HTTPS 回调 → tenant context，核同一目标租户（M7 §1.5）。
- [ ] 核目录 SKU、已发布表单 / 流程、产品绑定及就绪状态；缺内容即停，按 prod-safe 手册记断点。
- [ ] 在专用演示租户实跑开通 → 发起 → 审批填写意见 → 我的申请，核结果与意见；记录实际时间及脱敏证据。
- [ ] 核备份 / 恢复责任人及 remoke 的 allow、鉴权、具名操作者、确认门禁；不运行远程 reset，不打印凭据。

仅当全部必需项有真实证据且路径实跑完成才选 Go；任一缺失、失败或未执行选 No-Go。探活成功只证明可达，URL 未设的 skipped 是未执行。

结论：[ ] Go　[ ] No-Go　未关闭门禁 / 卡点：________________

Eric 签名：________________　签署时间：________________

执行依据：[M7 验收清单](M7-Keycloak演示验收清单.md) · [X1 真机 runbook](../engineering/runbooks/demo-host-realhost-launch.md) · [Prod-safe 内容手册](../engineering/runbooks/demo-host-prod-safe-content.md) · [remoke 纪律](../engineering/runbooks/demo-host-remoke-discipline.md)。
