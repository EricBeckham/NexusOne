# WEB 五产品壳任务卡 V0.1

- 基线：`main@00c468b`（P5 已合入，PR #280）
- 依据：`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md`；五张首页母版图在 `docs/design/references/web/`
- 范围：只做 WEB。H5（`/m/*`、`web/src/mobile/**`）整波延后
- 执行方：本地 Codex、Cursor 等按卡领取；NexusOne 只负责拆分与审阅

## 默认决策（可改）

1. `/` 打开**产品选择页**（五张产品卡片）。
2. 审计页本轮**留在 P3**，平台审计以后再拆到 P1。
3. W0 最先做，其他任务等 W0 合并后再开工（W1 可提前）。

## 所有任务通用约束

- 每个任务从最新 `origin/main` 新开分支和 worktree，不在带未提交改动的 `D:\NexusOne` 里做。
- 只改 `web/src`（不含 `web/src/mobile/**`）。不改 H5，不改后端接口。
- 原有深链（例如 `/p3/ledgers/:id`）必须还能打开。
- 交付前必须通过：`npm test`、`vite build`、H5 页面门禁、`git diff --check`。
- 一个任务一个 PR，只含本任务改动；未跟踪的历史日志文件不要带进去。
- 页面上不编造数字；没有真实数据时显示「暂无」。

## 现状摘要（main@00c468b）

- 只有一个 M0-12 验收壳：`web/src/layout/AppShell.vue` 一条侧栏装下五个产品的全部菜单，品牌写着「M0-12 壳」。
- 顶栏只有面包屑、平台操作开关、租户切换和会话；缺全局搜索、环境标识、通知、完整用户菜单。
- 路由已按 `/p1`…`/p5` 和 `meta.product` 分好，但没有按产品切换的框架，也没有各产品首页；`/` 是跨产品验收工作台（`HomeView`）。
- `web/src/styles/tokens.css` 已有；规范第 45 节的公共组件基本未做（现仅 `NxStatus`、`NxTodo`、`NxSourceBanner`）。
- P4（`/p4/market`）、P5（`/p5/connect`）只有薄页。

## 任务总览

| 编号 | 任务 | 分支 | 前置 | 建议执行方 | 负责人 | 状态 |
|---|---|---|---|---|---|---|
| W0 | 界面框架（共用顶栏 + 产品侧栏 + 产品切换） | `ui/w0-shell` | 无 | 本地 Codex | Cursor | 进行中 |
| W1 | 公共组件最小集 | `ui/w1-components` | 无 | Cursor | Codex | 已领取 |
| W2 | P1 星域 + P2 星权挂进侧栏 | `ui/w2-p1-p2` | W0（最好也有 W1） | 空闲者 | Cursor | 进行中 |
| W3 | P3 星构挂进侧栏 | `ui/w3-p3` | W0 | 空闲者 | | 待领取 |
| W4 | P4 星市 + P5 星联 | `ui/w4-p4-p5` | W0 | 空闲者 | | 待领取 |
| W5 | 收尾（文档、截图、清单） | `ui/w5-cleanup` | W2、W3、W4 | 最后收尾者 | | 待领取 |

顺序：W0 与 W1 可同时开；W0 合并后 W2、W3、W4 并行；最后 W5。

---

## W0 界面框架（其他任务都等它）

分支：`ui/w0-shell`

要做：

- 把 `web/src/layout/AppShell.vue` 拆成 `GlobalHeader`（共用顶栏）、`ProductSidebar`（产品侧栏）、`ProductSwitcher`（产品切换）。
- 顶栏：面包屑；全局搜索入口（先占位）；环境标识（开发 / 测试 / 生产）；通知入口（跳 `/p3/notifications`）；用户菜单（用户名、租户、岗位、切换租户、退出）。原「平台操作」开关收进用户菜单，平台视图要一眼可辨（规范第 32 节）。
- 侧栏顶部显示产品图标 + 中文名 + 英文名（高 68–76px）；宽度展开 200、收起 64；菜单最多两层，一级不超过 11 项；当前项用该产品颜色浅底。
- 每个产品一份菜单配置（例如 `web/src/nav/p1.js`…`p5.js`），按路由 `meta.product` 渲染。
- 新增 `/p1`…`/p5` 五个首页占位，写清是哪个产品。
- 新增产品选择页，`/` 打开它。
- 产品颜色由 `data-product` + `tokens.css` 变量驱动。
- 去掉「M0-12 壳」字样，改为显示当前产品名。

验收：

- 五个产品之间能切换，切换后颜色、侧栏菜单、首页入口都正确。
- 原有深链都能打开。
- `/login`、`/callback` 不出现产品框架。

## W1 公共组件最小集

分支：`ui/w1-components`

要做（参照规范第 45 节，只做这几个）：

- `NxPageHeader`（页面标题区）、`NxEmpty`（空状态）、`NxErrorState`（出错）、`NxNoPermission`（无权限）、`NxStatCard`（统计卡）、`NxStatusTag`（状态标签，可包一层现有 `NxStatus`）。
- 颜色、间距只用 `tokens.css` 变量。
- 每个组件写单测；做一个示例页，只挂在开发路由下。

验收：单测通过；组件里没有写死的颜色值。尽早合入，W2–W4 会用。

## W2 P1 星域 + P2 星权

分支：`ui/w2-p1-p2`　前置：W0 已合并（最好 W1 也已合并）

要做：

- P1 菜单：首页、租户列表 `/p1/tenants`、新建租户、租户详情。租户关系、配额、平台服务态、平台审计进菜单但标「后续」。
- P2 菜单：首页、用户 `/p2/persons`、组织架构 `/p2/org`、岗位 `/p2/positions`、权限 `/p2/permissions`（现为空壳，保持原样）、MFA（`/p2/mfa/*`，放身份安全分组）。
- P1、P2 简版首页：统计卡用真实接口数据，没有就显示「暂无」。
- 列表页、详情页换成 `NxPageHeader`。

验收：P1、P2 侧栏只有各自菜单；首页能打开，没有假数据。

## W3 P3 星构

分支：`ui/w3-p3`　前置：W0 已合并

要做：

- P3 菜单：首页、台账、表单 / FormStudio、待办、通知、审计、报表、dump、导出向导。流程设计器、发布中心标「后续」。
- 把原 `/` 验收工作台（`HomeView`）的快捷入口挪到 P3 首页，然后删掉 `HomeView` 或只在开发环境保留。
- 这些页面换成 `NxPageHeader`。

验收：P3 页面只在 P3 侧栏出现；台账、表单、待办原有功能和测试不受影响。

## W4 P4 星市 + P5 星联

分支：`ui/w4-p4-p5`　前置：W0 已合并

要做：

- P4 菜单：首页、应用目录（现 `/p4/market`；如改为 `/p4/apps`，旧地址要跳转）。规范第 28 节其他子页进菜单标「后续」。
- P5 菜单：首页、接入总览（现 `/p5/connect`）。规范第 29 节的 API、Client、Webhook、Connector、Credential、调用日志进菜单标「后续」，本轮不拆成真页面。
- P4、P5 简版首页，真实数据，没有就显示「暂无」。

验收：两个产品侧栏和首页正确；P5 原有页面照常可用。

## W5 收尾

分支：`ui/w5-cleanup`　前置：W2、W3、W4 都已合并

要做：

- 更新 `web/README` 和路由表。
- 全仓再搜一遍「M0-12 壳」，确认没有残留。
- 在 `docs/planning/快进任务人员安排清单_V0.1.md` 加上本波任务和负责人，写明 H5 延后。
- 五个产品首页各截一张图附在 PR 里。

验收：文档与实际界面一致；截图在 PR 里。

## 缺口清单（本波只占位，不实现）

- P1：租户关系树、权益与配额正式页、平台服务态、平台审计中心。
- P2：权限（角色 / 数据范围 / 字段）仍为空壳；身份集成卡。
- P3：应用工作台正式首页、流程设计器、发布中心。
- P4：规范第 28 节子页（包、SKU、订阅、安装升级等）。
- P5：规范第 29 节子页（API、Client、Webhook、Connector、Credential、调用日志）。
- 工程结构：规范建议 `products/p1-tenant`…`p5-connect` + `layouts/`；本波不强制大搬迁，新框架和菜单配置按产品拆分即可。

## 修订记录

- 2026-09-24：Codex 从 `origin/main@7e12898` 领取 W1，分支 `ui/w1-components`，工作树 `C:\Users\Administrator\.codex\worktrees\w1-components\NexusOne`。
- 2026-09-24：Cursor 从 `origin/main@e7223ea` 领取 W0，分支 `ui/w0-shell`，工作树 `D:\NexusOne-w0`。
- 2026-09-24：W0 开工，拆出共用顶栏、产品侧栏和产品切换。
- 2026-09-24：Cursor 从 `origin/main@83bc784` 领取 W2，分支 `ui/w2-p1-p2`。P1、P2 菜单和简版首页开工。
