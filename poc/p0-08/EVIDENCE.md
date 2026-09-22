# P0-08 本机运行记录（2026-09-22）

环境：Windows，Surface Laptop 4，AMD Ryzen 5（6 核/12 线程）、约 8 GB 内存；独立本地 PostgreSQL 15.18 测试实例 `127.0.0.1:55438`，数据库 `nexus_p0_08`。单连接顺序执行，默认数据库参数；非目标部署 Profile。

命令：`./bench.ps1 -TenantCount 1000 -CustomPerTenant 1 -SharedTables 5`。先运行 10 租户预检，再扩展至 1,000 租户；重复运行一次以采集新增的 100 次 Catalog 查询与分批 drift 指标。第二次运行会跳过既有表，因此 DDL 样本来自首次建表。

| 指标 | 本次观察值 |
|---|---:|
| Quota/identifier/drift 契约 | PASS（事务回滚） |
| 自建台账表 | 1,000 |
| 应用共享台账表 | 5 |
| 动态表索引（含主键） | 2,010 |
| 共享表租户数据 | 每张表 1,000 租户各一行 |
| 建表及索引 DDL P95 | 11.789 ms（1,005 个样本） |
| 建表及索引 DDL max | 26.812 ms |
| Catalog count 查询 P95 | 1.6212 ms（100 次，同一会话热缓存） |
| Catalog count 查询 max | 1.791 ms |
| SchemaDriftCheck | 11 批 × 100，0 个问题 |
| SchemaDriftCheck 单批 P95 | 3.32 ms |

采集日志（本机忽略目录）：`results/bench-20260922-150045.log` 与 `results/bench-20260922-150124.log`。日志中的 `EXPLAIN (ANALYZE, BUFFERS)` 最后一次执行时间 2.403 ms。上述结果仅证明脚本在该开发机可执行；**不据此冻结最大表数、最大索引数或产品性能阈值**。单台账 1,000 万行、并发 DDL、故障恢复、目标 Linux/ARM64 和长期 Catalog 退化未测。
