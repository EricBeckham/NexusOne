# 文档

产品与过程文档集中在本目录。模块实现说明、PoC 证据仍放在对应代码目录。

| 目录 | 内容 |
| --- | --- |
| [requirements](./requirements/星枢企业业务平台_需求规格_V1.1.md) | 需求规格 V1.1 |
| [architecture](./architecture/星枢企业业务平台_技术框架_V1.1.md) | 技术框架 V1.1 |
| [design](./design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md) | Web 页面规范；[H5 规范](./design/星枢NexusOne_H5页面设计与前端开发规范_V1.1.md)；效果图在 `design/references/` |
| [planning](./planning/实施任务拆解_V1.1.md) | 实施任务拆解；[团队领取清单](./planning/团队领取任务清单_V1.1.md)（P0+M0+G18 已在 main；当前领 S0+S1 余量） |
| [g15-h5-mobile](./g15-h5-mobile.md) | G15 H5 移动浏览器：登录回跳 / 文件 / 路由 / 宿主消息（F014 postMessage）。产品 G15 仍开放（APP WebView / 小程序） |
| [g15-f010-h5-runtime](./g15-f010-h5-runtime.md) | G15 F010 H5 运行入口：表单 / 列表 / 待办 / 查询 / 通知跳转（移动浏览器）。桌面页未改；其他宿主 / PKCE 仍开放 |
| [g7-business-types](./g7-business-types.md) | G7 请假/报销/出差 catalog（#69）+ 双租户撤回/驳回走通证据；[报销/出差撤回 403](./g7-withdraw-expense-travel.md) 为 leftover，不宣称 G7 产品关闭 |
| [g17-separation-of-duties](./g17-separation-of-duties.md) | G17 三权分立 slice（ARCH-CMP-002）；不是完整 G17 关闭；[证据](./engineering/g17-separation-EVIDENCE.md) |
| [g17-gm-tls-dual-cert](./g17-gm-tls-dual-cert.md) | G17 leftover：本地国密 TLS 双证书（SM2 签名+加密证书 + RFC 8998 TLS_SM4_GCM_SM3）；不是等保/密码机/Nginx 国密关闭；[证据](./engineering/g17-gm-tls-EVIDENCE.md) |
| [g2-form-submission-write](./g2-form-submission-write.md) | G2 leftover：表单填报 / POST submissions 按 `form.write` 走 `FormSurfaceGuard`；不是产品级 G2 关闭 |
| [engineering](./engineering/m0-01-engineering-foundation.md) | 工程底座与启动说明；[M0-07 表单/发布](./m0-07-form-publish.md)；[G5 表单发布回滚](./g5-form-publish-rollback.md)；[G7 请假撤回 HTTP](./g7-withdraw-evidence.md)；[G7 报销/出差撤回 leftover](./g7-withdraw-expense-travel.md)；[G3 幂等/乐观锁](./g3-data-consistency.md)；[G9 version poll / invalidate-only](./g9-policy-snapshot.md)；[G9 Redis-down failover](./g9-policysnapshot-invalidate.md)；[G9 P2 timeout-then-reject](./g9-p2-timeout-reject.md)；[M0-10 文件/缓存/通知](./m0-10-file-cache-notify.md)；[G1 导出/文件/缓存/Job](./g1-isolation-surfaces.md)；[G1 live Redis/MinIO](./g1-live-redis-minio.md)；[ops live Redis/MinIO isolation](./engineering/runbooks/live-redis-minio-isolation.md)；[G1 JOIN / 导出 FieldPolicy](./g1-query-join-field-policy.md)；[G1 Query AST remainder](./g1-query-ast-remainder.md)；[G1 nested subquery](./g1-query-nested-subquery.md)；[G1 OUTER/CROSS JOIN](./g1-query-outer-cross-join.md)；[M0-11 审计/密码/恢复](./engineering/m0-11-crypto-audit-recovery.md)；[M0-11 证据](./engineering/m0-11-EVIDENCE.md)；[G4 审计覆盖](./engineering/g4-audit-coverage.md)；[G4 导出审计/归档](./g4-export-audit-archive.md)；[G4 对象冷归档](./g4-object-cold-archive.md)；[G4 同 trace 走通](./g4-same-trace-walk.md)；[G4 PKCE 登录上链](./g4-pkce-login-audit.md)；[G6 整库/对象恢复演练证据](./engineering/g6-recovery-drill-EVIDENCE.md)；[G6 单租户逻辑恢复 leftover 证据](./engineering/g6-single-tenant-logical-restore-EVIDENCE.md)；[PostgreSQL 整库恢复 runbook](./engineering/runbooks/postgres-whole-db-restore.md)；[PostgreSQL 单租户逻辑恢复 leftover runbook](./engineering/runbooks/postgres-single-tenant-logical-restore.md)；[MinIO 对象恢复 runbook](./engineering/runbooks/object-store-restore.md)；[G10 身份对账](./g10-identity-reconcile.md)；[G10 文件对账](./g10-file-reconcile.md)；[G17 管理员强制 MFA 切片](./g17-admin-mfa.md)；[S0 SecretPort](./engineering/s0-secret-port.md)；[S0 SecretPort 证据](./engineering/s0-secret-port-EVIDENCE.md)；[Linux/ARM Profile 采集证据（未关闭）](./engineering/linux-arm-profile-EVIDENCE.md) |

代码旁文档：

- `modules/isolation-kernel/docs/`：隔离内核契约与证据
- `nexus-p1-tenant/`、`nexus-p2-iam/`：模块 README 与契约
- `poc/`：P0 验证说明，不作为产品验收结论
