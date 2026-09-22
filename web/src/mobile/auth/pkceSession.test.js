import { beforeEach, describe, expect, it } from "vitest";
import { DEFAULT_MOBILE_HOME } from "./safeReturn.js";
import { consumePendingPkce, loadPendingPkce, PENDING_PKCE_KEY, savePendingPkce } from "./pkceSession.js";

describe("H5 PKCE sessionStorage", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it("stores the verifier off the URL and returns it on a matching state", () => {
    savePendingPkce({
      state: "st-1",
      verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
      challenge: "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
      returnTo: "/m/p3/files",
    });
    const stored = loadPendingPkce();
    expect(stored.verifier).toHaveLength(43);
    expect(sessionStorage.getItem(PENDING_PKCE_KEY)).not.toMatch(/\/m\/callback/);
    const consumed = consumePendingPkce("st-1");
    expect(consumed).toEqual({
      ok: true,
      reason: "pkce_matched",
      returnTo: "/m/p3/files",
      verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
      challenge: "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
    });
    expect(loadPendingPkce()).toBeNull();
  });

  it("rejects a state mismatch and drops the verifier", () => {
    savePendingPkce({
      state: "st-1",
      verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
      returnTo: "/m/p3/home",
    });
    const consumed = consumePendingPkce("st-other");
    expect(consumed.ok).toBe(false);
    expect(consumed.reason).toBe("state_mismatch");
    expect(consumed.verifier).toBe("");
    expect(consumed.returnTo).toBe(DEFAULT_MOBILE_HOME);
    expect(loadPendingPkce()).toBeNull();
  });
});
