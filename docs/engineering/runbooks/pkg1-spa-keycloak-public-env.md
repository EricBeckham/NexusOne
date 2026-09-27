# PKG1 · SPA 打包必须带 Keycloak 公开配置

适用：演示、预发、正式，**同一条规则**。只写变量名、路径和检查方法；不写真实 issuer、client id 以外的任何值，也不写任何密钥。本文不代表任何环境已部署或已验收。

## 1. 根因

- 产品登录页读两个 Vite 变量：`VITE_NEXUS_KEYCLOAK_ISSUER`（公开 issuer URL）和 `VITE_NEXUS_KEYCLOAK_CLIENT_ID`（公开 client id）。读取点是 `web/src/auth/keycloakPublic.js`。
- 这两个值**只在 Vite 构建期**注入，被直接编进 `dist`。运行期改服务器 env、改后端配置、重启 jar 都不会改变已打好的 SPA。
- 仓库里没有这两个值：`web/.env.production` 被 `.gitignore` 的 `.env.*` 规则忽略，干净 clone / 新 worktree 里默认**没有**这个文件。
- 漏掉 → `dist` 里 `keycloakPublic` 只拿到空字符串 → 产品 `/login` 显示「登录服务暂未配置，请联系管理员。」（`data-testid="idp-unconfigured"`），没有 Keycloak 登录入口。
- 这是**前端公开配置**问题，与 U2 窗口、身份插入、TenantContext / IAM 种子无关；那些见 [TC4](tc4-demo-u2-after-keycloak-checklist.md) 与 [TC5](../../planning/TC5-三人登录TenantContext验收清单.md)。

两个值都是公开值（会出现在浏览器里），但**不得**把 client secret 或其他任何 `VITE_*` 密钥放进构建环境：凡是 `VITE_` 前缀的变量都会被打进产物。

## 2. 强制打包顺序

在要打包的那棵构建树的 `web/` 目录里依次做：

1. **注入两个变量**，从受控来源二选一：
   - 复制受控的 env 文件到 `web/.env.production`。演示可用本机样例路径 `D:\Projects\NexusOne\web\.env.production`（只读取、复制，不改它、不提交它）；
   - 或由构建机 / CI 从各自的配置或密钥管理注入同名进程环境变量（Vite 以进程环境变量优先于 `.env.*` 文件）。预发、正式用各自环境的值。

   不要把 `web/.env.production` 提交进 git，不要在文档、PR、日志里贴真实值。
2. **构建**：`npm run build`。
3. **上传前检查**：`dist/assets/keycloakPublic-*.js` 必须包含真实 issuer URL 和 client id。chunk 名随构建变化，以通配为准；若将来这个 chunk 被合并进别的文件，就对 `dist/assets/*.js` 全体做同样的检查。PowerShell 示例（在 `web/` 下执行，期望值用环境变量传入，不写进脚本）：

   ```powershell
   $f = Get-ChildItem dist\assets -Filter "keycloakPublic-*.js"
   if (-not $f) { throw "keycloakPublic chunk missing" }
   if (-not (Select-String -Path $f.FullName -SimpleMatch $env:EXPECT_KEYCLOAK_ISSUER -Quiet)) { throw "issuer missing in SPA" }
   if (-not (Select-String -Path $f.FullName -SimpleMatch $env:EXPECT_KEYCLOAK_CLIENT_ID -Quiet)) { throw "client id missing in SPA" }
   ```

4. **检查不过就停**：禁止上传这份 `dist`，禁止把这次构建标为 release / 可部署。回到第 1 步补齐变量后重新构建，不在服务器上改静态文件。

jar 和 SPA 是两份产物，**两份都要**来自同一个已合 `main` 的 SHA，各自检查。只打 jar 不重打 SPA、或从没有 env 的 worktree 打 SPA，都会出本文的故障。

`web/.env.example` 目前不在仓库里；需要样例时只写变量名和占位值（如 `https://<keycloak-host>/realms/<realm>`、`<public-client-id>`），不要复制真实 env 文件进仓库。

## 3. 验收硬条件

部署后，在硬刷新（`Ctrl + Shift + R`）过的浏览器里：

- [ ] 打开产品 `/login`，**不得**出现「登录服务暂未配置」。
- [ ] 页面有可用的 Keycloak 登录入口，点击后跳到目标环境的 Keycloak 登录页（realm 与 issuer 对应）。
- [ ] 若出现「登录服务暂未配置」：记卡点「SPA 缺 Keycloak 公开配置」，按第 2 节重新打包再部署；**不**记成 TenantContext / IAM / U2 问题，也**不**在服务器上热修 `dist`。

本条是 [TC5](../../planning/TC5-三人登录TenantContext验收清单.md) 与 [DEMO-U7](../../planning/DEMO-U7-三人演示验收清单.md) 的前置：登录入口不可用时，后续的三人登录与 TenantContext 核对都无从开始。

## 4. 历史事故（简记）

- **LOGIN 波**：产品登录壳上线时构建环境缺这两个 `VITE_` 变量，`/login` 显示「登录服务暂未配置」。
- **ID7**：从干净 worktree 打包，`web/.env.production` 不在树里，又缺一次。部署的 jar（`665968f2`，#723）是对的，SPA 是错的。

教训：jar 对不代表 SPA 对；每次打 SPA 都按第 2 节注入并检查，不要默认「上次配过」。
