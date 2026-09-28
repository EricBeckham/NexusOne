import { api } from "./client";

/** PLC3 menu. Returns `{ items }` once that API is deployed. */
export function portalMenu() {
  return api("/api/v1/p3/portal/menu");
}

/** PLC3 home modules. Returns `{ modules }`. */
export function portalModules() {
  return api("/api/v1/p3/portal/home-modules");
}

/** PLC3 replace the tenant menu. Body is `{ items }`. */
export function replacePortalMenu(body) {
  return api("/api/v1/p3/portal/menu", { method: "PUT", body });
}

/** PLC3 replace home modules. Body is `{ modules }`. */
export function replacePortalModules(body) {
  return api("/api/v1/p3/portal/home-modules", { method: "PUT", body });
}

/**
 * PLA6 / C5. Filters the candidate keys down to the ones this member may read.
 * The response is `{ entries: string[] }`.
 */
export async function readableEntries(keys) {
  const list = Array.isArray(keys) ? keys : [];
  const query = list.map((key) => `keys=${encodeURIComponent(key)}`).join("&");
  const path = query
    ? `/api/v1/permission/entries/readable?${query}`
    : "/api/v1/permission/entries/readable";
  const body = await api(path);
  return Array.isArray(body?.entries) ? body.entries : [];
}

/**
 * Stand-in for {@code AppInstanceQuery.current()} until B registers that bean.
 * Platform approval does not emit an event, so a later wiring replaces this
 * with the reconciled instance list. Until then the portal has no instance entries.
 */
export function currentInstances() {
  return [];
}

/** PLC8 drifted overrides for the current tenant. */
export function baselineDrift() {
  return api("/api/v1/p3/instance-overrides/drift");
}

/** PLC8 rebase one override onto the current platform version. {@code choices} maps a path to TENANT or PLATFORM. */
export function mergeBaseline(instanceId, objectType, objectCode, choices) {
  return api(`/api/v1/p3/instances/${encodeURIComponent(instanceId)}/overrides/${encodeURIComponent(objectType)}/${encodeURIComponent(objectCode)}/merge`, {
    method: "POST",
    body: { choices },
  });
}
