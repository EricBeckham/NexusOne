import { createRouter, createWebHistory } from "vue-router";
import { session } from "./api/session";
import { isSafeMobileReturn, safeMobileReturn } from "./mobile/auth/safeReturn";
import { postHost } from "./mobile/host/hostBridge";

const routes = [
  { path: "/login", name: "login", component: () => import("./views/LoginView.vue"), meta: { public: true, title: "登录" } },
  { path: "/", name: "home", component: () => import("./views/HomeView.vue"), meta: { title: "工作台", product: "p1" } },
  { path: "/p1/tenants", name: "tenants", component: () => import("./views/tenants/TenantListView.vue"), meta: { title: "租户目录", product: "p1" } },
  { path: "/p1/tenants/new", name: "tenant-create", component: () => import("./views/tenants/TenantCreateView.vue"), meta: { title: "创建租户", product: "p1" } },
  { path: "/p1/tenants/:id", name: "tenant-detail", component: () => import("./views/tenants/TenantDetailView.vue"), meta: { title: "租户详情", product: "p1" } },
  { path: "/p2/persons", name: "persons", component: () => import("./views/org/PersonListView.vue"), meta: { title: "用户目录", product: "p2" } },
  { path: "/p2/org", name: "org", component: () => import("./views/org/OrgTreeView.vue"), meta: { title: "组织架构", product: "p2" } },
  { path: "/p2/positions", name: "positions", component: () => import("./views/org/PositionView.vue"), meta: { title: "岗位任职", product: "p2" } },
  { path: "/p2/permissions/:kind?", name: "permissions", component: () => import("./views/permission/PermissionStubView.vue"), meta: { title: "权限", product: "p2" } },
  { path: "/p3/ledgers", name: "ledgers", component: () => import("./views/studio/LedgerListView.vue"), meta: { title: "台账", product: "p3" } },
  { path: "/p3/ledgers/:id", name: "ledger-detail", component: () => import("./views/studio/LedgerDetailView.vue"), meta: { title: "台账详情", product: "p3" } },
  { path: "/p3/forms", name: "forms", component: () => import("./views/studio/FormListView.vue"), meta: { title: "表单", product: "p3" } },
  { path: "/p3/forms/:id", name: "form-studio", component: () => import("./views/studio/FormStudioView.vue"), meta: { title: "表单填报/发布", product: "p3" } },
  { path: "/p3/todos", name: "todos", component: () => import("./views/studio/TodoInboxView.vue"), meta: { title: "待办", product: "p3" } },
  { path: "/p3/todos/:id", name: "todo-detail", component: () => import("./views/studio/TodoInboxView.vue"), meta: { title: "待办办理", product: "p3" } },
  { path: "/p3/notifications", name: "notifications", component: () => import("./views/studio/NotifyInboxView.vue"), meta: { title: "站内通知", product: "p3" } },
  { path: "/p3/audit", name: "audit", component: () => import("./views/studio/AuditTraceView.vue"), meta: { title: "审计哈希链", product: "p3" } },
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
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  if (to.meta.harness || !to.meta.mobile || to.meta.public) {
    return true;
  }
  if (session.accessToken) {
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
