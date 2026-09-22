# P0-07 本机故障转移实测记录

- 日期：2026-09-22；环境：Windows x86_64、Java 17、Quartz 2.5.2、PostgreSQL 15.18，`127.0.0.1:55438/nexus_p0_07`，用户 `nexus_poc`，仅回环地址/本地 host trust。
- 范围：专用 PoC 数据库和脚本启动的 `poc.Main failover-node` Java 进程；未停止 PostgreSQL 或其他任务进程。
- 构建：`mvn -q -DskipTests compile` 成功；`mvn -q dependency:build-classpath` 成功。
- 首次脚本运行在 `poc_recovery_log` 尚不存在时查询状态而中止；确认无 PoC Java 节点残留后，修正为状态查询前创建测试表。
- 修正后首次实测 Run ID `a995cc44-8b73-4097-af1b-9004903d2c36`：节点 A PID `19348` 开始长任务，节点 B PID `640` 启动后，脚本验证 A 的命令行并终止 A；B 记录恢复和完成。数据库为 `START=1, RECOVER_START=1, COMPLETE=1`，实例 ID 各异，`failover-check` 通过。
- 加入存活节点正常退出步骤后复测 Run ID `eba0a2c7-be57-4994-84a7-680112e4622e`：A PID `24496`、B PID `20284`。A 实例 `LAPTOP-Surface1790061066316` 记录 `START`；B 实例 `LAPTOP-Surface1790061074978` 记录 `RECOVER_START`、`COMPLETE`。数据库计数仍为 `1/1/1`，两个实例 ID 不同；`failover-check` 通过，退出码 0。结束后检查 `poc.Main failover-node` 进程数为 0。
- 复测结束后 `quartz.qrtz_scheduler_state` 尚有 B 的一条 check-in 行，虽然 B 进程已退出；需在部署监控中考虑旧行清理/超时，不把该字段直接当成活跃进程数。

结论：在此本机 PostgreSQL PoC 中，Quartz 对一个标记 `requestRecovery(true)` 的运行中任务完成了跨节点恢复。这个测试不覆盖外部副作用去重、业务事务幂等、多任务并发、网络分区、目标 Linux/CPU/JDK 21 Profile 或升级回滚。故障恢复意味着任务可能重跑，实际业务 Job 必须以 PostgreSQL 中的幂等键和状态机保护副作用。
