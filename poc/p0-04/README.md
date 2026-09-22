# P0-04 Flowable 同库同事务 PostgreSQL PoC

基于 Spring Boot 3.3.5、Flowable 7.1.0、JDK 17 的本机可编译候选组合；NexusOne 产品基线为 Java 21，仍须在 Java 21 及冻结版本上复验。Flowable 作为应用内引擎，使用 Spring 提供的单一 `DataSource` 和 `PlatformTransactionManager`。JDBC `currentSchema=flowable,public` 及 `flowable.database-schema=flowable` 将引擎表放在 `flowable` Schema，业务样例表明确放在 `public`。Flowable 自行管理引擎表；业务代码只通过 Flowable Service API 访问流程，不查询引擎内部表。

## 准备数据库

在独立 PostgreSQL 测试实例中，以管理员创建登录角色 `nexus_poc`（设置密码）和数据库 `nexus_poc`。连接该数据库运行 `setup.sql`。示例：

```powershell
psql -U postgres -d nexus_poc -f setup.sql
$env:POC_JDBC_URL = 'jdbc:postgresql://localhost:5432/nexus_poc?currentSchema=flowable,public'
$env:POC_DB_USER = 'nexus_poc'
$env:POC_DB_PASSWORD = '<test-password>'
mvn -q -Dtest=FlowablePostgresIT test
```

运行前需确保角色有 `flowable` Schema 的建表权及 `public.business_marker` 的 DML 权。不要把真实密码写入仓库。`FlowablePostgresIT` 检查 PostgreSQL 数据库类型、引擎表 Schema、共享事务管理器、BPMN 人工任务及业务写入与流程实例的共同回滚。测试数据使用 UUID，适合重复运行；样例记录会保留在测试库。

## 当前实测

- `mvn -q -DskipTests package`：通过（包括测试源码编译）。
- 原有 PostgreSQL 服务要求 SCRAM 密码，首次尝试因未提供 `POC_DB_PASSWORD` 而失败。
- 后续在独立本地 PostgreSQL 15.18 测试实例 `127.0.0.1:55438/nexus_p0_04` 运行 `mvn -q -Dtest=FlowablePostgresIT test`：**2 个测试通过，0 失败**。覆盖同 DataSource/事务管理器、独立 `flowable` Schema、人工任务及业务写入与流程实例共同回滚。该临时实例仅绑定回环地址，使用本地 host trust；结果不代表目标交付环境。
- 本机 Maven 使用 JDK 17；PostgreSQL 15 Windows 服务运行中；未检测到 Docker。

## 后续门禁

仍须在 Java 21/目标 PostgreSQL Profile 补测 DMN、异步作业、引擎 Schema 升级及应用回滚后的数据库兼容性；当前没有这些结果，也没有发布结论。生产环境应将 `flowable.database-schema-update` 改为受控升级策略，不在应用启动时自动修改引擎 Schema。

参考：[Flowable 7.1.0 Spring Boot 文档](https://github.com/flowable/flowable-engine/blob/flowable-7.1.0/docs/docusaurus/docs/bpmn/ch05a-Spring-Boot.md)、[Flowable Spring 事务文档](https://github.com/flowable/flowable-engine/blob/flowable-7.1.0/docs/docusaurus/docs/bpmn/ch05-Spring.md)。
