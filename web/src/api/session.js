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
  memberships: [],
  platformMode: true,
  demoActorId: "01900000-0000-7000-8000-0000000000ac",
  demoReason: "m0-12-ui",
  bootstrapToken: "",
  lastTraceId: "",
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
    memberships: stored.memberships || [],
    platformMode: stored.platformMode !== false,
    demoActorId: stored.demoActorId || session.demoActorId,
    demoReason: stored.demoReason || session.demoReason,
    bootstrapToken: stored.bootstrapToken || "",
    lastTraceId: stored.lastTraceId || "",
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
    memberships: session.memberships,
    platformMode: session.platformMode,
    demoActorId: session.demoActorId,
    demoReason: session.demoReason,
    bootstrapToken: session.bootstrapToken,
    lastTraceId: session.lastTraceId,
  });
}

function applyView(view) {
  session.accountId = view?.account_id || view?.accountId || "";
  session.email = view?.email || "";
  session.displayName = view?.display_name || view?.displayName || "";
  session.contextId = view?.context_id || view?.contextId || "";
  session.tenantId = view?.tenant_id || view?.tenantId || "";
  session.personId = view?.person_id || view?.personId || "";
  session.memberships = view?.memberships || [];
  persist();
}

export async function loginWithAccessToken(accessToken) {
  session.accessToken = accessToken;
  persist();
  const view = await p2.login({ access_token: accessToken });
  applyView(view);
  return view;
}

export async function switchTenant(tenantId) {
  const view = await p2.switchTenant(tenantId);
  applyView(view);
  return view;
}

export async function refreshSession() {
  if (!session.accessToken) {
    return null;
  }
  const view = await p2.session();
  applyView(view);
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
  session.memberships = [];
  session.lastTraceId = "";
  persist();
}
