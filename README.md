# NexusOne

## 文档

目录说明见 [docs/README.md](./docs/README.md)。

- [团队领取任务清单 V1.1](./docs/planning/团队领取任务清单_V1.1.md)：P0+M0+G18 已在 `main`；S0+S1 wave4 切片（#111～#123 / #124 / #125 / #126 / #127 / #128 / #129 / #130 / #131 / #132 / #133 / #134 / #135 / #136 / #137 / #138 / #139 / #140 / #141 / #142 / #144，对齐 `e0a409a`）已落地。G1 单列 DataScope `IN` leftover 见 [docs/g1-datascope-or-in-ast.md](./docs/g1-datascope-or-in-ast.md)（#142；不宣称产品 G1 关闭）。只领取清单里「仍开放」的产品余量，不要重做 P0 叶子或 M0 实现，不要重开 G18，不要宣称产品 G1/G2/G4/G7/G9/G15/G17 或完整 S0/S1 通过。
- [实施任务拆解 V1.1](./docs/planning/实施任务拆解_V1.1.md)：阶段、父任务、历史 PoC 证据与验收门禁。
- [P0 叶子 Issues](https://github.com/EricBeckham/NexusOne/issues?q=is%3Aissue+label%3Atype%3Aleaf)：历史追溯，勿再领实现。
- [M0 父 Issues](https://github.com/EricBeckham/NexusOne/issues?q=is%3Aissue+label%3Aphase%3AM0)：历史追溯，勿再领实现。

需求基准：[需求规格 V1.1](./docs/requirements/星枢企业业务平台_需求规格_V1.1.md)；技术基准：[技术框架 V1.1](./docs/architecture/星枢企业业务平台_技术框架_V1.1.md)。

当前执行口径：数据库为本机 PostgreSQL 15，缓存为本机 Redis 3.2；Java 应用直接以本机 JAR/进程运行，**不使用 Docker**。凭据只通过本机环境变量或受控 Secret 提供，不写入仓库或 Issue。业务代码只经 `SecretPort`（进程内 overlay + 环境变量）取凭据；**不引入 OpenBao / KMS**。说明见 [docs/engineering/s0-secret-port.md](./docs/engineering/s0-secret-port.md)。`poc/` 是此前验证资产，不能自动视为正式验收通过。

M0-10（CachePort / File Service / 站内通知）见 [docs/m0-10-file-cache-notify.md](./docs/m0-10-file-cache-notify.md)。可选 live Redis / MinIO 跨租户拒绝钻见 [docs/g1-live-redis-minio.md](./docs/g1-live-redis-minio.md)。
M0-07（基础表单与发布）见 [docs/m0-07-form-publish.md](./docs/m0-07-form-publish.md)。G5 表单失败回滚证据见 [docs/g5-form-publish-rollback.md](./docs/g5-form-publish-rollback.md)。G5 leftover：发布中途失败（CREATE abort / SchemaService 成功后再 abort）后 Running 指针与 snapshot 不变，见 [docs/g5-mid-publish-failure.md](./docs/g5-mid-publish-failure.md)（不宣称产品 G5 关闭）。G7 请假撤回 HTTP 证据见 [docs/g7-withdraw-evidence.md](./docs/g7-withdraw-evidence.md)。G7 浏览器撤回 leftover（Vue `todo-withdraw`，不宣称 G7 产品关闭）见 [docs/g7-withdraw-browser.md](./docs/g7-withdraw-browser.md)。G7 报销/出差 list/detail/todo 查询 leftover 见 [docs/g7-query-expense-travel.md](./docs/g7-query-expense-travel.md)（#140；不宣称产品 G7 关闭，不重开 G18）。G4 桌面 PKCE 点击 leftover（Playwright，不宣称产品 G4 关闭）见 [docs/g4-pkce-browser-click.md](./docs/g4-pkce-browser-click.md)。G9 version poll 见 [docs/g9-policy-snapshot.md](./docs/g9-policy-snapshot.md)；Redis-down failover 见 [docs/g9-policysnapshot-invalidate.md](./docs/g9-policysnapshot-invalidate.md)；fail-closed DENY 见 [docs/g9-failover-remainder.md](./docs/g9-failover-remainder.md)；in-process dual-reader / lost-invalidate version-poll leftover 见 [docs/g9-multinode-partition.md](./docs/g9-multinode-partition.md)（不宣称 Sentinel/Cluster / replica promotion）。
G3（submit-start 幂等键与记录乐观锁）见 [docs/g3-data-consistency.md](./docs/g3-data-consistency.md)。G3 leftover：Outbox 消费幂等压测 / 重复投递证据见 [docs/g3-outbox-consume-idempotent.md](./docs/g3-outbox-consume-idempotent.md)（#139；至少一次投递 + `exec_idempotency` 跳过二次副作用；不宣称产品 G3 关闭，不重做 Outbox/Dispatcher）。
G15 H5 人机 PKCE leftover（`/m/*` Authorization Code + PKCE，Playwright/stub；不宣称产品 G15 关闭）见 [docs/g15-h5-pkce-browser.md](./docs/g15-h5-pkce-browser.md)。G15 微信 / 钉钉生产宿主契约桩（不是生产验收）见 [docs/g15-wx-dt-prod-host.md](./docs/g15-wx-dt-prod-host.md)。
G2 leftover：报表 query/export 走同一 `SurfaceGuard` 见 [docs/g2-report-surface-guard.md](./docs/g2-report-surface-guard.md)；物理 dump / 原始导出见 [docs/g2-physical-dump-surface.md](./docs/g2-physical-dump-surface.md)（#137；不宣称产品 G2 关闭）。isolation-only `tenant_only` / catalog 白名单诚实 leftover 见 [docs/g2-tenant-only-catalog.md](./docs/g2-tenant-only-catalog.md)（#144；有 P2 `TenantContext` 时不能静默跳过 `SurfaceGuard`）。待办 HTTP leftover 见 [docs/g2-todo-http-surface.md](./docs/g2-todo-http-surface.md)（`GET /api/v1/p3/todos` 走同一 `SurfaceGuard`；不重做 #144 catalog）。
G6 leftover：`RecoveryPort` dry-run 契约（不宣称分钟级 RPO / 产品 HA / 产品 RecoveryPort 关闭；不碰 `haopan_platform`）见 [docs/g6-recovery-port-dry-run.md](./docs/g6-recovery-port-dry-run.md)。
G4 leftover：SurfaceGuard 守卫拒绝 / 权限拒绝上同一哈希链见 [docs/g4-guard-deny-audit.md](./docs/g4-guard-deny-audit.md)（#136；不宣称产品 G4 关闭；与 #125 桌面 PKCE 点击正交）。
G1 leftover：导出 Query AST 对 hidden 字段 drop（不再只 reject）见 [docs/g1-export-fieldpolicy-drop.md](./docs/g1-export-fieldpolicy-drop.md)（#134；不宣称产品 G1 关闭；二次授权 / mask 仍在 `FieldPolicyProjector`）。`GuardedLedgerStore` leftover BoundSql / COUNT 守卫见 [docs/g1-queryast-boundsql-guard.md](./docs/g1-queryast-boundsql-guard.md)（不重做 #142 AST；不宣称产品 G1 关闭）。

## 应用骨架（M0-01）

模块地图、错误码、Flyway 版本窗与本机 JAR 启动见 [docs/engineering/m0-01-engineering-foundation.md](./docs/engineering/m0-01-engineering-foundation.md)。

```bash
export NEXUS_DB_URL='jdbc:postgresql://127.0.0.1:5432/nexusone'
export NEXUS_DB_USER='nexus'
export NEXUS_DB_PASSWORD  # 只放环境变量
mvn -q test
mvn -q -DskipTests package
java -jar nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar
```

## 前端与双租户验收（M0-12）

最小 Vue 3 壳与 HTTP 对抗脚本。P1 租户 / P2 组织身份 / P3 台账表单待办 / 权限快照走已合并 live API。产品 G18（一条纵向闭环）已关闭，记录在 [docs/g18-business-path.md](./docs/g18-business-path.md)。G10 身份半边（P2 人员与 Keycloak 对账修复）见 [docs/g10-identity-reconcile.md](./docs/g10-identity-reconcile.md)；Keycloak 8080 drift-fix dry-run leftover（#141；检测+提案，默认不写）见 [docs/g10-identity-drift-dry-run.md](./docs/g10-identity-drift-dry-run.md)；文件元数据与对象对账见 [docs/g10-file-reconcile.md](./docs/g10-file-reconcile.md)。G17 管理员强制 MFA 切片（非完整 G17）见 [docs/g17-admin-mfa.md](./docs/g17-admin-mfa.md)。G17 国密 TLS 双证书本地切片见 [docs/g17-gm-tls-dual-cert.md](./docs/g17-gm-tls-dual-cert.md)。G17 Nginx/Tongsuo（或本机双栈）国密 TLS leftover 见 [docs/g17-gm-tls-nginx-dual-stack.md](./docs/g17-gm-tls-nginx-dual-stack.md)（无 Tongsuo/Nginx 时诚实 SKIP，不宣称等保/密评/密码机）。G17 TOTP 注册 UX leftover（应用内文案/错误路径，不重做 #118 驱动）见 [docs/g17-totp-enrollment-ux.md](./docs/g17-totp-enrollment-ux.md)。清单与运行方式：[docs/m0-12-ui-acceptance.md](./docs/m0-12-ui-acceptance.md)，界面：[web/README.md](./web/README.md)。

```bash
export NEXUS_DB_URL='jdbc:postgresql://127.0.0.1:5432/nexusone'
export NEXUS_DB_USER='nexus'
export NEXUS_DB_PASSWORD  # 只放环境变量
./scripts/m0-12/run-dual-tenant.sh

cd web && npm install && npm run dev
```
