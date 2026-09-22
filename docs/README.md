# 文档

产品与过程文档集中在本目录。模块实现说明、PoC 证据仍放在对应代码目录。

| 目录 | 内容 |
| --- | --- |
| [requirements](./requirements/星枢企业业务平台_需求规格_V1.1.md) | 需求规格 V1.1 |
| [architecture](./architecture/星枢企业业务平台_技术框架_V1.1.md) | 技术框架 V1.1 |
| [design](./design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md) | Web 页面规范；[H5 规范](./design/星枢NexusOne_H5页面设计与前端开发规范_V1.1.md)；效果图在 `design/references/` |
| [planning](./planning/实施任务拆解_V1.1.md) | 实施任务拆解；[团队领取清单](./planning/团队领取任务清单_V1.1.md)（P0+M0+G18 已在 main；当前领 S0+S1 余量） |
| [g7-business-types](./g7-business-types.md) | G7 请假/报销/出差 + 撤回跑法 |
| [engineering](./engineering/m0-01-engineering-foundation.md) | 工程底座与启动说明；[M0-07 表单/发布](./m0-07-form-publish.md)；[G5 表单发布回滚](./g5-form-publish-rollback.md)；[G3 幂等/乐观锁](./g3-data-consistency.md)；[M0-10 文件/缓存/通知](./m0-10-file-cache-notify.md)；[G1 导出/文件/缓存/Job](./g1-isolation-surfaces.md)；[G1 live Redis/MinIO](./g1-live-redis-minio.md)；[G1 JOIN / 导出 FieldPolicy](./g1-query-join-field-policy.md)；[M0-11 审计/密码/恢复](./engineering/m0-11-crypto-audit-recovery.md)；[M0-11 证据](./engineering/m0-11-EVIDENCE.md)；[G4 审计覆盖](./engineering/g4-audit-coverage.md)；[G6 整库/对象恢复演练证据](./engineering/g6-recovery-drill-EVIDENCE.md)；[PostgreSQL 整库恢复 runbook](./engineering/runbooks/postgres-whole-db-restore.md)；[MinIO 对象恢复 runbook](./engineering/runbooks/object-store-restore.md)；[G10 身份对账](./g10-identity-reconcile.md) |
代码旁文档：

- `modules/isolation-kernel/docs/`：隔离内核契约与证据
- `nexus-p1-tenant/`、`nexus-p2-iam/`：模块 README 与契约
- `poc/`：P0 验证说明，不作为产品验收结论
