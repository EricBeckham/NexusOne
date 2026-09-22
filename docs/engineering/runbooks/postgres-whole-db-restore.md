# PostgreSQL 整库恢复（逻辑备份）

**状态：G6 S1 环境级整库恢复演练（专用库）。不是目标 Profile，不是分钟级 RPO，不是 G17。**

适用：NexusOne 业务库形状的 PostgreSQL **15.x** 整库 `pg_dump` / `pg_restore`。不覆盖 GaussDB。本 runbook **禁止**按 `tenant_id` 过滤；整库必须保留全部租户。单租户 leftover 是另一条运维演练，见 [postgres-single-tenant-logical-restore.md](./postgres-single-tenant-logical-restore.md)。它不是 V1.1 产品能力，也不改 `RecoveryPort`。

可复现脚本：[`scripts/g6-recovery/run-drill.sh`](../../../scripts/g6-recovery/run-drill.sh)。实测记录：[`g6-recovery-drill-EVIDENCE.md`](../g6-recovery-drill-EVIDENCE.md)。

## 原则

- 恢复必须落到**独立实例 / 独立数据库**。备份命令成功不等于恢复通过。
- G6 演练使用专用集群（默认 `127.0.0.1:55432`）和专用库名 `nexusone_g6_src` → `nexusone_g6_dst`。
- **禁止**连接或创建 `haopan_platform`。脚本在名称含 `haopan` 或端口为 `5432` 时直接拒绝。
- **禁止**按 `tenant_id` 过滤导出/导入。整库恢复必须保留源库全部租户行。单租户 leftover 不得走本 runbook。
- 密码只走环境变量或本机 workdir（`.local/g6-recovery/`，已 gitignore）。不要把 dump 提交进 Git。
- 审计表是 append-only；产品路径接线后应再跑 `AuditPort.verifyChain()`。本演练校验表级 SHA-256 与行数。

## 备份

```bash
export PATH="/usr/lib/postgresql/15/bin:$PATH"
export PGHOST=127.0.0.1
export PGPORT=55432
# PGPASSWORD via env only when the cluster is not local trust
pg_dump --format=custom --file="$WORK/backup/nexusone_g6_src.dump" \
  --dbname=nexusone_g6_src
sha256sum "$WORK/backup/nexusone_g6_src.dump"
```

记录：PostgreSQL 精确版本、`pg_dump --version`、dump SHA-256、UTC 时间。不要上传含数据的 dump。

## 恢复到专用空库

1. 同一大版本 PostgreSQL 15.x，`CREATE DATABASE nexusone_g6_dst TEMPLATE template0`。
2. `pg_restore --dbname=nexusone_g6_dst --exit-on-error nexusone_g6_src.dump`
3. 比较 `platform.flyway_schema_history` 与关键表的 `COPY … ORDER BY` SHA-256。
4. 确认恢复库中的 **全部** `tenant_id` 仍在（本演练为两个租户）。出现“只恢复一个租户”即失败。
5. `SELECT datname FROM pg_database WHERE datname ILIKE '%haopan%'` 必须为空。
6. 记录 dump/restore 墙钟秒数。这是演练 RTO 观察值，**不是**交付等级分钟级 RPO 验收。

## 本仓库接口

`one.nexus.audit.recovery.RecoveryPort.dryRunPostgres()` 只探测 `pg_dump --version`，**不会**执行 dump 或 restore。`profileAccepted` 恒为 false。G6 演练走本 runbook / `scripts/g6-recovery/`，不改产品代码。dry-run 契约 leftover 见 [g6-recovery-port-dry-run.md](../../g6-recovery-port-dry-run.md)。
