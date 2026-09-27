import { reactive } from "vue";
import { loadSettings, p2, saveSettings } from "./client";

export const session = reactive({
  accessToken: "",
  contextId: "",
  tenantId: "",
  accountId: "",
  personId: "",
  email: "",
  displayName: "",
  positionName: "",
  memberships: [],
  platformMode: true,
  demoActorId: "01900000-0000-7000-8000-0000000000ac",
  demoReason: "m0-12-ui",
  bootstrapToken: "",
  lastTraceId: "",
  loginMethod: "",
  error: "",
});

function hydrate() {
  const stored = loadSettings();
  Object.assign(session, {
    accessToken: stored.accessToken || "",
    contextId: stored.contextId || "",
    tenantId: stored.tenantId || "",
    accountId: stored.accountId || "",
    personId: stored.personId || "",
    email: stored.email || "",
    displayName: stored.displayName || "",
    positionName: stored.positionName || "",
    memberships: stored.memberships || [],
    platformMode: stored.platformMode !== false,
    demoActorId: stored.demoActorId || session.demoActorId,
    demoReason: stored.demoReason || session.demoReason,
    bootstrapToken: stored.bootstrapToken || "",
    lastTraceId: stored.lastTraceId || "",
    loginMethod: stored.loginMethod || "",
  });
}

hydrate();

export function persist() {
  saveSettings({
    ...loadSettings(),
    accessToken: session.accessToken,
    contextId: session.contextId,
    tenantId: session.tenantId,
    accountId: session.accountId,
    personId: session.personId,
    email: session.email,
    displayName: session.displayName,
    positionName: session.positionName,
    memberships: session.memberships,
    platformMode: session.platformMode,
    demoActorId: session.demoActorId,
    demoReason: session.demoReason,
    bootstrapToken: session.bootstrapToken,
    lastTraceId: session.lastTraceId,
    loginMethod: session.loginMethod || "",
  });
}

function applyView(view) {
  const previousContext = session.contextId;
  session.accountId = view?.account_id || view?.accountId || "";
  session.email = view?.email || "";
  session.displayName = view?.display_name || view?.displayName || "";
  session.contextId = view?.context_id || view?.contextId || "";
  session.tenantId = view?.tenant_id || view?.tenantId || "";
  session.personId = view?.person_id || view?.personId || "";
  session.memberships = view?.memberships || [];
  // AUTH4: a position belongs to one tenant context; a new context must not show the previous one.
  if (session.contextId !== previousContext) session.positionName = "";
  persist();
}

/**
 * AUTH4: the header shows the name of the PRIMARY, ACTIVE employment in the current tenant. No context, no primary
 * post, or no permission to read it → empty (shown as 暂无). Never guesses from other posts.
 */
export async function loadPrimaryPosition() {
  const personId = session.personId;
  const tenantId = session.tenantId;
  if (!session.accessToken || !session.contextId || !personId) {
    session.positionName = "";
    persist();
    return "";
  }
  let name = "";
  try {
    const [employments, positions] = await Promise.all([p2.employments(personId), p2.positions()]);
    const primary = (Array.isArray(employments) ? employments : []).find((row) =>
      String(row?.kind || "").toUpperCase() === "PRIMARY"
      && String(row?.status || "ACTIVE").toUpperCase() === "ACTIVE"
      && !row?.deleted_at && !row?.deletedAt
      && (!tenantId || !(row?.tenant_id || row?.tenantId) || (row?.tenant_id || row?.tenantId) === tenantId));
    const positionId = primary?.position_id || primary?.positionId;
    const position = positionId ? (Array.isArray(positions) ? positions : []).find((row) => row?.id === positionId) : null;
    name = position?.name || "";
  } catch {
    name = "";
  }
  if (session.personId === personId && session.tenantId === tenantId) {
    session.positionName = name;
    persist();
  }
  return name;
}

export function applySessionView(view) {
  const token = view?.access_token || view?.accessToken;
  if (token) {
    session.accessToken = token;
  }
  applyView(view);
  return view;
}

export async function loginWithAccessToken(accessToken) {
  session.accessToken = accessToken;
  persist();
  const view = await p2.login({ access_token: accessToken });
  applyView(view);
  await loadPrimaryPosition();
  return view;
}

export async function switchTenant(tenantId) {
  const view = await p2.switchTenant(tenantId);
  applyView(view);
  await loadPrimaryPosition();
  return view;
}

export async function refreshSession() {
  if (!session.accessToken) {
    return null;
  }
  const view = await p2.session();
  applyView(view);
  await loadPrimaryPosition();
  return view;
}

export async function ensureContext() {
  if (!session.accessToken) {
    return null;
  }
  return loginWithAccessToken(session.accessToken);
}

export function rememberTrace(traceId) {
  if (traceId) {
    session.lastTraceId = traceId;
    persist();
  }
  return traceId;
}

export function clearIdentity() {
  session.accessToken = "";
  session.contextId = "";
  session.tenantId = "";
  session.accountId = "";
  session.personId = "";
  session.email = "";
  session.displayName = "";
  session.positionName = "";
  session.memberships = [];
  session.lastTraceId = "";
  session.loginMethod = "";
  persist();
}
