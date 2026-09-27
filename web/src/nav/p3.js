export const p3Nav = [
  { id: "home", label: "首页", to: "/p3" },
  { id: "todos", label: "待办", to: "/p3/todos", section: "工作台" },
  { id: "forms", label: "表单", to: "/p3/forms", section: "工作台" },
  { id: "ledgers", label: "台账", to: "/p3/ledgers", section: "工作台" },
  { id: "notifications", label: "站内通知", to: "/p3/notifications", section: "工作台" },
  {
    // FA5: one group keeps the sidebar within eleven top items.
    id: "process-config",
    label: "流程与自动化",
    section: "流程配置",
    children: [
      { id: "workflow-designer", label: "流程设计器", to: "/p3/workflow" },
      { id: "publish-center", label: "发布中心", to: "/p3/publish-center" },
      { id: "completion-actions", label: "办结后动作", to: "/p3/completion-actions" },
    ],
  },
  { id: "reports", label: "报表", to: "/p3/reports", section: "数据与审计" },
  { id: "exports", label: "导出向导", to: "/p3/exports/wizard", section: "数据与审计" },
  { id: "dumps", label: "物理导出", to: "/p3/dumps", section: "数据与审计" },
  { id: "audit", label: "审计哈希链", to: "/p3/audit", section: "数据与审计" },
];
