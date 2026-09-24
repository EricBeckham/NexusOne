export const p2Nav = [
  { id: "home", label: "首页", to: "/p2" },
  { id: "persons", label: "用户目录", to: "/p2/persons" },
  { id: "org", label: "组织架构", to: "/p2/org" },
  { id: "positions", label: "岗位任职", to: "/p2/positions" },
  {
    id: "permissions",
    label: "权限",
    children: [
      { id: "roles", label: "角色授权", to: "/p2/permissions/roles" },
      { id: "datascope", label: "数据范围", to: "/p2/permissions/datascope" },
      { id: "fields", label: "字段权限", to: "/p2/permissions/fields" },
    ],
  },
  {
    id: "security",
    label: "身份安全",
    children: [
      { id: "totp", label: "TOTP 注册", to: "/p2/mfa/enroll" },
      { id: "recovery", label: "恢复码", to: "/p2/mfa/recovery" },
      { id: "email", label: "邮箱 MFA", to: "/p2/mfa/email" },
    ],
  },
];
