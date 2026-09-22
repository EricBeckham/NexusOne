# PostgreSQL 单租户逻辑恢复（运维 leftover 演练）

**状态：G6 leftover 运维演练。不是 V1.1 产品单租户恢复能力，不是分钟级 RPO，不是 G17，不是 RecoveryPort 接线。**

适用：在**专用** PostgreSQL **15.x** 集群上，把双租户源库里 **一个** 租户的业务行 `COPY` 进专用空库。不覆盖 GaussDB。产品路径仍按 V1.1：租户导入/导出只作为迁移能力，`RecoveryPort` 保持 dry-run。

可复现脚本：[`scripts/g6-recovery/run-single-tenant.sh`](../../../scripts/g6-recovery/run-single-tenant.sh)。  
双租户整库路径仍是 [`run-drill.sh`](../../../scripts/g6-recovery/run-drill.sh) / [postgres-whole-db-restore.md](./postgres-whole-db-restore.md)。  
实测记录：[`g6-single-tenant-logical-restore-EVIDENCE.md`](../g6-single-tenant-logical-restore-EVIDENCE.md)。

## 原则

- 恢复必须落到**独立实例 / 独立空库**。不得连接或创建 `haopan_platform`。
- 专用集群默认 `127.0.0.1:55432`。端口 `5432` 直接拒绝。
- 源库保持双租户。只把 `G6_TENANT_ID` 的业务行写入 `nexusone_g6_one`。
- 业务表：`platform.audit_record`、`files.file_object`、`ledger.sample_row`。按 `tenant_id` 过滤。
- `platform.flyway_schema_history` 是 schema 元数据，整表复制，不是租户业务行。
- `platform.audit_chain_meta` 是双租户链头；单租户演练按恢复后的最后一条审计行重建，不复制源库双租户 head。
- 目的库出现另一个 `tenant_id` 即失败。源库不再同时持有两个租户即失败。
- 密码只走环境变量或本机 workdir（`.local/g6-recovery/`，已 gitignore）。不要把 dump 提交进 Git。

## 备份（schema）与按租户 COPY

```bash
export PATH="/usr/lib/postgresql/15/bin:$PATH"
export PGHOST=127.0.0.1
export PGPORT=55432
# PGPASSWORD via env only when the cluster is not local trust

pg_dump --schema-only --no-owner --no-privileges \
  --file="$WORK/backup/nexusone_g6_src.schema.sql" \
  --dbname=nexusone_g6_src

psql -d postgres -c "CREATE DATABASE nexusone_g6_one TEMPLATE template0 ENCODING 'UTF8';"
psql -d nexusone_g6_one -f "$WORK/backup/nexusone_g6_src.schema.sql"

# one tenant only; G6_TENANT_ID is a UUID (script rejects anything else)
psql -d nexusone_g6_src \
  -c "COPY (SELECT * FROM ledger.sample_row WHERE tenant_id = '${G6_TENANT_ID}'::uuid) TO STDOUT" \
  | psql -d nexusone_g6_one -c "COPY ledger.sample_row FROM STDIN"
```

同样复制 `platform.audit_record` 与 `files.file_object`。然后比较

`COPY (SELECT * FROM … WHERE tenant_id = '<uuid>'::uuid ORDER BY …) TO STDOUT WITH CSV | sha256sum`

与目的库整表（此时只能有这一个租户）的 SHA-256。

## 本仓库接口

`one.nexus.audit.recovery.RecoveryPort.dryRunPostgres()` 只探测 `pg_dump --version`，**不会**执行 dump、schema apply 或按租户 COPY。`profileAccepted` 恒为 false。本 leftover 走脚本 / 本 runbook，不改产品代码。
