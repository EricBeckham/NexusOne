export const p1Nav = [
  { id: "home", label: "首页", to: "/p1" },
  { id: "tenants", section: "租户", label: "租户列表", to: "/p1/tenants" },
  { id: "tenant-create", section: "租户", label: "新建租户", to: "/p1/tenants/new" },
  { id: "tenant-registrations", section: "租户", label: "注册审订", to: "/p1/tenant-registrations" },
  { id: "tenant-detail", section: "租户", label: "租户详情", detailBase: "/p1/tenants" },
  { id: "relations", section: "租户", label: "租户关系", to: "/p1/relations" },
  { id: "quotas", section: "租户", label: "权益与配额", to: "/p1/quotas", detailSuffix: "/entitlement" },
  { id: "service", section: "平台", label: "平台服务态", to: "/p1/services" },
  { id: "audit", section: "平台", label: "平台审计", to: "/p1/audit" },
  { id: "form-templates", section: "平台", label: "表单模板", to: "/p1/form-templates" },
];
