import { createRouter, createWebHistory } from "vue-router";

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
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
