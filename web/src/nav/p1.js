export const p1Nav = [
  { id: "home", label: "首页", to: "/p1" },
  { id: "tenants", label: "租户列表", to: "/p1/tenants" },
  { id: "tenant-create", label: "新建租户", to: "/p1/tenants/new" },
  { id: "tenant-detail", label: "租户详情", detailBase: "/p1/tenants" },
  { id: "relations", label: "租户关系", to: "/p1/relations" },
  { id: "quotas", label: "权益与配额", to: "/p1/quotas", detailSuffix: "/entitlement" },
  { id: "service", label: "平台服务态", to: "/p1/services" },
  { id: "audit", label: "平台审计", to: "/p1/audit" },
];
