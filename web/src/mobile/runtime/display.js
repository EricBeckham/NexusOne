export const SYSTEM_SKIP = new Set([
  "tenant_id",
  "deleted_at",
  "ext_data",
  "schema_version",
  "_source",
]);

export function formatCell(value) {
  if (value == null || value === "") {
    return "—";
  }
  if (typeof value === "object") {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  return String(value);
}

export function toneFor(status) {
  const value = String(status || "").toUpperCase();
  if (value === "APPROVED" || value === "APPROVE" || value === "DONE" || value === "READ" || value === "DELIVERED" || value === "READY" || value === "RUNNING") {
    return "success";
  }
  if (value === "REJECTED" || value === "REJECT" || value === "WITHDRAWN" || value === "FAIL") {
    return "danger";
  }
  if (value === "PENDING" || value === "CREATED" || value === "DRAFT") {
    return "warning";
  }
  return "info";
}

export function recordTitle(row, fields = []) {
  if (!row || typeof row !== "object") {
    return "记录";
  }
  const preferred = ["title", "name", "biz_no", "business_key", "display_name"];
  for (const key of preferred) {
    if (row[key]) {
      return String(row[key]);
    }
  }
  for (const field of fields) {
    const code = field.code || field.field_code;
    if (code && row[code] && !SYSTEM_SKIP.has(code)) {
      return String(row[code]);
    }
  }
  return row.id ? String(row.id) : "记录";
}

export function inputTypeFor(type) {
  const normalized = String(type || "").toLowerCase();
  if (normalized === "number" || normalized === "numeric" || normalized === "integer" || normalized === "money") {
    return "number";
  }
  if (normalized === "date" || normalized === "timestamptz") {
    return "date";
  }
  return "text";
}

export function isPendingTodo(row) {
  const value = String(row?.status || "").toUpperCase();
  return value === "PENDING" || value === "RUNNING" || value === "CREATED";
}
