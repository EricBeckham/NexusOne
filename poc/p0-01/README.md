# P0-01 PostgreSQL 数据隔离 PoC

本 PoC 用 Java 8 标准库实现受限 Query AST → TenantInjector → TableAccessPlan Guard → PostgreSQL SQL Renderer，以适配本机编译器；NexusOne 产品基线仍为 Java 21，须在目标 JDK 重验。表名和列名只能来自 `TableRegistry`，值统一绑定参数；不接受任意原生 SQL。`PlatformContext` 只能由服务端入口按白名单签发，且每次签发都写入审计。

运行：

```powershell
cd poc/p0-01
./run.ps1
```

`run.ps1` 默认使用 `javac` 和 `java` 验证 Guard 与 SQL 生成。配置 `PGDATABASE`、`PGUSER` 后还会通过 Maven 取得 pgJDBC 42.7.8，在真实 PostgreSQL 运行双租户集成测试。按需设置 `PGHOST`、`PGPORT`、`PGPASSWORD`。例如：

```powershell
$env:PGHOST='127.0.0.1'; $env:PGPORT='55438'
$env:PGUSER='nexus_poc'; $env:PGDATABASE='nexus_p0_01'
./run.ps1
```

集成测试在事务内建立 `p001` fixture Schema/表并回滚，不留下测试数据。测试专用 JDBC 直接执行只用于建表 fixture；业务增删改查均使用 `Executor.compile` 生成的 SQL 与绑定参数。

`NativeSqlPort` 只接受基础设施预登记的聚合模板 ID，调用者不能提交 SQL 文本或表名。每次编译先走同一个 Query AST/TenantInjector/TableAccessPlan Guard，再按访问计划生成带租户绑定的 PostgreSQL count 查询。此模板机制仍是 PoC，尚未覆盖通用复杂原生 SQL。

`check-static.ps1 -SourceRoot <业务源码根目录> -InfrastructureRoot <根目录内的基础设施白名单目录>` 可放入 CI，扫描业务 Java/XML 并禁止直接 JDBC、Spring JDBC、JPA native query 和 MyBatis 字符串替换。`test-static.ps1` 运行允许/拒绝 fixture；`run.ps1` 会执行这些 fixture。必须在真实项目 CI 中明确传入业务源码根目录；此 PoC 仓库尚无产品业务源码。

Renderer 按 PostgreSQL 标识符规则使用双引号包围已登记的表名和列名，并为所有值生成 JDBC `?` 参数。SQL 语法参考 [PostgreSQL 当前版 UPDATE](https://www.postgresql.org/docs/current/sql-update.html)、[DELETE](https://www.postgresql.org/docs/current/sql-delete.html) 和 [quote_ident](https://www.postgresql.org/docs/current/functions-string.html) 文档。调用者必须通过 `Executor.compile` 获取 SQL；registry 与平台路径白名单应在服务启动时由可信配置固定。

后续集成必须补充：分页/并发测试；Join、子查询、批量写入和 DataScope/FieldPolicy 的正式 AST；通用复杂 NativeSqlPort 的 SQL/访问计划一致性证明；将静态绕过检查接入真实业务模块 CI（正则原型不能覆盖别名、反射等绕过）；`table_registry` 与实际 DDL 同事务登记。当前 renderer 只支持单表等值过滤及单行写入。

JDBC 参数绑定参考 [pgJDBC 官方查询文档](https://github.com/pgjdbc/pgjdbc/blob/master/docs/content/documentation/query.md)。
