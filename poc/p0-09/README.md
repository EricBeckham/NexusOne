# P0-09 共享应用表 N/N-1 兼容 PoC

与 P0-08 对齐：共享表名模式为 `a_{app_code}_{ledger_code}`，固定字段含 `id, tenant_id, created_at, created_by, updated_at, updated_by, deleted_at, version, schema_version, ext_data`。本脚本使用 `a_demo_order`，一张表中放 A/B/C 三租户记录。`ext_data` 只承载租户覆盖值，核心业务字段为普通列。

运行（PostgreSQL 15+，连接用户需要临时表权限）：

```powershell
psql -X -v ON_ERROR_STOP=1 -d '<dedicated-test-database>' -f poc/p0-09/verify.sql
```

2026-09-22 在独立本地 PostgreSQL 15.18 测试实例（`nexus_p0_09`）执行，退出码 0，输出 `P0-09 assertions passed`。随后另开连接查询 `information_schema.tables`，PoC 表数量为 0，确认事务回滚清理。

脚本在事务中建立临时表，成功时输出 `P0-09 assertions passed`，最后 `ROLLBACK` 清理。任一断言失败将以非零退出码终止。执行阶段：

1. v1 基线：A/B/C 均使用 `legacy_label`。
2. Expand：平台级添加可空 `display_name`，不删除或收窄旧字段。
3. Migrate：仅回填 A，第二次执行必须更新 0 行。
4. Switch：A=v2 双写新旧列；B/C=v1 继续读写旧列。v2 用 `coalesce(display_name, legacy_label)` 双读。
5. A 回滚：只切回租户运行版本，保留物理新列、v2 记录和全部业务数据；v1 仍可读。
6. Contract：所有受支持租户不再使用 v1 且保留期已过，才判定可进入单独清理流程。脚本从不执行 `DROP COLUMN`。

生产实现需由 SchemaService 执行平台级 DDL，并为多批回填做 checkpoint、失败重试及审计。旧客户端若支持更新，双写必须覆盖**更新路径**，不能只覆盖插入。Contract 还需冻结支持版本清单、核对后台作业/导出/API 等旧字段消费者并人工审批；此 PoC 只模拟租户版本与保留期两个关键条件。

参考：[PostgreSQL 15 ALTER TABLE](https://www.postgresql.org/docs/15/sql-altertable.html)。
