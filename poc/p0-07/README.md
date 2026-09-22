# P0-07：Quartz / Flyway + PostgreSQL 验证资产

对应任务：[GitHub Issue #7](https://github.com/EricBeckham/NexusOne/issues/7)。候选组合为 Quartz `2.5.2`、Flyway `13.7.0`、PostgreSQL `17`、PostgreSQL JDBC `42.7.8`；版本、授权、镜像 digest 和目标 Java 21/Linux/CPU Profile 在运行矩阵完成前不能正式冻结。这个最小工程以 Java 17 编译以便本机做静态构建，产品基线 Java 21 须另行验证。

## 启动

需要 Docker Compose v2、JDK 17+、Maven 3.9+。本地 PostgreSQL 绑定 `127.0.0.1:55437`，仅用于 PoC。于本目录运行：

```powershell
Copy-Item .env.example .env
# 将 .env 中占位密码换成随机本机密码；不要提交 .env。
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --wait
$env:P0_07_DB_PASSWORD = '<与 .env 中 POSTGRES_PASSWORD 一致>'
mvn -q -DskipTests compile
```

首次启动时 PostgreSQL 镜像初始化脚本创建 `quartz`、`platform_static` 两个 Schema，加载 Quartz `2.5.2` 官方 PostgreSQL 表 DDL，并创建测试日志表。`init/tables_postgres.sql` 来源为 [Quartz v2.5.2 源码](https://github.com/quartz-scheduler/quartz/blob/v2.5.2/quartz/src/main/resources/org/quartz/impl/jdbcjobstore/tables_postgres.sql)，下载后 SHA-256 为 `60D22E05E00203702F18F835916C75270AEB6828E7051BB81DD87EBD7BF6A943`。这些初始化脚本仅在数据库卷为空时执行；重跑应使用新的隔离数据库，而非在已有数据上重复执行 DDL。

## Flyway 静态迁移与失败恢复

依次运行，每条输出必须有 `PASS`：

```powershell
mvn -q exec:java -Dexec.args=flyway-base
mvn -q exec:java -Dexec.args=flyway-fail
mvn -q exec:java -Dexec.args=flyway-recover
```

V1 创建静态配置表，V2 扩列。故障路径 V3 在建表后执行除零，预期 PostgreSQL 的事务 DDL 回滚，脚本校验半成品表不存在。随后以修正后的 V3 重新迁移并 `validate`，检查历史表有三条成功迁移。**失败迁移的修正文件只适用于本 PoC 的预发布演练**；实际发布后不能随意改动已应用的版本迁移文件，需新增前向修复迁移或恢复数据库备份。Flyway 只管理平台静态表，不管理 P3 动态台账物理表。

## Quartz JDBC JobStore、集群与 Misfire

先执行调度（此时尚无运行节点）：

```powershell
mvn -q exec:java -Dexec.args=quartz-schedule
```

在 **两个独立终端**中同时执行下列命令；每个节点运行 30 秒。调度命令预置 20 个未来触发器及 1 个已经过期的触发器，后者指定 `FireNow` Misfire 指令。若距离调度已超过 12 秒，普通触发器也可能被视为 Misfire，但仍应各执行一次。

```powershell
$env:P0_07_DB_PASSWORD = '<与 .env 一致>'
mvn -q exec:java -Dexec.args=quartz-node
```

两个节点都退出后：

```powershell
mvn -q exec:java -Dexec.args=quartz-check
docker compose --env-file .env exec -T postgres psql -U poc -d nexus_p0_07 -c 'select scheduler_instance, count(*) from quartz.poc_fire_log group by scheduler_instance;'
```

`quartz-check` 要求 20 个集群任务和 1 个 Misfire 任务各执行一次；测试日志表的 `job_key` 主键阻止重复记录。查看两个节点日志中是否有异常；数据库计数本身不能证明未尝试重复执行。Quartz 的 `JobStoreTX`、`PostgreSQLDelegate`、`isClustered=true`、自动 instance ID 和 `misfireThreshold` 均在代码中显式配置。

## 未覆盖的门禁

| 项目 | 待补证据 | 当前状态 |
|---|---|---|
| 真正故障转移 | 两节点运行时强制杀死持有执行中的节点，监测恢复、重复副作用、misfire 和重试；需业务幂等表保护 | 未运行 |
| 并发/压力 | 多节点长期运行、时钟同步、锁竞争、Misfire 积压与 P95 | 未运行 |
| 升级/回滚 | 旧版 Quartz 表和 Flyway 迁移升级、备份恢复、旧应用版本兼容 | 未运行 |
| 生产隔离 | 使用最小权限数据库账号；本 PoC 的镜像初始化账号 `poc` 是超级用户 | 未运行 |
| 交付矩阵 | Java 21、目标 Linux/CPU、PostgreSQL 版本、许可证、SBOM/CVE、离线源 | 未运行 |

本机没有 Docker CLI、`psql` 或可用的隔离 PostgreSQL 测试库及凭据；数据库测试尚未运行。已在本机以 Maven 3.9.15 / Java 17 执行 `mvn -q -DskipTests compile`，退出码为 0；Compose YAML 静态解析通过。这些静态结果不代表 PostgreSQL 契约通过。

## 来源

- [Quartz 2.5 JDBC JobStore 集群配置](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/configuration/ConfigJDBCJobStoreClustering.html)
- [Quartz 2.5 JobStoreTX 配置](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/configuration/ConfigJobStoreTX.html)
- [Quartz PostgreSQL 表 DDL](https://github.com/quartz-scheduler/quartz/blob/v2.5.2/quartz/src/main/resources/org/quartz/impl/jdbcjobstore/tables_postgres.sql)
- [Flyway PostgreSQL 模块](https://github.com/flyway/flyway/blob/main/documentation/Reference/Database%20Driver%20Reference/PostgreSQL%20Database.md)
- [Flyway Java API](https://documentation.red-gate.com/flyway/reference/usage/api-java)
