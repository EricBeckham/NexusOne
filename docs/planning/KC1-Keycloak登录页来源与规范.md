# KC1 · Keycloak 登录页来源与规范

- `/login`：`web/src/views/LoginProductView.vue`，CTA 调用 `web/src/auth/keycloakPublic.js` 的 `keycloakAuthorizeHref`。
- 实际跳转：公开 issuer 下 `/protocol/openid-connect/auth`，授权码流程。表单是 Keycloak 服务端渲染，不是 Vue 中间页；本卡不读取演示机、不声称知道其当前 theme 设置。
- 原仓库：`deploy/keycloak/compose.yaml` 固定官方 26.7.4；只有 import stub 与 README，没有定制主题。不改 realm stub/client/账号或业务配置。
- 本次落点：`deploy/keycloak/themes/nexusone/login`；继承官方 `keycloak` 登录模板/脚本，仅覆盖 CSS 和中英文标题。部署形态为 Keycloak theme 目录（或正规镜像内的主题资源），不是 SPA bundle。
- 规范：`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md` §2 token/字体/间距/圆角、§9 按钮、§10 表单、§20 错误、§47 CSS、§54 响应式/截图；`web/src/styles/tokens.css`；已合 LOGIN 的 `LoginProductView.vue`（P2 紫蓝、浅背景白卡、文字品牌）。仓内没有独立 IdP 视觉稿，沿用上述已实现登录基线，不自造色板。
- 品牌用 LOGIN 既有的「星枢 NexusOne」真实文字，不放假 logo/随机图片，不从外部 CDN 拉字体/主题。
- 官方协议参考：[主题继承与发布](https://www.keycloak.org/ui-customization/themes)、[固定 26.7.4 登录模板](https://github.com/keycloak/keycloak/blob/26.7.4/themes/src/main/resources/theme/base/login/login.ftl)。保留 form action、username/password、错误、回跳和原生提交禁用逻辑。

验收步骤、执行证据与部署边界见 [KC2–KC3 runbook](../engineering/runbooks/keycloak-login-theme.md)。不操作演示机，不改 `NEXUS_*`，不打开 U2。
