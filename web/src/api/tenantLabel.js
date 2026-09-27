/**
 * DEMO-U6: how a tenant is shown to people. Name first, then code; never the bare tenant UUID (the UUID is only the
 * value used for switching). Without either label the text says so instead of printing the id.
 */
export const UNNAMED_TENANT = "未命名租户";

export function tenantIdOf(membership) {
  return membership?.tenant_id || membership?.tenantId || "";
}

export function tenantLabel(membership) {
  const name = String(membership?.tenant_name || membership?.tenantName || "").trim();
  if (name) return name;
  const code = String(membership?.tenant_code || membership?.tenantCode || "").trim();
  if (code) return code;
  return UNNAMED_TENANT;
}

export function currentTenantLabel(session) {
  if (!session?.tenantId) return "暂无";
  const current = (Array.isArray(session.memberships) ? session.memberships : [])
    .find((item) => tenantIdOf(item) === session.tenantId);
  return tenantLabel(current);
}
