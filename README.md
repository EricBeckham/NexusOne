# NexusOne

## 文档

目录说明见 [docs/README.md](./docs/README.md)。

- [团队领取任务清单 V1.1](./docs/planning/团队领取任务清单_V1.1.md)：23 张可独立分配的 P0 叶子任务、M0 分工建议、前置依赖与完成判定。
- [实施任务拆解 V1.1](./docs/planning/实施任务拆解_V1.1.md)：阶段、父任务、历史 PoC 证据与验收门禁。
- [P0 叶子 Issues](https://github.com/EricBeckham/NexusOne/issues?q=is%3Aissue+label%3Atype%3Aleaf)：待团队成员领取并填写负责人、评审人、交付位置和证据。
- [M0 父 Issues](https://github.com/EricBeckham/NexusOne/issues?q=is%3Aissue+label%3Aphase%3AM0)：P0 门禁完成后推进实现。

需求基准：[需求规格 V1.1](./docs/requirements/星枢企业业务平台_需求规格_V1.1.md)；技术基准：[技术框架 V1.1](./docs/architecture/星枢企业业务平台_技术框架_V1.1.md)。

当前执行口径：数据库为本机 PostgreSQL 15，缓存为本机 Redis 3.2；Java 应用直接以本机 JAR/进程运行，**不使用 Docker**。凭据只通过本机环境变量或受控 Secret 提供，不写入仓库或 Issue。`poc/` 是此前验证资产，不能自动视为正式验收通过。

M0-10（CachePort / File Service / 站内通知）见 [docs/m0-10-file-cache-notify.md](./docs/m0-10-file-cache-notify.md)。
M0-07（基础表单与发布）见 [docs/m0-07-form-publish.md](./docs/m0-07-form-publish.md)。G5 表单失败回滚证据见 [docs/g5-form-publish-rollback.md](./docs/g5-form-publish-rollback.md)。
G3（submit-start 幂等键与记录乐观锁）见 [docs/g3-data-consistency.md](./docs/g3-data-consistency.md)。 Outbox 消费幂等沿用 [docs/m0-09-outbox-api.md](./docs/m0-09-outbox-api.md)。

## 应用骨架（M0-01）

模块地图、错误码、Flyway 版本窗与本机 JAR 启动见 [docs/engineering/m0-01-engineering-foundation.md](./docs/engineering/m0-01-engineering-foundation.md)。

```bash
export NEXUS_DB_URL='jdbc:postgresql://127.0.0.1:5432/nexusone'
export NEXUS_DB_USER='nexus'
export NEXUS_DB_PASSWORD  # 只放环境变量
mvn -q test
mvn -q -DskipTests package
java -jar nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar
```

## 前端与双租户验收（M0-12）

最小 Vue 3 壳与 HTTP 对抗脚本。P1 租户 / P2 组织身份 / P3 台账表单待办 / 权限快照走已合并 live API。产品 G18（一条纵向闭环）已关闭，记录在 [docs/g18-business-path.md](./docs/g18-business-path.md)。清单与运行方式：[docs/m0-12-ui-acceptance.md](./docs/m0-12-ui-acceptance.md)，界面：[web/README.md](./web/README.md)。

```bash
export NEXUS_DB_URL='jdbc:postgresql://127.0.0.1:5432/nexusone'
export NEXUS_DB_USER='nexus'
export NEXUS_DB_PASSWORD  # 只放环境变量
./scripts/m0-12/run-dual-tenant.sh

cd web && npm install && npm run dev
```
