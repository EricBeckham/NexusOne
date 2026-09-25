/**
 * One vocabulary and colour per status across 我的待办 / 已办 / 我发起 / 我的申请 and the decision notice (N2).
 * Codes come from the todo list (prefer instance_status / APPROVED after N5 mapTodo), process instances and product applications.
 */
const STATUS = {
  PENDING: { label: "待处理", tone: "warning" },
  CREATED: { label: "待处理", tone: "warning" },
  RUNNING: { label: "进行中", tone: "info" },
  IN_APPROVAL: { label: "审批中", tone: "info" },
  APPROVE: { label: "已同意", tone: "success" },
  APPROVED: { label: "已通过", tone: "success" },
  REJECT: { label: "已驳回", tone: "danger" },
  REJECTED: { label: "已驳回", tone: "danger" },
  WITHDRAWN: { label: "已撤回", tone: "neutral" },
  DONE: { label: "已办结", tone: "neutral" },
};

/** @param {string} status  @param {string} fallback label used when there is no status at all */
export function todoStatus(status, fallback = "暂无") {
  const code = String(status || "").trim().toUpperCase();
  if (!code) return { label: fallback, tone: fallback === "进行中" ? "info" : "neutral" };
  return STATUS[code] || { label: String(status), tone: "neutral" };
}
