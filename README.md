# NexusOne

## 团队任务入口

- [团队领取任务清单 V1.1](./团队领取任务清单_V1.1.md)：23 张可独立分配的 P0 叶子任务、M0 分工建议、前置依赖与完成判定。
- [实施任务拆解 V1.1](./实施任务拆解_V1.1.md)：阶段、父任务、历史 PoC 证据与验收门禁。
- [P0 叶子 Issues](https://github.com/EricBeckham/NexusOne/issues?q=is%3Aissue+label%3Atype%3Aleaf)：待团队成员领取并填写负责人、评审人、交付位置和证据。
- [M0 父 Issues](https://github.com/EricBeckham/NexusOne/issues?q=is%3Aissue+label%3Aphase%3AM0)：P0 门禁完成后推进实现。

需求基准：[需求规格 V1.1](./星枢企业业务平台_需求规格_V1.1.md)；技术基准：[技术框架 V1.1](./星枢企业业务平台_技术框架_V1.1.md)。

当前执行口径：**仅拆分和交接任务，由团队成员实施。**数据库为本机 PostgreSQL 15，缓存为本机 Redis 3.2；Java 应用直接以本机 JAR/进程运行，**不使用 Docker**。凭据只由领取人通过本机环境变量或受控 Secret 提供，不写入仓库或 Issue。`poc/` 是此前验证资产，不能自动视为正式验收通过。
