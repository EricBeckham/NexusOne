/**
 * Same Decision rule as Java SurfaceGuard: !allowed is deny.
 * Maps no_grant / explicit_deny / tenant_boundary to desktop P2.PERM codes.
 */
import { DENY_CODES, ENGINE, denyCode } from "./reportCatalog.js";

export function requireAllowed(decision) {
  if (!decision || decision.allowed !== true) {
    const reason = decision?.reason || "no_grant";
    throw denyError(reason, decision);
  }
  return {
    ...decision,
    engine: decision.engine || ENGINE,
  };
}

export function denyError(reason, decision) {
  const code = denyCode(reason);
  const error = new Error(reason);
  error.status = 403;
  error.code = code;
  error.body = {
    allowed: false,
    reason,
    engine: decision?.engine || ENGINE,
    policy_version: decision?.policy_version ?? decision?.policyVersion ?? 0,
    fields: decision?.fields || {},
    code,
  };
  return error;
}

export async function decide(check, resource, action) {
  const decision = await check({ resource, action });
  const allowed = requireAllowed(decision);
  return { ...allowed, resource, action, engine: allowed.engine || ENGINE };
}

export function formatDeny(err) {
  const code = err?.code || err?.body?.code || "";
  const message = err?.message || err?.body?.reason || err?.body?.message || "";
  return `${code} ${message}`.trim();
}

export { DENY_CODES, ENGINE, denyCode };
