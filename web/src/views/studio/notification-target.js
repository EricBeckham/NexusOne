const TARGETS = Object.freeze({
  "approval.task.assigned": { to: { path: "/p3/todos", query: { box: "inbox" } }, label: "查看待办" },
  "approval.task.approved": { to: { path: "/p3/todos", query: { box: "initiated" } }, label: "查看我发起" },
  "approval.task.rejected": { to: { path: "/p3/todos", query: { box: "initiated" } }, label: "查看我发起" },
});

export function notificationTarget(row = {}) {
  return TARGETS[row.template_code || row.templateCode] || null;
}
