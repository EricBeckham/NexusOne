/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { STORAGE_KEY } from "./client";
import { applySessionView, clearIdentity, session, switchTenant } from "./session";

beforeEach(() => {
  sessionStorage.clear();
  clearIdentity();
  vi.unstubAllGlobals();
});

describe("session access token", () => {
  it("stores access_token from a login or PKCE session view", () => {
    applySessionView({
      access_token: "stub.from-login",
      account_id: "acct-1",
      display_name: "Ada",
    });
    expect(session.accessToken).toBe("stub.from-login");
    expect(JSON.parse(sessionStorage.getItem(STORAGE_KEY)).accessToken).toBe("stub.from-login");

    applySessionView({ accessToken: "stub.camel", accountId: "acct-1" });
    expect(session.accessToken).toBe("stub.camel");
  });

  it("sends Authorization Bearer on tenant switch and keeps a readable error without a token", async () => {
    await expect(switchTenant("tenant-1")).rejects.toThrow("没有 access token");

    applySessionView({ access_token: "stub.switch", account_id: "acct-1" });
    const fetchMock = vi.fn(async (_url, init) => ({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ account_id: "acct-1", tenant_id: "tenant-1", context_id: "ctx-2" }),
    }));
    vi.stubGlobal("fetch", fetchMock);
    await switchTenant("tenant-1");
    expect(fetchMock).toHaveBeenCalled();
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer stub.switch");
    expect(String(init.body)).toContain("tenant-1");
    expect(session.accessToken).toBe("stub.switch");
    expect(session.contextId).toBe("ctx-2");
  });
});
