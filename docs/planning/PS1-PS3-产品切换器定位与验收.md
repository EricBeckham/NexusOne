# PS1–PS3 · 产品切换器贴左栏底

## PS1 定位（只读结论）

- 壳：`web/src/layout/AppShell.vue`；侧栏 `aside.side` 为 sticky/top:0/100vh，原先整栏 overflow:auto。
- 导航：`web/src/layout/ProductSidebar.vue`；头部与菜单为 fragment，普通文档流。
- 切换器：`web/src/layout/ProductSwitcher.vue`；普通 nav，原先 margin-top:16px，只跟随菜单末尾，既没有 flex 剩余空间也没有独立底部槽，因此短菜单不贴底、长菜单会把它卷走。
- 路由入口：`web/src/nav/products.js`，仍使用各产品原来的 home；业务语义不变。
- 规范：`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md` §2 tokens、§3 Desktop（200/64 侧栏）、§5 导航、§47 CSS；`web/src/styles/tokens.css`。折叠沿用现有列状图标与 title，无额外窄屏模式。

## PS2 布局

侧栏 sticky + 100dvh + flex column；菜单位于 flex:1/min-height:0 的独立滚动区；切换器作为不收缩的直接底部槽。主内容滚动和菜单滚动均不带走切换器。不使用全局 fixed，不遮主内容，也不改产品切换行为。新增间距只用既有 token。

## PS3 核对

- `AppShell.browser.test.js` 检查底部槽结构、flex/overflow 契约、五产品路由和折叠入口；happy-dom 不作为几何验收。
- 真浏览器检查 1920 / 1440 / 1280 / 768 宽：短菜单底部、长菜单独立滚动；滚动主内容后底部位置不变；折叠后五个图标可点击且 title 保留。
- PR 正文附实际执行结果；未执行的真浏览器步骤不得勾绿。只开 PR，等 Eric「合」；合入后正规 SPA 构建部署，不热修演示机。

## 本机执行结果

2026-09-27：壳测试 10 项通过；`npm run build` 通过。独立 Vite 预览仅挂真实 AppShell 与模拟长主内容，没有后端、登录或身份写入；Chrome 在 1920/1440/1280/768 × 展开/折叠共 8 组检查通过，900 高窗口内切换器 bottom=884（保留既有底部 16 token padding），主内容/菜单滚动后位置不变，逐个点击五产品 home 成功。

另以真实 P3 菜单在 1280×600 验证长菜单：滚动区 clientHeight=463、scrollHeight=676，实际 scrollTop>0，切换器仍 bottom=584。

截图：[展开](../design/evidence/ps-bottom/expanded.png)、[折叠](../design/evidence/ps-bottom/collapsed.png)。这仅为本机壳视觉/路由验收，不是产品 API 或演示机验收。
