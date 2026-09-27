# KC2–KC3 · Keycloak 登录视觉 theme

预备/本机验证 ≠ 演示机部署验收。产品 `/login` 是 SPA；CTA 后的 username/password 页来自 Keycloak，本卡不改前端授权行为、不改 realm/client/用户、credential、IAM/U2 或任何演示机 env。

## 资源与构建检查

主题 `deploy/keycloak/themes/nexusone/login` 继承固定 Keycloak 26.7.4 的 `keycloak`，不复制或覆盖 `.ftl` 登录表单，不添加自定义认证 JS。原生密码可见性、错误消息、回跳、remember-me、MFA/其它认证器均由官方模板处理。

`nexus-tokens.css` 只机械提取 `web/src/styles/tokens.css` 的 root + P2 tokens；新增视觉只引用这些变量。白卡、浅背景、紫蓝主按钮、系统字体和文字品牌沿用 LOGIN 基线。没有新色板、外链主题或 logo 占位；文字品牌不是阻塞项。主 CTA 原生 submit 时 disabled，沿用禁用/等待态，不增加认证逻辑或假加载。

在本 worktree 根自行执行：

```powershell
node deploy/keycloak/scripts/sync-theme-tokens.mjs
node deploy/keycloak/scripts/sync-theme-tokens.mjs --check
node --test deploy/keycloak/scripts/theme.test.mjs
```

## 本地查看（只在自己的 worktree）

已有开发容器按 [开发预备 README](../../../deploy/keycloak/README.md) 启停；compose 只读挂载 `themes/nexusone`，不会自动选择 theme。不要为了预览改仓内 realm JSON、client 回跳或账号业务配置。

也可将官方 26.7.4 发行包解压到本 worktree `.local/keycloak/`，将仓内 `nexusone` 主题复制到该发行包的 `themes/nexusone`。无需用户或 bootstrap admin 密码，使用全新内置 H2、本机回环端口；不连接已有数据库：

```powershell
# JAVA_HOME 指向本机受支持 JDK；TEMP/TMP、java.io.tmpdir 均指向本 worktree .local/tmp。
# 仅供本地隔离预览，不能作为演示机启动配置。
.local/keycloak/keycloak-26.7.4/bin/kc.bat start-dev --http-host=127.0.0.1 --http-port=18081 --spi-theme--default=nexusone --spi-theme--cache-themes=false --spi-theme--cache-templates=false --spi-theme--static-max-age=-1
```

本地预览必须直接走 account-console 的授权入口，并附 `code_challenge_method=S256` 和一个合成 public challenge：缺 PKCE 会被官方 client 拒绝。本地 `--spi-theme--default` 会影响所有类型，不能直接打开 `/account/`（本主题没有 account 类型）；此参数仅供隔离预览，正式部署只选择 Login Theme。

例如以下纯视觉地址只用于本机预览（合成 challenge 没有对应 verifier，不能据此换码；不输入真实身份/密码）：

```text
http://127.0.0.1:18081/realms/master/protocol/openid-connect/auth?client_id=account-console&redirect_uri=http%3A%2F%2F127.0.0.1%3A18081%2Frealms%2Fmaster%2Faccount%2F&response_type=code&scope=openid&code_challenge_method=S256&code_challenge=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
```

中文 bundle 为 `messages_zh_CN.properties`；真实环境是否启用中文由既有 realm locale 设置决定，本卡不自动改变 locale 配置。预览只证明主题渲染，不证明 NexusOne client 回跳或 Keycloak 三人登录；用完停止本次进程。缓存关闭参数只用于本机，生产不使用。所有 DB/日志/浏览器 profile/下载产物只在本 worktree `.local/`。

## 合入后的正规部署（由 Eric/父代理另行授权）

1. 审核、合入 PR 后，以该 commit 构建 Keycloak 镜像/部署包；将完整 `nexusone/login` 纳入发行包 `/opt/keycloak/themes/nexusone/login` 或受控只读挂载，不在运行中的演示机手改 CSS/theme。
2. 由有权 IdP 运维者按正式发布流程选用 Login Theme `nexusone`（含 client 级 theme override 时核对生效来源）。这只是选择视觉，不变更登录 flow、用户、密码、client secret 或回跳 URL；本 PR 不自动改任何 realm 配置。
3. 按现有 Keycloak 正规重启/滚动发布与缓存策略上线，生产缓存保持启用。不通过 jar/SPA/theme 热修代替发布，也不删已有 Keycloak 数据。
4. 硬刷新产品 `/login`，点「使用 Keycloak 登录」；确认 issuer、client、回跳仍是发布前受控配置。查实际主题资源为 `nexusone`，没有默认主题 fallback/404。再按 [M7](../../planning/M7-Keycloak演示验收清单.md) 执行真实登录验收；口令/token/cookie/Authorization 不进证据或仓库。
5. 回滚使用上一受控主题包与选用设置，不删除账号/realm/DB。本地原生表单测试不是外网演示机验收。

## 自检清单

- [ ] 中文/英文文字品牌为「星枢 NexusOne」；无随机图/外链字体。
- [ ] 背景/白卡/间距/圆角/系统字体沿用 WEB/LOGIN tokens，1440 与 768 无横向溢出。
- [ ] username/password 标签及密码可见性仍可用；一个主 submit，禁用态保持原生行为。
- [ ] 错误摘要与字段错误可见，danger token，键盘焦点清晰。
- [ ] 正式 `/login` → Keycloak → client 回跳由发布后 M7 检查；本机预览不得代勾。

## 本机执行结果（2026-09-27）

- token 同步检查与 3 项 Node 契约测试通过。
- 官方 26.7.4 独立发行包、全新内置 H2、回环 18081，无 bootstrap admin/用户创建，文件均在本 worktree；无 Docker，未运行 compose。
- Chrome 对真实服务端模板检查 1440/768/375：实际 theme CSS 已加载；浅背景/P2 主按钮 token、文字品牌、密码显示/隐藏及无横向溢出通过。截图：[登录](../../design/evidence/kc-login-theme/login.png)、[原生错误](../../design/evidence/kc-login-theme/error.png)。截图无口令/账号凭证。
- 仅向全新本地 IdP 提交不存在的合成用户名 + 内存生成的随机密码，原生 `input-error`、`aria-invalid` 与 danger token 通过；不创建用户，不涉及演示机。首个无 PKCE 预览请求被官方 client 拒绝，随后使用 S256 预览成功，未修改 client 策略来绕过。
- 默认 master 未开启国际化，所以真实截图为英文表单；中文标题资源契约已检查，中文全流程与真实 client 回跳未验收，不改 realm 来代勾。

未验收项留空；本 PR 保持打开，等 Eric「合」。发布阻塞：theme 尚未正式部署/选用，须合入后由 Eric/父代理正规发布并按 M7 验收，不是产品实现缺规范。
