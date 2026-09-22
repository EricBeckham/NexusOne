# P0-08 PostgreSQL 动态台账容量 PoC

`setup.sql` 创建独立 `p008` Schema、`table_registry`、租户 Quota、SchemaService 函数、批量 SchemaDriftCheck 和 DDL 耗时记录。动态表命名：自建 `l_{tenant_code}_{ledger_code}`，共享应用 `a_{app_code}_{ledger_code}`。共享表固定包含 `tenant_id`，不会按租户复制。系统字段符合架构文档 §19.2。

运行前配置 PostgreSQL 专用测试库：`PGDATABASE`、`PGUSER`，按需配置 `PGHOST`、`PGPORT`、`PGPASSWORD`。`PSQL_EXE` 可覆盖默认 `D:\Software\PostgreSQL\15\bin\psql.exe`。例：

```powershell
$env:PGDATABASE='nexus_poc'
$env:PGUSER='poc_user'
$env:PGHOST='127.0.0.1'
./bench.ps1 -TenantCount 10 -CustomPerTenant 1 -SharedTables 2
```

正式 1,000 租户模型可设 `-TenantCount 1000`，但会实际创建表和索引，须先准备专用测试库并确认资源。脚本不会自动删库或删表；重复相同参数会跳过已注册表，因此重复压测应使用新测试库。日志写入 `results/`。`contract.sql` 在事务中验证配额、标识符拒绝和 drift，完成后回滚。

**当前状态**：已在独立本地 PostgreSQL 15.18 测试库运行 1,000 租户、1,000 张自建表、5 张共享表模型；结果见 `EVIDENCE.md`。本地开发机结果不能代表目标 Profile，尚未冻结任何容量阈值。`capacity_limit` 的 100,000/300,000 是防误创建的 PoC 上限，绝非产品可承诺容量。`THRESHOLDS.md` 用于记录实测并冻结门禁。

SQL 设计参考 [PostgreSQL 15 CREATE INDEX](https://www.postgresql.org/docs/15/sql-createindex.html) 与 [LOCK](https://www.postgresql.org/docs/15/sql-lock.html)。索引在新表上于同一事务创建；此 PoC 未使用 `CREATE INDEX CONCURRENTLY`。
