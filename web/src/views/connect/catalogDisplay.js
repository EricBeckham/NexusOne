export function catalogEntry(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) {
    return null;
  }
  const id = row.id == null || row.id === "" ? "" : String(row.id);
  if (!id) {
    return null;
  }
  const tenant = row.tenant_id ?? row.tenantId;
  const method = String(row.http_method ?? row.httpMethod ?? "").trim().toUpperCase();
  return {
    id,
    shared: tenant == null || tenant === "",
    name: row.name == null ? "" : String(row.name),
    httpMethod: method,
    path: row.path == null ? "" : String(row.path),
    version: row.version == null ? "" : String(row.version),
    scope: row.scope == null ? "" : String(row.scope),
    status: row.status == null ? "" : String(row.status),
    description: row.description == null ? "" : String(row.description),
    updatedAt: row.updated_at ?? row.updatedAt ?? "",
    createdAt: row.created_at ?? row.createdAt ?? "",
  };
}

export function catalogRows(body) {
  if (!Array.isArray(body)) {
    return null;
  }
  return body.map(catalogEntry).filter(Boolean);
}

export function formatTime(value) {
  if (value == null || value === "") {
    return "暂无";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "暂无" : date.toLocaleString("zh-CN", { hour12: false });
}

export const CATALOG_TABS = ["overview", "request", "response", "scope"];
