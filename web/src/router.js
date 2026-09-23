import { createRouter, createWebHistory } from "vue-router";
import { session } from "./api/session";
import { isSafeMobileReturn, safeMobileReturn } from "./mobile/auth/safeReturn";
import { postHost } from "./mobile/host/hostBridge";

const routes = [
  { path: "/login", name: "login", component: () => import("./views/LoginView.vue"), meta: { public: true, title: "登录" } },
  { path: "/callback", name: "callback", component: () => import("./views/CallbackView.vue"), meta: { public: true, title: "授权码回跳" } },
  { path: "/", name: "home", component: () => import("./views/HomeView.vue"), meta: { title: "选择产品" } },
  { path: "/p1", name: "p1-shell", component: () => import("./views/ProductShellView.vue"), meta: { title: "星域", product: "p1" } },
  { path: "/p2", name: "p2-shell", component: () => import("./views/ProductShellView.vue"), meta: { title: "星权", product: "p2" } },
  { path: "/p3", name: "p3-shell", component: () => import("./views/ProductShellView.vue"), meta: { title: "星构", product: "p3" } },
  { path: "/p1/tenants", name: "tenants", component: () => import("./views/tenants/TenantListView.vue"), meta: { title: "租户目录", product: "p1" } },
  { path: "/p1/tenants/new", name: "tenant-create", component: () => import("./views/tenants/TenantCreateView.vue"), meta: { title: "创建租户", product: "p1" } },
  { path: "/p1/tenants/:id", name: "tenant-detail", component: () => import("./views/tenants/TenantDetailView.vue"), meta: { title: "租户详情", product: "p1" } },
  { path: "/p2/persons", name: "persons", component: () => import("./views/org/PersonListView.vue"), meta: { title: "用户目录", product: "p2" } },
  { path: "/p2/org", name: "org", component: () => import("./views/org/OrgTreeView.vue"), meta: { title: "组织架构", product: "p2" } },
  { path: "/p2/positions", name: "positions", component: () => import("./views/org/PositionView.vue"), meta: { title: "岗位任职", product: "p2" } },
  { path: "/p2/mfa/enroll", name: "totp-enroll", component: () => import("./views/org/TotpEnrollView.vue"), meta: { title: "TOTP 注册", product: "p2", public: true } },
  { path: "/p2/mfa/recovery", name: "mfa-recovery", component: () => import("./views/org/RecoveryCodesView.vue"), meta: { title: "恢复码", product: "p2", public: true } },
  { path: "/p2/mfa/email", name: "mfa-email", component: () => import("./views/org/EmailMfaView.vue"), meta: { title: "邮箱 MFA", product: "p2", public: true } },
  { path: "/p2/permissions/:kind?", name: "permissions", component: () => import("./views/permission/PermissionStubView.vue"), meta: { title: "权限", product: "p2" } },
  { path: "/p3/ledgers", name: "ledgers", component: () => import("./views/studio/LedgerListView.vue"), meta: { title: "台账", product: "p3" } },
  { path: "/p3/ledgers/:id", name: "ledger-detail", component: () => import("./views/studio/LedgerDetailView.vue"), meta: { title: "台账详情", product: "p3" } },
  { path: "/p3/forms", name: "forms", component: () => import("./views/studio/FormListView.vue"), meta: { title: "表单", product: "p3" } },
  { path: "/p3/forms/:id", name: "form-studio", component: () => import("./views/studio/FormStudioView.vue"), meta: { title: "表单填报/发布", product: "p3" } },
  { path: "/p3/todos", name: "todos", component: () => import("./views/studio/TodoInboxView.vue"), meta: { title: "待办", product: "p3" } },
  { path: "/p3/todos/:id", name: "todo-detail", component: () => import("./views/studio/TodoInboxView.vue"), meta: { title: "待办办理", product: "p3" } },
  { path: "/p3/notifications", name: "notifications", component: () => import("./views/studio/NotifyInboxView.vue"), meta: { title: "站内通知", product: "p3" } },
  { path: "/p3/audit", name: "audit", component: () => import("./views/studio/AuditTraceView.vue"), meta: { title: "审计哈希链", product: "p3" } },
  { path: "/p3/reports", name: "reports", component: () => import("./views/studio/ReportView.vue"), meta: { title: "报表表面", product: "p3" } },
  { path: "/p3/dumps", name: "dumps", component: () => import("./views/studio/DumpView.vue"), meta: { title: "物理 dump", product: "p3" } },
  { path: "/p3/exports/wizard", name: "export-wizard", component: () => import("./views/studio/ExportWizardView.vue"), meta: { title: "导出向导", product: "p3" } },
  { path: "/m", redirect: "/m/p3/home" },
  {
    path: "/m/login",
    name: "m-login",
    component: () => import("./mobile/products/p3-studio/pages/LoginPage.vue"),
    meta: { mobile: true, public: true, tabs: false, title: "H5 登录" },
  },
  {
    path: "/m/callback",
    name: "m-callback",
    component: () => import("./mobile/products/p3-studio/pages/CallbackPage.vue"),
    meta: { mobile: true, public: true, tabs: false, title: "登录回跳" },
  },
  {
    path: "/m/host",
    name: "m-host",
    component: () => import("./mobile/products/p3-studio/pages/HostMessagePage.vue"),
    meta: { mobile: true, public: true, title: "宿主消息", product: "p3" },
  },
  {
    path: "/m/host-harness",
    name: "m-host-harness",
    component: () => import("./mobile/products/p3-studio/pages/HostHarnessPage.vue"),
    meta: { harness: true, public: true, title: "宿主验收" },
  },
  {
    path: "/m/mp-webview-harness",
    name: "m-mp-webview-harness",
    component: () => import("./mobile/products/p3-studio/pages/MiniProgramWebViewHarnessPage.vue"),
    meta: { harness: true, public: true, title: "模拟小程序 web-view" },
  },
  {
    path: "/m/app-webview-harness",
    name: "m-app-webview-harness",
    component: () => import("./mobile/products/p3-studio/pages/AppWebViewHarnessPage.vue"),
    meta: { harness: true, public: true, title: "APP WebView 模拟宿主" },
  },
  {
    path: "/m/wx-dt-harness",
    name: "m-wx-dt-harness",
    component: () => import("./mobile/products/p3-studio/pages/WeChatDingTalkHarnessPage.vue"),
    meta: { harness: true, public: true, title: "微信钉钉生产宿主契约桩" },
  },
  {
    path: "/m/native-bridge-harness",
    name: "m-native-bridge-harness",
    component: () => import("./mobile/products/p3-studio/pages/NativeBridgeHarnessPage.vue"),
    meta: { harness: true, public: true, title: "APP 原生桥契约桩" },
  },
  {
    path: "/m/wx-dt-prod-gap-harness",
    name: "m-wx-dt-prod-gap-harness",
    component: () => import("./mobile/products/p3-studio/pages/WeChatDingTalkProdGapHonestyPage.vue"),
    meta: { harness: true, public: true, title: "微信钉钉生产验收缺口诚实" },
  },
  {
    path: "/m/native-bridge-honesty-harness",
    name: "m-native-bridge-honesty-harness",
    component: () => import("./mobile/products/p3-studio/pages/NativeBridgeHonestyPage.vue"),
    meta: { harness: true, public: true, title: "APP 原生桥诚实 leftover" },
  },
  {
    path: "/m/p3/home",
    name: "m-home",
    component: () => import("./mobile/products/p3-studio/pages/HomePage.vue"),
    meta: { mobile: true, title: "星构", product: "p3" },
  },
  {
    path: "/m/p3/files",
    name: "m-files",
    component: () => import("./mobile/products/p3-studio/pages/FilesPage.vue"),
    meta: { mobile: true, title: "文件", product: "p3" },
  },
  {
    path: "/m/p3/files/:id",
    name: "m-file-detail",
    component: () => import("./mobile/products/p3-studio/pages/FileDetailPage.vue"),
    meta: { mobile: true, back: true, tabs: false, title: "文件详情", product: "p3" },
  },
  {
    path: "/m/p3/forms",
    name: "m-forms",
    component: () => import("./mobile/products/p3-studio/pages/FormsPage.vue"),
    meta: { mobile: true, title: "表单", product: "p3" },
  },
  {
    path: "/m/p3/forms/:id",
    name: "m-form-fill",
    component: () => import("./mobile/products/p3-studio/pages/FormFillPage.vue"),
    meta: { mobile: true, back: true, tabs: false, title: "填报", product: "p3" },
  },
  {
    path: "/m/p3/lists",
    name: "m-lists",
    component: () => import("./mobile/products/p3-studio/pages/ListsPage.vue"),
    meta: { mobile: true, title: "列表", product: "p3" },
  },
  {
    path: "/m/p3/lists/:id",
    name: "m-list-records",
    component: () => import("./mobile/products/p3-studio/pages/ListRecordsPage.vue"),
    meta: { mobile: true, back: true, tabs: false, title: "记录", product: "p3" },
  },
  {
    path: "/m/p3/lists/:id/records/:recordId",
    name: "m-record-detail",
    component: () => import("./mobile/products/p3-studio/pages/RecordDetailPage.vue"),
    meta: { mobile: true, back: true, tabs: false, title: "记录详情", product: "p3" },
  },
  {
    path: "/m/p3/todos",
    name: "m-todos",
    component: () => import("./mobile/products/p3-studio/pages/TodosPage.vue"),
    meta: { mobile: true, title: "待办", product: "p3" },
  },
  {
    path: "/m/p3/todos/:id",
    name: "m-todo-detail",
    component: () => import("./mobile/products/p3-studio/pages/TodoDetailPage.vue"),
    meta: { mobile: true, back: true, tabs: false, title: "办理", product: "p3" },
  },
  {
    path: "/m/p3/query",
    name: "m-query",
    component: () => import("./mobile/products/p3-studio/pages/QueryPage.vue"),
    meta: { mobile: true, title: "查询", product: "p3" },
  },
  {
    path: "/m/p3/notifications",
    name: "m-notifications",
    component: () => import("./mobile/products/p3-studio/pages/NotifyPage.vue"),
    meta: { mobile: true, title: "通知", product: "p3" },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  if (to.meta.harness || !to.meta.mobile || to.meta.public) {
    return true;
  }
  if (session.accessToken || session.accountId || session.contextId) {
    return true;
  }
  const returnTo = isSafeMobileReturn(to.fullPath) ? to.fullPath : safeMobileReturn(to.path);
  return { path: "/m/login", query: { return: returnTo } };
});

router.afterEach((to) => {
  if (to.meta.mobile && !to.meta.harness) {
    postHost("route", { action: "changed", path: to.fullPath });
  }
});

export default router;
