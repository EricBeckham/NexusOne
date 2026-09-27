/**
 * @vitest-environment happy-dom
 *
 * G4 leftover: desktop PKCE login click + callback posts code_verifier.
 * Stubs authorize-url / login (no JAR, no Docker). Not a product G4 close.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import {
  DESKTOP_PKCE_STORAGE,
  DESKTOP_PKCE_TRACE,
  clearDesktopPkcePending,
  completeDesktopPkceCallback,
  startDesktopPkceLogin,
} from "../api/pkce-desktop";

const { authorizeUrl, login } = vi.hoisted(() => ({
  authorizeUrl: vi.fn(),
  login: vi.fn(),
}));

vi.mock("../api/client", () => ({
  p2: { authorizeUrl, login },
  stubAccessToken: () => "stub.token",
}));

vi.mock("../api/session", () => ({
  session: { accountId: "", displayName: "", email: "", contextId: "", demoActorId: "", demoReason: "", bootstrapToken: "" },
  persist: vi.fn(),
  loginWithAccessToken: vi.fn(),
  applySessionView: vi.fn(),
  rememberTrace: vi.fn(),
}));

import LoginView from "./LoginView.vue";
import { applySessionView, rememberTrace } from "../api/session";

async function mountLogin() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/login/dev", component: LoginView },
      { path: "/login", component: { template: "<div>product login</div>" } },
      { path: "/register", component: { template: "<div>register</div>" } },
    ],
  });
  await router.push("/login/dev");
  await router.isReady();
  return mount(LoginView, { global: { plugins: [router] } });
}

describe("desktop PKCE leftover", () => {
  beforeEach(() => {
    authorizeUrl.mockReset();
    login.mockReset();
    applySessionView.mockReset();
    rememberTrace.mockReset();
    sessionStorage.clear();
    clearDesktopPkcePending();
  });

  it("startDesktopPkceLogin stores verifier and assigns authorize URL", async () => {
    const assignLocation = vi.fn();
    authorizeUrl.mockResolvedValue({
      url: "http://127.0.0.1:18089/protocol/openid-connect/auth?code_challenge=abc&code_challenge_method=S256&state=st-1",
      state: "st-1",
      code_verifier: "a".repeat(43),
      code_challenge: "abc",
      code_challenge_method: "S256",
    });
    const pending = await startDesktopPkceLogin({
      authorizeUrl,
      assignLocation,
      origin: "http://127.0.0.1:4174",
    });
    expect(authorizeUrl).toHaveBeenCalledWith({ pkce: "s256", traceId: DESKTOP_PKCE_TRACE });
    expect(pending.code_verifier).toHaveLength(43);
    expect(JSON.parse(sessionStorage.getItem(DESKTOP_PKCE_STORAGE)).redirect_uri).toBe(
      "http://127.0.0.1:4174/callback",
    );
    expect(assignLocation).toHaveBeenCalledTimes(1);
    expect(assignLocation.mock.calls[0][0]).toContain("code_challenge=");
  });

  it("clicks data-testid=pkce-login on the desktop login card", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, origin: "http://127.0.0.1:4174", assign });
    authorizeUrl.mockResolvedValue({
      url: "http://idp.test/auth?code_challenge=xyz&code_challenge_method=S256&state=st-2",
      state: "st-2",
      code_verifier: "b".repeat(43),
    });
    const wrapper = await mountLogin();
    const button = wrapper.get('[data-testid="pkce-login"]');
    expect(button.text()).toMatch(/PKCE/);
    await button.trigger("click");
    await flushPromises();
    expect(authorizeUrl).toHaveBeenCalledWith({ pkce: "s256", traceId: DESKTOP_PKCE_TRACE });
    expect(assign).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it("completeDesktopPkceCallback posts code + verifier then applies session", async () => {
    sessionStorage.setItem(
      DESKTOP_PKCE_STORAGE,
      JSON.stringify({
        code_verifier: "c".repeat(43),
        state: "st-3",
        trace_id: DESKTOP_PKCE_TRACE,
        redirect_uri: "http://127.0.0.1:4174/callback",
      }),
    );
    login.mockResolvedValue({
      account_id: "acct-1",
      display_name: "G4 PKCE",
      context_id: "ctx-1",
      access_token: "stub.pkce",
    });
    const result = await completeDesktopPkceCallback({
      code: "auth-code-1",
      state: "st-3",
      login,
      applySession: applySessionView,
      rememberTrace,
    });
    expect(login).toHaveBeenCalledWith(
      {
        code: "auth-code-1",
        redirect_uri: "http://127.0.0.1:4174/callback",
        code_verifier: "c".repeat(43),
        codeVerifier: "c".repeat(43),
      },
      { traceId: DESKTOP_PKCE_TRACE },
    );
    expect(applySessionView).toHaveBeenCalledTimes(1);
    expect(rememberTrace).toHaveBeenCalledWith(DESKTOP_PKCE_TRACE);
    expect(result.pending.trace_id).toBe(DESKTOP_PKCE_TRACE);
    expect(sessionStorage.getItem(DESKTOP_PKCE_STORAGE)).toBeNull();
  });
});
