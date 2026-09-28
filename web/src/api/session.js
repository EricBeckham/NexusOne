import { reactive } from "vue";
import { loadSettings, onAccessTokenRenewed, p2, saveSettings } from "./client";

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
  platformMode: false,
  demoActorId: "01900000-0000-7000-8000-0000000000ac",
  demoReason: "m0-12-ui",
  bootstrapToken: "",
  lastTraceId: "",
  loginMethod: "",
  error: "",
  // PV3: product ids from GET /api/v1/auth/products; null = unknown (older server / not loaded) → show all.
  products: null,
});

// PV3: which account + context the product list belongs to; a new context (tenant switch, new login) reloads it.
let productsLoadedFor = null;
let productsInFlight = null;

/**
 * Loads the visible products once per account + issued context. Never throws: on an older server (404) or any
 * failure the list stays null and the whole switcher is shown — the server still refuses what the person may not use.
 */
export async function ensureProducts() {
  if (!session.accessToken) {
    return session.products;
  }
  const key = `${session.accountId || ""}|${session.contextId || ""}`;
  if (productsLoadedFor === key) {
    return session.products;
  }
  if (!productsInFlight) {
    productsInFlight = (async () => {
      let products = null;
      try {
        const body = await p2.visibleProducts();
        products = Array.isArray(body?.products) ? body.products.filter((id) => typeof id === "string") : null;
      } catch {
        products = null;
      }
      session.products = products;
      productsLoadedFor = key;
      return products;
    })().finally(() => {
      productsInFlight = null;
    });
  }
  return productsInFlight;
}

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
    platformMode: stored.platformMode === true,
    demoActorId: stored.demoActorId || session.demoActorId,
    demoReason: stored.demoReason || session.demoReason,
    bootstrapToken: stored.bootstrapToken || "",
    lastTraceId: stored.lastTraceId || "",
    loginMethod: stored.loginMethod || "",
  });
}

hydrate();

// AUTH7R2: keep the in-memory session on the renewed token, so a later persist() never writes the expired one back.
onAccessTokenRenewed((token) => {
  session.accessToken = token;
});

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
  const token = view?.access_token || view?.accessToken;
  if (token) {
    session.accessToken = token;
  }
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
  applyView(view);
  return view;
}

/** AUTH5: the agreed demo business tenant (demo_acme). */
export const DEMO_BUSINESS_TENANT_ID = "01900000-0000-7000-8000-000000000101";

function membershipTenant(item) {
  return item?.tenant_id || item?.tenantId || "";
}

/**
 * AUTH5: which tenant to enter when login returned no context and there are several ACTIVE memberships:
 * demo_acme if it is one of them, otherwise the first ACTIVE one. Fewer than two ACTIVE → null (the server already
 * issues the context for exactly one).
 */
export function pickAutoTenant(memberships) {
  const active = (Array.isArray(memberships) ? memberships : [])
    .filter((item) => String(item?.status || "").toUpperCase() === "ACTIVE" && membershipTenant(item));
  if (active.length < 2) return null;
  const demo = active.find((item) => membershipTenant(item) === DEMO_BUSINESS_TENANT_ID);
  return membershipTenant(demo || active[0]);
}

/**
 * AUTH5: enter a tenant automatically after login when there is no context yet. A failed switch leaves the user
 * signed in without a context (the header still offers the manual switch) and records the reason.
 */
export async function autoSelectTenant() {
  if (session.contextId) return null;
  const tenantId = pickAutoTenant(session.memberships);
  if (!tenantId) return null;
  try {
    await switchTenant(tenantId);
    session.error = "";
    return tenantId;
  } catch (error) {
    session.error = error?.message || "自动进入租户失败，请在右上角手动切换租户。";
    return null;
  }
}

export async function loginWithAccessToken(accessToken) {
  session.accessToken = accessToken;
  persist();
  const view = await p2.login({ access_token: accessToken });
  applyView(view);
  // switchTenant already loads the position for the tenant it enters.
  if (!(await autoSelectTenant())) await loadPrimaryPosition();
  return view;
}

export async function switchTenant(tenantId) {
  if (!session.accessToken) {
    throw new Error("没有 access token，无法切换租户。请重新登录后再试。");
  }
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
  // Platform view is an identity-scoped choice. Never carry a platform operator's
  // cross-tenant mode into the next person who signs in on the same browser.
  session.platformMode = false;
  session.products = null;
  productsLoadedFor = null;
  persist();
}
