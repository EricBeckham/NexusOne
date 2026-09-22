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
  { path: "/p3/:kind", name: "studio", component: () => import("./views/studio/StudioStubView.vue"), meta: { title: "星构", product: "p3" } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
