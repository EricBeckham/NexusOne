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

export function stubAccessToken({ sub, email, username, name, acr = "mfa", amr = "otp" }) {
  // Fabricates acr=mfa so the closed G18 stub-IdP admin walk still issues a
  // privileged context. This is not a TOTP / SMS / hardware challenge.
  const strength =
    acr || amr
      ? `${acr ? `,"acr":"${acr}"` : ""}${amr ? `,"amr":"${amr}"` : ""}`
      : "";
  const json = `{"sub":"${sub}","email":"${email}","preferred_username":"${username}","name":"${name}"${strength}}`;
  const bytes = new TextEncoder().encode(json);
  let bin = "";
  bytes.forEach((b) => {
    bin += String.fromCharCode(b);
  });
  const b64 = btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  return `stub.${b64}`;
}

export function newTraceId() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return "0199a1c0-0000-7000-8000-00000000a000";
}

export async function api(path, { method = "GET", body, headers = {}, auth = true, platform = false, traceId } = {}) {
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
  if (traceId) {
    nextHeaders["X-Trace-Id"] = traceId;
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

/** Binary download helper (IM4 export). Shares auth/platform headers with `api`. */
export async function apiBlob(path, { method = "GET", body, headers = {}, auth = true, platform = false, traceId } = {}) {
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
  if (traceId) {
    nextHeaders["X-Trace-Id"] = traceId;
  }
  if (platform || settings.platformMode) {
    nextHeaders["X-Nexus-Demo-Actor-Id"] =
      settings.demoActorId || "01900000-0000-7000-8000-0000000000ac";
    nextHeaders["X-Nexus-Demo-Reason"] = settings.demoReason || "m0-12-ui";
  }
  const response = await fetch(path, {
    method,
    headers: nextHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const text = await response.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        data = { raw: text };
      }
    }
    const error = new Error(data?.message || data?.raw || `HTTP ${response.status}`);
    error.status = response.status;
    error.body = data;
    error.code = data?.code;
    error.traceId = data?.trace_id || data?.traceId || response.headers.get("X-Trace-Id") || "";
    throw error;
  }
  const disposition = response.headers.get("Content-Disposition") || "";
  const match = /filename\*?=(?:UTF-8''|")?([^\";]+)/i.exec(disposition);
  const filename = match ? decodeURIComponent(match[1].replace(/"/g, "").trim()) : "";
  const blob = await response.blob();
  return { blob, filename, contentType: response.headers.get("Content-Type") || "" };
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
  serviceStatus: () => api("/api/v1/p1/platform/services", { platform: true, auth: false }),
  actuatorHealth: () => api("/actuator/health", { auth: false }),
  tenantTree: () => api("/api/v1/p1/tenants/tree", { platform: true, auth: false }),
  tenantChildren: (id) => api(`/api/v1/p1/tenants/${encodeURIComponent(id)}/children`, { platform: true, auth: false }),
  tenantAncestors: (id) => api(`/api/v1/p1/tenants/${encodeURIComponent(id)}/ancestors`, { platform: true, auth: false }),
  reparentTenant: (id, parentTenantId) => api(`/api/v1/p1/tenants/${encodeURIComponent(id)}/parent`, {
    method: "PATCH",
    body: { parent_tenant_id: parentTenantId },
    platform: true,
    auth: false,
  }),
  consume: (id, body) =>
    api(`/api/v1/p1/tenants/${id}/quotas/consume`, { method: "POST", body, platform: true, auth: false }),
  audit: (id) => api(`/api/v1/p1/tenants/${id}/audit-events?limit=50`, { platform: true, auth: false }),
  formTemplates: () => api("/api/v1/p1/platform/form-templates", { platform: true, auth: false }),
  formTemplate: (id) => api(`/api/v1/p1/platform/form-templates/${encodeURIComponent(id)}`, { platform: true, auth: false }),
  createFormTemplate: (body) => api("/api/v1/p1/platform/form-templates", { method: "POST", body, platform: true, auth: false }),
  updateFormTemplate: (id, body) => api(`/api/v1/p1/platform/form-templates/${encodeURIComponent(id)}`, { method: "PUT", body, platform: true, auth: false }),
  validateFormTemplate: (id) => api(`/api/v1/p1/platform/form-templates/${encodeURIComponent(id)}/validate`, { method: "POST", body: {}, platform: true, auth: false }),
  publishFormTemplate: (id) => api(`/api/v1/p1/platform/form-templates/${encodeURIComponent(id)}/publish`, { method: "POST", body: {}, platform: true, auth: false }),
  applyTenantRegistration: (body) => api("/api/v1/p1/tenant-registrations", { method: "POST", body, auth: false }),
  listTenantRegistrations: (status) => {
    const q = status ? `?status=${encodeURIComponent(status)}` : "";
    return api(`/api/v1/p1/tenant-registrations${q}`, { platform: true, auth: false });
  },
  getTenantRegistration: (id) => api(`/api/v1/p1/tenant-registrations/${encodeURIComponent(id)}`, { platform: true, auth: false }),
  approveTenantRegistration: (id, note) => api(`/api/v1/p1/tenant-registrations/${encodeURIComponent(id)}/approve`, { method: "POST", body: { note }, platform: true, auth: false }),
  rejectTenantRegistration: (id, note) => api(`/api/v1/p1/tenant-registrations/${encodeURIComponent(id)}/reject`, { method: "POST", body: { note }, platform: true, auth: false }),
};

export const p2 = {
  health: () => api("/api/v1/health", { auth: false }),
  authorizeUrl: (opts = {}) => {
    const query = new URLSearchParams();
    if (opts.pkce) {
      query.set("pkce", opts.pkce);
    }
    if (opts.acr) {
      query.set("acr", opts.acr);
    }
    if (opts.code_challenge) {
      query.set("code_challenge", opts.code_challenge);
    }
    const suffix = query.toString() ? `?${query}` : "";
    return api(`/api/v1/auth/authorize-url${suffix}`, { auth: false, traceId: opts.traceId });
  },
  login: (body, opts = {}) =>
    api("/api/v1/auth/login", { method: "POST", body, auth: false, traceId: opts.traceId }),
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
  identityProviders: () => api("/api/v1/identity/providers"),
  createIdentityProvider: (body) => api("/api/v1/identity/providers", { method: "POST", body }),
  updateIdentityProvider: (id, body) => api(`/api/v1/identity/providers/${encodeURIComponent(id)}`, { method: "PUT", body }),
  deleteIdentityProvider: (id) => api(`/api/v1/identity/providers/${encodeURIComponent(id)}`, { method: "DELETE" }),
  identityProviderSecretStatus: (id) => api(`/api/v1/identity/providers/${encodeURIComponent(id)}/secret`),
  writeIdentityProviderSecret: (id, clientSecret) => api(`/api/v1/identity/providers/${encodeURIComponent(id)}/secret`, { method: "PUT", body: { clientSecret } }),
  rotateIdentityProviderSecret: (id, clientSecret) => api(`/api/v1/identity/providers/${encodeURIComponent(id)}/secret/rotate`, { method: "POST", body: { clientSecret } }),
  testIdentityProviderConnection: (id) => api(`/api/v1/identity/providers/${encodeURIComponent(id)}/test-connection`, { method: "POST" }),
  syncIdentityProvider: (id) => api(`/api/v1/identity/providers/${encodeURIComponent(id)}/sync`, { method: "POST" }),
  bootstrapAdmin: (tenantId, body) =>
    api(`/api/v1/internal/tenants/${tenantId}/admin-users`, { method: "POST", body, auth: false }),
};

export const p4 = {
  catalog: () => api("/api/v1/p4/catalog", { auth: false }),
  apps: () => api("/api/v1/p4/apps", { auth: false }),
  app: (appId) => api(`/api/v1/p4/apps/${encodeURIComponent(appId)}`, { auth: false }),
  versions: (appId) => api(`/api/v1/p4/apps/${encodeURIComponent(appId)}/versions`, { auth: false }),
  version: (versionId) => api(`/api/v1/p4/versions/${encodeURIComponent(versionId)}`, { auth: false }),
  skus: () => api("/api/v1/p4/skus", { auth: false }),
  sku: (code) => api(`/api/v1/p4/skus/${encodeURIComponent(code)}`, { auth: false }),
  subscriptions: () => api("/api/v1/p4/subscriptions"),
  subscription: (id) => api(`/api/v1/p4/subscriptions/${encodeURIComponent(id)}`),
  commercialEntitlements: () => api("/api/v1/p4/commercial-entitlements"),
  commercialEntitlement: (id) => api(`/api/v1/p4/commercial-entitlements/${encodeURIComponent(id)}`),
  installations: () => api("/api/v1/p4/installations"),
  installation: (id) => api(`/api/v1/p4/installations/${encodeURIComponent(id)}`),
  // XB6 / XB7: honest upgrade step state and facts (UNKNOWN / null where nothing records or declares them).
  upgradeSteps: (id) => api(`/api/v1/p4/installations/${encodeURIComponent(id)}/upgrade-steps`),
  upgradeFacts: (id) => api(`/api/v1/p4/installations/${encodeURIComponent(id)}/upgrade-facts`),
  createApp: (body) => api("/api/v1/p4/apps", { method: "POST", body, platform: true, auth: false }),
  createVersion: (appId, body) => api(`/api/v1/p4/apps/${appId}/versions`, { method: "POST", body, platform: true, auth: false }),
  transition: (versionId, action) => api(`/api/v1/p4/versions/${versionId}/${action}`, { method: "POST", body: {}, platform: true, auth: false }),
  createSku: (body) => api("/api/v1/p4/skus", { method: "POST", body, platform: true, auth: false }),
  productDefinitions: () => api("/api/v1/p4/product-definitions", { platform: true, auth: false }),
  // PC6: wizard readiness for one product definition (DONE / NOT_READY / UNKNOWN per step, from stored data).
  productConfigReadiness: (id) => api(`/api/v1/p4/product-definitions/${encodeURIComponent(id)}/config-readiness`, { platform: true, auth: false }),
  // PC9: sample ledger for the current product (columns from its published form; rows always empty).
  productLedgerSample: (id) => api(`/api/v1/p4/product-definitions/${encodeURIComponent(id)}/samples/ledger`, { platform: true, auth: false }),
  // PC11: built-in print layout filled with the current product's form fields (values always blank).
  productPrintSample: (id) => api(`/api/v1/p4/product-definitions/${encodeURIComponent(id)}/samples/print`, { platform: true, auth: false }),
  // PC13: current counts for the product (null = could not count); trend_available is always false.
  productReportSample: (id) => api(`/api/v1/p4/product-definitions/${encodeURIComponent(id)}/samples/report`, { platform: true, auth: false }),
  // IM4: download product-config format 1.0 JSON bundle for one definition.
  exportProductDefinition: (id) => apiBlob(`/api/v1/p4/product-definitions/${encodeURIComponent(id)}/export`, { platform: true, auth: false }),
  // IM5: import package → importJobId (ConflictStrategy skip|new-version|fail; Idempotency-Key).
  importProductDefinition: (packageBody, conflictStrategy, idempotencyKey) => api("/api/v1/p4/product-definitions/import", {
    method: "POST",
    body: { package_body: packageBody, conflict_strategy: conflictStrategy },
    headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {},
    platform: true,
    auth: false,
  }),
  // IM6: read import job status / results.
  productDefinitionImportJob: (id) => api(`/api/v1/p4/product-definitions/import-jobs/${encodeURIComponent(id)}`, { platform: true, auth: false }),
  // CF-R3: static print field→slot contract and the per-product mapping (process-local override, lost on restart).
  printMappingContract: () => api("/api/v1/p4/print-mapping/contract", { platform: true, auth: false }),
  productPrintMapping: (id) => api(`/api/v1/p4/product-definitions/${encodeURIComponent(id)}/print-mapping`, { platform: true, auth: false }),
  saveProductPrintMapping: (id, body) => api(`/api/v1/p4/product-definitions/${encodeURIComponent(id)}/print-mapping`, { method: "PUT", body, platform: true, auth: false }),
  createProductDefinition: (body) => api("/api/v1/p4/product-definitions", { method: "POST", body, platform: true, auth: false }),
  updateProductDefinition: (id, body) => api(`/api/v1/p4/product-definitions/${encodeURIComponent(id)}`, { method: "PUT", body, platform: true, auth: false }),
  bindProductDefinitionSku: (id, skuCode) => api(`/api/v1/p4/product-definitions/${encodeURIComponent(id)}/skus/${encodeURIComponent(skuCode)}`, { method: "PUT", platform: true, auth: false }),
  install: (tenantId, body) => api(`/api/v1/p4/tenants/${tenantId}/installations`, { method: "POST", body }),
  subscribe: (tenantId, body) => api(`/api/v1/p4/tenants/${encodeURIComponent(tenantId)}/subscriptions`, { method: "POST", body }),
  requestActivation: (tenantId, body) => api(`/api/v1/p4/tenants/${encodeURIComponent(tenantId)}/activation-applications`, { method: "POST", body }),
  activationApplications: (mine = false) => api(`/api/v1/p4/activation-applications${mine ? "?mine=true" : ""}`, mine ? {} : { platform: true, auth: false }),
  activationApplication: (id) => api(`/api/v1/p4/activation-applications/${encodeURIComponent(id)}`, { platform: true, auth: false }),
  approveActivation: (id, note) => api(`/api/v1/p4/activation-applications/${encodeURIComponent(id)}/approve`, { method: "POST", body: { note }, platform: true, auth: false }),
  rejectActivation: (id, note) => api(`/api/v1/p4/activation-applications/${encodeURIComponent(id)}/reject`, { method: "POST", body: { note }, platform: true, auth: false }),
  upgrade: (installationId, versionId) => api(`/api/v1/p4/installations/${installationId}/upgrade`, { method: "POST", body: { versionId } }),
  uninstall: (installationId) => api(`/api/v1/p4/installations/${installationId}/uninstall`, { method: "POST", body: {} }),
};

/** P5 thin self-use integration surface; no external developer portal or OAuth ecosystem. */
export const p5 = {
  issueClient: (body) => api("/api/v1/p5/clients", { method: "POST", body, platform: true }),
  clients: () => api("/api/v1/p5/clients"),
  client: (id) => api(`/api/v1/p5/clients/${encodeURIComponent(id)}`),
  createWebhook: (body) => api("/api/v1/p5/webhooks", { method: "POST", body, platform: true }),
  webhooks: () => api("/api/v1/p5/webhooks"),
  webhook: (id) => api(`/api/v1/p5/webhooks/${encodeURIComponent(id)}`),
  webhookDeliveries: (id) => api(`/api/v1/p5/webhooks/${encodeURIComponent(id)}/deliveries`),
  connectors: () => api("/api/v1/p5/connectors"),
  runConnector: (code) => api(`/api/v1/p5/connectors/${encodeURIComponent(code)}/run`, { method: "POST", body: {} }),
  connectorRuns: () => api("/api/v1/p5/connector-runs"),
  callLogs: () => api("/api/v1/p5/call-logs"),
  apiCatalog: () => api("/api/v1/p5/api-catalog"),
  apiCatalogEntry: (id) => api(`/api/v1/p5/api-catalog/${encodeURIComponent(id)}`),
  credentials: () => api("/api/v1/p5/credentials"),
  credential: (id) => api(`/api/v1/p5/credentials/${encodeURIComponent(id)}`),
  createCredential: (body) => api("/api/v1/p5/credentials", { method: "POST", body }),
  updateCredential: (id, body) => api(`/api/v1/p5/credentials/${encodeURIComponent(id)}`, { method: "PATCH", body }),
  credentialSecretStatus: (id) => api(`/api/v1/p5/credentials/${encodeURIComponent(id)}/secret`),
  writeCredentialSecret: (id, secret) => api(`/api/v1/p5/credentials/${encodeURIComponent(id)}/secret`, { method: "PUT", body: { secret } }),
  rotateCredentialSecret: (id, secret) => api(`/api/v1/p5/credentials/${encodeURIComponent(id)}/secret/rotate`, { method: "POST", body: { secret } }),
  revokeCredentialSecret: (id) => api(`/api/v1/p5/credentials/${encodeURIComponent(id)}/secret`, { method: "DELETE" }),
  probeCredentialSecret: (id) => api(`/api/v1/p5/credentials/${encodeURIComponent(id)}/secret/probe`, { method: "POST" }),
};

/** M0-05 / G2 permission engine. Role matrix lives here; no /api/v1/p2/roles. */
export const perm = {
  health: () => api("/api/v1/permission/health", { auth: false }),
  snapshot: () => api("/api/v1/permission/snapshot"),
  check: (body) => api("/api/v1/permission/check", { method: "POST", body }),
  matrix: () => api("/api/v1/permission/matrix"),
  roles: () => api("/api/v1/permission/roles"),
  assignments: () => api("/api/v1/permission/assignments"),
  grants: () => api("/api/v1/permission/grants"),
  installExample: () => api("/api/v1/permission/matrix/example", { method: "POST", body: {} }),
  createRole: (body) => api("/api/v1/permission/roles", { method: "POST", body }),
  assign: (body) => api("/api/v1/permission/assignments", { method: "POST", body }),
  grant: (body) =>
    api("/api/v1/permission/grants", { method: "POST", body, traceId: loadSettings().lastTraceId }),
};

/** M0-10 in-app notify. List is empty without a server-issued TenantContext (403). */
export const notify = {
  list: () => api("/api/v1/notifications"),
  enqueue: (body) => api("/api/v1/notifications", { method: "POST", body }),
  read: (id) => api(`/api/v1/notifications/${id}/read`, { method: "POST", body: {} }),
};

/** M0-11 hash chain, read-only by trace. Tenant-filtered. Never X-Tenant-Id. */
export const audit = {
  byTrace: (traceId) => api(`/api/v1/audit/traces/${encodeURIComponent(traceId)}`),
  platformEvents: (filters = {}) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== null && value !== "") query.set(key, String(value));
    }
    const suffix = query.toString() ? `?${query}` : "";
    return api(`/api/v1/audit/events${suffix}`, { platform: true, auth: false });
  },
  verifyChain: () => api("/api/v1/audit/chain/verify", { method: "POST", platform: true, auth: false }),
};
