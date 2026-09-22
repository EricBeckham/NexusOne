# P0-05：Redis 缓存契约验证资产

对应任务：[GitHub Issue #5](https://github.com/EricBeckham/NexusOne/issues/5)。本目录使用 `redis:8.2.10-bookworm` 作为**候选验证版本**，不是已冻结的产品版本。Redis 8 的授权可选 RSALv2、SSPLv1 或 AGPLv3；正式交付采用哪种授权、镜像 digest、SBOM、CVE 和支持期限均需单独记录并经法务/交付评审。Redis 官方列出 [各版本授权差异](https://redis.io/legal/licenses/)：7.2 及以前为 BSD-3-Clause，7.4 至 7.8 为 RSALv2/SSPLv1，8 起增加 AGPLv3 选项。

## 运行

在本目录执行：

```powershell
docker compose config --quiet
docker compose up -d
docker compose exec -T redis sh /opt/poc/cache-contract.sh
docker compose exec -T redis redis-cli INFO server
docker compose images
docker compose down
```

脚本覆盖：`EX`/`TTL`/过期、`INCR`、`SET NX PX` 锁争用、按随机 Token 的 Lua 安全解锁、两租户 Key 前缀。每项成功时打印 `PASS`，失败时退出非零。脚本使用唯一运行 ID 并在退出时清理自己的 Key。Key 规范示例为 `nexus:v1:{tenant-id}:cache:<domain>:<resource>`；花括号也让同租户 Key 在 Cluster 下使用相同 hash tag，但实际 Cluster 行为尚未验证。

Compose 没有映射宿主端口，且关闭 RDB/AOF，因为此资产仅验证缓存语义，不保存业务唯一事实。若测试生产部署，必须另外设计 ACL、认证、TLS、持久化/复制/高可用与监控。关键幂等 Key 和业务状态应存 PostgreSQL；Redis 丢失后，服务仍须按数据库唯一约束和状态机保持正确性。

## 尚未执行的验收

| 项目 | 执行要点 | 状态 |
|---|---|---|
| 故障转移 | 在目标 Sentinel/Cluster Profile 中启动主从，持续读写并杀主节点，测恢复时间、短暂错误、数据丢失窗口与客户端重连 | 未运行 |
| 缓存丢失 | `FLUSHDB`/重启仅在隔离环境进行；验证权限快照与业务查询重新加载，数据库幂等不失效 | 未运行 |
| 锁超时竞争 | 两客户端在锁 TTL 过后争用，旧持有者不得删掉新持有者锁；补业务 fencing/数据库状态保护 | 未运行 |
| 租户访问防线 | 通过产品 `CachePort` 以租户 A 身份请求 B Key，要求服务端在 Redis 命令发出前拒绝；当前脚本只证实命名隔离 | 未运行 |
| 容量/性能 | 在目标 OS/CPU 下按真实 Key 数、TTL 分布与并发记录 P95、内存/淘汰与故障恢复 | 未运行 |
| 版本/授权交付 | 固定 digest、许可证选择、许可证文本、镜像来源、SBOM、CVE 和离线包 | 未运行 |

本机缺少 Docker CLI，所以尚未实际启动 Redis 或运行上述脚本。`docker compose config --quiet` 也待 Docker 环境验证；当前只执行了脚本和 YAML 的静态解析。

## 文档依据

- [Redis 分布式锁说明](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)
- [Redis 官方授权说明](https://redis.io/legal/licenses/)
- [Docker 官方 Redis 镜像](https://hub.docker.com/_/redis)
- [Docker Compose 配置验证](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)
