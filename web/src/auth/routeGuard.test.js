import { describe, expect, it } from "vitest";
import { hasAccessToken, routeGuard } from "./routeGuard";

const desktop = (path = "/p4/catalog") => ({ path, fullPath: path, meta: { title: "x", product: "p4" } });
const mobile = (path = "/m/p3/home") => ({ path, fullPath: path, meta: { mobile: true } });
const empty = { accessToken: "", accountId: "", contextId: "", displayName: "" };

describe("AUTH3 route guard", () => {
  it("sends a desktop route without an access token to /login", () => {
    expect(routeGuard(desktop(), empty)).toEqual({ path: "/login" });
    expect(routeGuard(desktop("/"), empty)).toEqual({ path: "/login" });
  });

  it("does not treat a leftover accountId / displayName / contextId as signed in on desktop", () => {
    expect(routeGuard(desktop(), { ...empty, accountId: "a1", displayName: "林晓", contextId: "c1" })).toEqual({ path: "/login" });
    expect(routeGuard(desktop(), { ...empty, accessToken: "   " })).toEqual({ path: "/login" });
  });

  it("lets a desktop route open with an access token", () => {
    expect(routeGuard(desktop(), { ...empty, accessToken: "token" })).toBe(true);
    expect(hasAccessToken({ accessToken: "token" })).toBe(true);
    expect(hasAccessToken({})).toBe(false);
  });

  it("always opens public and harness routes", () => {
    expect(routeGuard({ path: "/login", fullPath: "/login", meta: { public: true } }, empty)).toBe(true);
    expect(routeGuard({ path: "/callback", fullPath: "/callback", meta: { public: true } }, empty)).toBe(true);
    expect(routeGuard({ path: "/m/host-harness", fullPath: "/m/host-harness", meta: { harness: true, public: true } }, empty)).toBe(true);
  });

  it("keeps the H5 rule and its own /m/login page", () => {
    const redirect = routeGuard(mobile("/m/p3/todos"), empty);
    expect(redirect.path).toBe("/m/login");
    expect(redirect.query.return).toBe("/m/p3/todos");
    expect(routeGuard(mobile(), { ...empty, accessToken: "token" })).toBe(true);
    expect(routeGuard({ path: "/m/login", fullPath: "/m/login", meta: { mobile: true, public: true } }, empty)).toBe(true);
  });
});
