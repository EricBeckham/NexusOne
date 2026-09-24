export const p2Nav = [
  { id: "home", label: "首页", to: "/p2" },
  { id: "persons", label: "用户", to: "/p2/persons" },
  { id: "org", label: "组织架构", to: "/p2/org" },
  { id: "positions", label: "岗位任职", to: "/p2/positions" },
  { id: "permissions", label: "权限", to: "/p2/permissions" },
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
