/** @vitest-environment happy-dom */
import { describe, expect, it, vi } from "vitest";
import { PRODUCT_AUTH_AFTER, completeProductAuthCodeCallback } from "./productAuthCode";

describe("product auth-code callback (LOGIN6)", () => {
  it("posts code + redirect_uri without a PKCE verifier and lands on home", async () => {
    const login = vi.fn().mockResolvedValue({
      account_id: "a1",
      display_name: "Demo User",
      context_id: "ctx-1",
      access_token: "tok",
    });
    const applySession = vi.fn();
    const rememberTrace = vi.fn();
    const result = await completeProductAuthCodeCallback({
      code: "auth-code",
      redirectUri: "https://nexus.haopan.net/callback",
      login,
      applySession,
      rememberTrace,
      traceId: "trace-product",
    });
    expect(PRODUCT_AUTH_AFTER).toBe("/");
    expect(login).toHaveBeenCalledWith(
      {
        code: "auth-code",
        redirect_uri: "https://nexus.haopan.net/callback",
        redirectUri: "https://nexus.haopan.net/callback",
      },
      { traceId: "trace-product" },
    );
    const body = login.mock.calls[0][0];
    expect(body).not.toHaveProperty("code_verifier");
    expect(body).not.toHaveProperty("codeVerifier");
    expect(JSON.stringify(body)).not.toMatch(/secret/i);
    expect(applySession).toHaveBeenCalledWith(result.view);
    expect(rememberTrace).toHaveBeenCalledWith("trace-product");
    expect(result.view.display_name).toBe("Demo User");
  });

  it("rejects a missing authorization code", async () => {
    await expect(
      completeProductAuthCodeCallback({
        code: "",
        redirectUri: "https://nexus.haopan.net/callback",
        login: vi.fn(),
        applySession: vi.fn(),
      }),
    ).rejects.toThrow(/missing authorization code/);
  });
});