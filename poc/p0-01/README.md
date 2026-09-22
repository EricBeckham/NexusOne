# P0-01 PostgreSQL 数据隔离 PoC

本 PoC 用 Java 8 标准库实现受限 Query AST → TenantInjector → TableAccessPlan Guard → PostgreSQL SQL Renderer。表名和列名只能来自 `TableRegistry`，值统一绑定参数；不接受原生 SQL。`PlatformContext` 只能由服务端入口按白名单签发，且每次签发都写入审计。

运行：

```powershell
cd poc/p0-01
./run.ps1
```

`run.ps1` 使用 `javac` 和 `java`，无需 Maven 或数据库服务。测试验证 SQL 生成前的拒绝行为与参数顺序，不声称已完成 PostgreSQL 数据库上的集成验收。

Renderer 按 PostgreSQL 标识符规则使用双引号包围已登记的表名和列名，并为所有值生成 JDBC `?` 参数。SQL 语法参考 [PostgreSQL 当前版 UPDATE](https://www.postgresql.org/docs/current/sql-update.html)、[DELETE](https://www.postgresql.org/docs/current/sql-delete.html) 和 [quote_ident](https://www.postgresql.org/docs/current/functions-string.html) 文档。调用者必须通过 `Executor.compile` 获取 SQL；registry 与平台路径白名单应在服务启动时由可信配置固定。

后续集成必须补充：真实 PostgreSQL 双租户 CRUD/分页/并发测试；Join、子查询、批量写入和 DataScope/FieldPolicy 的正式 AST；`NativeSqlPort` 白名单入口；CI 静态禁用绕过仓储层的 JDBC；`table_registry` 与实际 DDL 同事务登记。当前 renderer 只支持单表等值过滤及单行写入。
