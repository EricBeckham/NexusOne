# P0-08 本机运行记录（2026-09-22）

环境：Windows 10，Surface Laptop 4，AMD Ryzen 5（6 核/12 线程）、约 7.5 GB 内存。本机已安装的 PostgreSQL 15.18，`127.0.0.1:5432`，专用库 `nexus_p0_08`。Java 进程不使用 Docker。单连接顺序执行，默认数据库参数；非目标部署 Profile。

命令：`./bench.ps1 -TenantCount 1000 -CustomPerTenant 1 -SharedTables 5`。契约在事务中验证配额、非法标识符和 drift，然后回滚。

| 指标 | 本次观察值 |
|---|---:|
| Quota/identifier/drift 契约 | PASS（事务回滚） |
| 自建台账表 | 1,000 |
| 应用共享台账表 | 5 |
| 动态表索引（含主键） | 2,010 |
| 共享表租户数据 | 每张共享表 1,000 行（`a_bench_s1` 已复核） |
| 建表及索引 DDL P95 | 22.768 ms（1,005 个样本） |
| 建表及索引 DDL max | 42.081 ms |
| Catalog count 查询 P95 | 1.5881 ms（100 次，同一会话热缓存） |
| Catalog count 查询 max | 2.100 ms |
| SchemaDriftCheck | 11 批 × 100，0 个问题 |
| SchemaDriftCheck 单批 P95 | 11.338 ms |

采集日志（本机忽略目录）：`results/bench-20260922-160037.log`。日志中的 `EXPLAIN (ANALYZE, BUFFERS)` 执行时间 3.473 ms。上述结果仅证明脚本在该开发机可执行；**不据此冻结最大表数、最大索引数或产品性能阈值**。单台账 1,000 万行、并发 DDL、故障恢复、目标 Linux/ARM64 和长期 Catalog 退化未测。
