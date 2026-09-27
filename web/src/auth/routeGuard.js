import { isSafeMobileReturn, safeMobileReturn } from "../mobile/auth/safeReturn";

/**
 * AUTH3: one place that decides whether a route may open.
 * - public and harness routes always open;
 * - H5 (`meta.mobile`) keeps its own rule and login page (/m/login);
 * - every other desktop route needs a real access token. An accountId / displayName / contextId left in storage
 *   without a token is NOT a signed-in user.
 */
export function hasAccessToken(session) {
  return typeof session?.accessToken === "string" && session.accessToken.trim() !== "";
}

export function routeGuard(to, session) {
  if (to.meta?.harness || to.meta?.public) {
    return true;
  }
  if (to.meta?.mobile) {
    if (session.accessToken || session.accountId || session.contextId) {
      return true;
    }
    const returnTo = isSafeMobileReturn(to.fullPath) ? to.fullPath : safeMobileReturn(to.path);
    return { path: "/m/login", query: { return: returnTo } };
  }
  return hasAccessToken(session) ? true : { path: "/login" };
}
