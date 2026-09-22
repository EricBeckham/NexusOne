# PostgreSQL 整库恢复（逻辑备份）

**状态：runbook / dry-run 契约。不是 G6，不是目标 Profile 验收。**

适用：NexusOne 业务库（PostgreSQL 15.x）。不覆盖 GaussDB，不覆盖单租户逻辑恢复（V1.1 明确不做）。

## 原则

- 恢复必须落到**独立实例**，备份命令成功不等于恢复通过。
- 升级失败时按此流程回到升级**前**的库快照；不得假设旧程序能读升级后的 Schema。
- 密码、`NEXUS_DB_PASSWORD`、`NEXUS_CRYPTO_MASTER_KEY` 只走环境变量。
- 审计表是 append-only；恢复后应调用 `AuditPort.verifyChain()`（业务模块接线后）。当前只能在测试双上验证算法。

## 备份（示例，勿把密码写进命令历史文档）

```bash
export PGPASSWORD  # env only
pg_dump --format=custom --file=/secure/backup/nexusone.dump \
  --host=127.0.0.1 --port=5432 --username=nexus nexusone
```

记录：PostgreSQL 精确版本、`pg_dump --version`、文件 SHA-256、时间、操作者。不要上传含数据的 dump 到 Git。

## 恢复到独立实例

1. 安装**同一大版本** PostgreSQL 15.x，创建空库。
2. `pg_restore --dbname=nexusone_restore --exit-on-error nexusone.dump`
3. 检查 `platform.flyway_schema_history` 与迁移版本（含 M0-09 V500 与 M0-11 V501）。
4. 比较关键表行数；业务模块接线后校验 `platform.audit_record` 哈希链。
5. 应用以只读或隔离 URL 启动，跑契约测试。
6. 记录 RPO/RTO。未完成上述步骤不得把 G6 标为通过。

## 本仓库接口

`one.nexus.audit.recovery.RecoveryPort.dryRunPostgres()` 只探测 `pg_dump --version`，**不会**执行 dump 或 restore。`profileAccepted` 恒为 false。
