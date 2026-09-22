/**
 * F010 notify jumps: map in-app notification rows to a safe /m/* path.
 * Never desktop /p3, never tokens / tenant_id, never off-site.
 */
import { DEFAULT_MOBILE_HOME, isSafeMobileReturn, safeMobileReturn } from "../auth/safeReturn.js";

export const TEMPLATE_JUMPS = Object.freeze({
  "approval.task.assigned": "/m/p3/todos?box=inbox",
  "approval.task.approved": "/m/p3/todos?box=initiated",
  "approval.task.rejected": "/m/p3/todos?box=initiated",
});

const SAFE_ID = /^[A-Za-z0-9._-]+$/;

function pick(row, ...names) {
  if (row == null || typeof row !== "object") {
    return undefined;
  }
  for (const name of names) {
    if (row[name] != null && String(row[name]).trim() !== "") {
      return row[name];
    }
  }
  return undefined;
}

function safeId(value) {
  const id = String(value || "").trim();
  return SAFE_ID.test(id) ? id : "";
}

function variablesOf(row) {
  if (!row || typeof row !== "object") {
    return {};
  }
  if (row.variables && typeof row.variables === "object") {
    return row.variables;
  }
  if (row.payload && typeof row.payload === "object" && row.payload.variables) {
    return row.payload.variables;
  }
  return {};
}

export function resolveNotifyJump(row = {}) {
  const explicit = pick(row, "jump", "jump_path", "target_path", "path");
  if (explicit) {
    return isSafeMobileReturn(String(explicit)) ? String(explicit).trim() : DEFAULT_MOBILE_HOME;
  }
  const vars = variablesOf(row);
  const merged = { ...vars, ...row };
  const todoId = safeId(pick(merged, "todo_id", "todoId", "task_id", "taskId"));
  if (todoId) {
    return `/m/p3/todos/${todoId}`;
  }
  const formId = safeId(pick(merged, "form_id", "formId"));
  if (formId) {
    return `/m/p3/forms/${formId}`;
  }
  const ledgerId = safeId(pick(merged, "ledger_id", "ledgerId"));
  const recordId = safeId(pick(merged, "record_id", "recordId"));
  if (ledgerId && recordId) {
    return `/m/p3/lists/${ledgerId}/records/${recordId}`;
  }
  if (ledgerId) {
    return `/m/p3/lists/${ledgerId}`;
  }
  const fileId = safeId(pick(merged, "file_id", "fileId"));
  if (fileId) {
    return `/m/p3/files/${fileId}`;
  }
  const code = String(pick(row, "template_code", "templateCode") || "").toLowerCase();
  if (TEMPLATE_JUMPS[code]) {
    return TEMPLATE_JUMPS[code];
  }
  return DEFAULT_MOBILE_HOME;
}

export function safeNotifyJump(path, fallback = DEFAULT_MOBILE_HOME) {
  return safeMobileReturn(path, fallback);
}
