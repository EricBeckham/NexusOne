# DPO-X1 · Keycloak 开发预备（非演示机验收）

此 compose 仅本地 `start-dev`，端口 `127.0.0.1:18080`，不是 prod 配置。没有演示账号、真实 secret 或已验收结论。

在本 worktree 根运行：

```powershell
docker compose -f deploy/keycloak/compose.yaml config --quiet
docker compose -f deploy/keycloak/compose.yaml up -d
docker compose -f deploy/keycloak/compose.yaml down
```

先由本地安全注入设置 `NEXUS_DEV_KEYCLOAK_ADMIN_USER` / `NEXUS_DEV_KEYCLOAK_ADMIN_PASSWORD`，不要把真实值写进命令历史或仓库；不要输出展开后的 compose config（会含口令）。缺变量时 fail-fast。

默认导入 `REPLACE_REALM`，client 禁用，所有 `REPLACE_*` 都不是凭据。要准备可登录的本地环境：将 stub 复制到 worktree `.local/dpo-keycloak/import/`，替换 realm/client/redirect/web origin 并启用 client；在同目录创建 compose override，将 import 挂载改为该本地目录。真实 client secret 只在受控本地文件/IdP 中填写，绝不提交；不使用真实演示机凭据做本地开发。修改 realm 名后文件名也需为 `{realm}-realm.json`。已存在 realm 的启动导入不会覆盖，需管理台受控更新；不要为更新配置删除已有数据。

数据只落本 worktree `.local/dpo-keycloak/data/`（需让容器 UID 1000 可写）；停容器不会删数据。无命名 volume，无外部 PostgreSQL；开发内置 DB 不用于演示机部署。

依照 [官方容器指南](https://www.keycloak.org/server/containers) 和 [realm 导入指南](https://www.keycloak.org/server/importExport) 配置。镜像版本固定，不使用 latest。

演示机必须另按 [M7](../../docs/planning/M7-Keycloak演示验收清单.md) 与 [prod runbook](../../docs/engineering/runbooks/demo-host-prod-deployment.md) 准备 HTTPS、专用 confidential client、密钥注入及受控账号。预备 ≠ 已部署 ≠ 真机登录验收。

本轮环境没有 Docker CLI，因此未执行 compose config/up，不能宣称容器已起；JSON stub 已解析审阅，启动仍需容器运行时可用。
