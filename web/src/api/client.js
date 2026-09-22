/**
 * ARCH-024: the UI never sends client tenant identity (X-Tenant-Id / tenant_id query).
 * Tenant scope is a server-issued context (X-Nexus-Context-Id) after P2 membership checks.
 */
const FORBIDDEN_TENANT_HEADERS = ["x-tenant-id", "x-nexus-demo-tenant-id"];

export const STORAGE_KEY = "nexus.m012.session";

export function loadSettings() {
  try {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY) || "{}");
  } catch {
    return {};
  }
}

export function saveSettings(next) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
}

export function stubAccessToken({ sub, email, username, name }) {
  const json = `{"sub":"${sub}","email":"${email}","preferred_username":"${username}","name":"${name}"}`;
  const bytes = new TextEncoder().encode(json);
  let bin = "";
  bytes.forEach((b) => {
    bin += String.fromCharCode(b);
  });
  const b64 = btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  return `stub.${b64}`;
}

export async function api(path, { method = "GET", body, headers = {}, auth = true, platform = false } = {}) {
  const settings = loadSettings();
  const nextHeaders = { Accept: "application/json", ...headers };
  for (const key of Object.keys(nextHeaders)) {
    if (FORBIDDEN_TENANT_HEADERS.includes(key.toLowerCase())) {
      throw new Error("ARCH-024: client must not send tenant identity headers");
    }
  }
  if (body !== undefined && !nextHeaders["Content-Type"]) {
    nextHeaders["Content-Type"] = "application/json";
  }
  if (auth && settings.accessToken) {
    nextHeaders.Authorization = `Bearer ${settings.accessToken}`;
  }
  if (auth && settings.contextId) {
    nextHeaders["X-Nexus-Context-Id"] = settings.contextId;
  }
  if (platform || settings.platformMode) {
    nextHeaders["X-Nexus-Demo-Actor-Id"] =
      settings.demoActorId || "01900000-0000-7000-8000-0000000000ac";
    nextHeaders["X-Nexus-Demo-Reason"] = settings.demoReason || "m0-12-ui";
  }
  if (settings.bootstrapToken && path.includes("/internal/")) {
    nextHeaders["X-Nexus-Bootstrap-Token"] = settings.bootstrapToken;
  }
  const response = await fetch(path, {
    method,
    headers: nextHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { raw: text };
    }
  }
  if (!response.ok) {
    const error = new Error(data?.message || `HTTP ${response.status}`);
    error.status = response.status;
    error.body = data;
    error.code = data?.code;
    error.traceId = data?.trace_id || data?.traceId;
    throw error;
  }
  return data;
}

export const p1 = {
  listTenants: () => api("/api/v1/p1/tenants", { platform: true, auth: false }),
  getTenant: (id) => api(`/api/v1/p1/tenants/${id}`, { platform: true, auth: false }),
  platformTenant: () => api("/api/v1/p1/platform-tenant", { platform: true, auth: false }),
  createTenant: (body) => api("/api/v1/p1/tenants", { method: "POST", body, platform: true, auth: false }),
  enable: (id) =>
    api(`/api/v1/p1/tenants/${id}/enable`, { method: "POST", body: { confirm: true }, platform: true, auth: false }),
  disable: (id, reason) =>
    api(`/api/v1/p1/tenants/${id}/disable`, {
      method: "POST",
      body: { confirm: true, reason },
      platform: true,
      auth: false,
    }),
  getConfig: (id) => api(`/api/v1/p1/tenants/${id}/config`, { platform: true, auth: false }),
  putConfig: (id, body) =>
    api(`/api/v1/p1/tenants/${id}/config`, { method: "PUT", body, platform: true, auth: false }),
  entitlement: (id) => api(`/api/v1/p1/tenants/${id}/entitlement`, { platform: true, auth: false }),
  quotas: (id) => api(`/api/v1/p1/tenants/${id}/quotas`, { platform: true, auth: false }),
  consume: (id, body) =>
    api(`/api/v1/p1/tenants/${id}/quotas/consume`, { method: "POST", body, platform: true, auth: false }),
  audit: (id) => api(`/api/v1/p1/tenants/${id}/audit-events?limit=50`, { platform: true, auth: false }),
};

export const p2 = {
  health: () => api("/api/v1/health", { auth: false }),
  authorizeUrl: () => api("/api/v1/auth/authorize-url", { auth: false }),
  login: (body) => api("/api/v1/auth/login", { method: "POST", body, auth: false }),
  session: () => api("/api/v1/auth/session"),
  switchTenant: (tenantId) =>
    api("/api/v1/auth/tenant-switch", { method: "POST", body: { tenant_id: tenantId } }),
  logout: () => api("/api/v1/auth/logout", { method: "POST", body: {} }),
  persons: () => api("/api/v1/persons"),
  getPerson: (id) => api(`/api/v1/persons/${id}`),
  createPerson: (body) => api("/api/v1/persons", { method: "POST", body }),
  disablePerson: (id) => api(`/api/v1/persons/${id}/disable`, { method: "POST" }),
  departments: () => api("/api/v1/departments/tree"),
  createDepartment: (body) => api("/api/v1/departments", { method: "POST", body }),
  positions: () => api("/api/v1/positions"),
  createPosition: (body) => api("/api/v1/positions", { method: "POST", body }),
  employments: (personId) => api(`/api/v1/persons/${personId}/employments`),
  assignEmployment: (body) => api("/api/v1/employments", { method: "POST", body }),
  memberships: () => api("/api/v1/memberships"),
  addMembership: (accountId) => api("/api/v1/memberships", { method: "POST", body: { account_id: accountId } }),
  bootstrapAdmin: (tenantId, body) =>
    api(`/api/v1/internal/tenants/${tenantId}/admin-users`, { method: "POST", body, auth: false }),
};
