/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { STORAGE_KEY, api, apiBlob, loadSettings, onAccessTokenRenewed, saveSettings } from "./client";

const json = (status, body) => new Response(JSON.stringify(body), { status });

describe("AUTH7R2 silent access-token renewal", () => {
  let fetchMock;
  let calls;

  beforeEach(() => {
    saveSettings({ accessToken: "old-token", contextId: "ctx-1" });
    calls = [];
    fetchMock = vi.fn(async (url, init) => {
      calls.push({ url, auth: init.headers.Authorization, context: init.headers["X-Nexus-Context-Id"] });
      if (url === "/api/v1/auth/refresh") return json(200, { access_token: "new-token" });
      return init.headers.Authorization === "Bearer new-token"
        ? json(200, { ok: true })
        : json(401, { code: "AUTH6.PLATFORM_READ_DENIED", reason: "session_invalid" });
    });
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    sessionStorage.removeItem(STORAGE_KEY);
  });

  it("renews once on a 401 and replays the call with the new token and the same context", async () => {
    await expect(api("/api/v1/p1/tenants")).resolves.toEqual({ ok: true });
    expect(calls.map((c) => c.url)).toEqual(["/api/v1/p1/tenants", "/api/v1/auth/refresh", "/api/v1/p1/tenants"]);
    expect(calls[1].auth).toBe("Bearer old-token");
    expect(calls[2].auth).toBe("Bearer new-token");
    expect(calls[2].context).toBe("ctx-1");
    expect(loadSettings().accessToken).toBe("new-token");
    expect(loadSettings().contextId).toBe("ctx-1");
  });

  it("shares one renewal between concurrent 401s", async () => {
    await Promise.all([api("/api/v1/p1/tenants"), api("/api/v1/p1/tenants/tree"), api("/api/v1/p1/platform/services")]);
    expect(calls.filter((c) => c.url === "/api/v1/auth/refresh")).toHaveLength(1);
  });

  it("tells the session store about the renewed token", async () => {
    const seen = [];
    const off = onAccessTokenRenewed((token) => seen.push(token));
    await api("/api/v1/p1/tenants");
    off();
    expect(seen).toEqual(["new-token"]);
  });

  it("returns the original 401 when renewal is refused, and retries only once", async () => {
    fetchMock.mockImplementation(async (url, init) => {
      calls.push({ url, auth: init.headers.Authorization });
      if (url === "/api/v1/auth/refresh") return json(401, { code: "P2.IAM.REFRESH_FAILED" });
      return json(401, { code: "AUTH6.PLATFORM_READ_DENIED", reason: "session_invalid" });
    });
    const error = await api("/api/v1/p1/tenants").catch((err) => err);
    expect(error.status).toBe(401);
    expect(error.body.reason).toBe("session_invalid");
    expect(calls.map((c) => c.url)).toEqual(["/api/v1/p1/tenants", "/api/v1/auth/refresh"]);
    expect(loadSettings().accessToken).toBe("old-token");
  });

  it("does not loop when the replayed call is still 401", async () => {
    fetchMock.mockImplementation(async (url, init) => {
      calls.push({ url, auth: init.headers.Authorization });
      if (url === "/api/v1/auth/refresh") return json(200, { access_token: "new-token" });
      return json(401, { code: "P2.IAM.UNAUTHORIZED" });
    });
    await expect(api("/api/v1/p1/tenants")).rejects.toMatchObject({ status: 401 });
    expect(calls.map((c) => c.url)).toEqual(["/api/v1/p1/tenants", "/api/v1/auth/refresh", "/api/v1/p1/tenants"]);
  });

  it("never renews for 403, for unauthenticated calls, without a token, or for the auth endpoints", async () => {
    fetchMock.mockImplementation(async (url, init) => {
      calls.push({ url, auth: init.headers.Authorization });
      return json(url.includes("forbidden") ? 403 : 401, { code: "X" });
    });
    await api("/api/v1/forbidden").catch(() => {});
    await api("/api/v1/p1/getTenant", { auth: false }).catch(() => {});
    await api("/api/v1/auth/logout", { method: "POST", body: {} }).catch(() => {});
    saveSettings({ contextId: "ctx-1" });
    await api("/api/v1/p1/tenants").catch(() => {});
    expect(calls.some((c) => c.url === "/api/v1/auth/refresh")).toBe(false);
  });

  it("does not resurrect a token when the user signed out during renewal", async () => {
    fetchMock.mockImplementation(async (url, init) => {
      calls.push({ url, auth: init.headers.Authorization });
      if (url === "/api/v1/auth/refresh") {
        saveSettings({}); // logout cleared the session meanwhile
        return json(200, { access_token: "new-token" });
      }
      return json(401, { code: "X" });
    });
    await api("/api/v1/p1/tenants").catch(() => {});
    expect(loadSettings().accessToken).toBeUndefined();
    expect(calls.filter((c) => c.url === "/api/v1/p1/tenants")).toHaveLength(1);
  });

  it("renews downloads too", async () => {
    fetchMock.mockImplementation(async (url, init) => {
      calls.push({ url, auth: init.headers.Authorization });
      if (url === "/api/v1/auth/refresh") return json(200, { access_token: "new-token" });
      if (init.headers.Authorization === "Bearer new-token") {
        return new Response("a,b", { status: 200, headers: { "Content-Disposition": "attachment; filename=\"x.csv\"" } });
      }
      return json(401, { code: "X" });
    });
    const file = await apiBlob("/api/v1/p3/exports/1/download");
    expect(file.filename).toBe("x.csv");
    expect(calls.map((c) => c.url)).toEqual([
      "/api/v1/p3/exports/1/download", "/api/v1/auth/refresh", "/api/v1/p3/exports/1/download"]);
  });
});
