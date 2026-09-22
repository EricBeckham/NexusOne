/**
 * Same Decision rule as Java SurfaceGuard: !allowed is deny.
 * Maps no_grant / explicit_deny / tenant_boundary to desktop P2.PERM codes.
 */
export { decide, denyError, formatDeny, requireAllowed, DENY_CODES, ENGINE, denyCode } from "./reportGuard.js";
