/**
 * @vitest-environment happy-dom
 *
 * G15 leftover: H5 PKCE hop on /m/login → /m/callback. Desktop P3 is not mounted.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { PENDING_PKCE_KEY } from "../../../auth/pkceSession.js";
import { STORAGE_KEY } from "../../../../api/client.js";
import { session } from "../../../../api/session.js";

const { authorizeUrl, login } = vi.hoisted(() => ({
  authorizeUrl: vi.fn(),
  login: vi.fn(),
}));

vi.mock("../../../../api/client", async () => {
  const actual = await vi.importActual("../../../../api/client");
  return {
    ...actual,
    p2: {
      ...actual.p2,
      authorizeUrl,
      login,
    },
  };
});

import LoginPage from "./LoginPage.vue";
import CallbackPage from "./CallbackPage.vue";
import HomePage from "./HomePage.vue";

function resetSession() {
  sessionStorage.clear();
  session.accessToken = "";
  session.accountId = "";
  session.contextId = "";
  session.tenantId = "";
  session.email = "";
  session.displayName = "";
  session.personId = "";
  session.memberships = [];
  session.loginMethod = "";
  sessionStorage.removeItem(STORAGE_KEY);
}

async function mountLogin(returnTo = "/m/p3/files") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/m/login", component: LoginPage },
      { path: "/m/callback", component: CallbackPage },
      { path: "/m/p3/files", component: { template: "<div data-testid=\"files-landed\">files</div>" } },
      { path: "/m/p3/home", component: HomePage },
    ],
  });
  await router.push({ path: "/m/login", query: { return: returnTo } });
  await router.isReady();
  const wrapper = mount(LoginPage, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("H5 PKCE login hop", () => {
  beforeEach(() => {
    resetSession();
    authorizeUrl.mockReset();
    login.mockReset();
  });

  it("sends a browser-held challenge and posts the verifier on callback", async () => {
    authorizeUrl.mockImplementation(async (params) => ({
      state: "st-h5",
      url: `/m/callback?code=h5-pkce-code&state=st-h5&code_challenge=${params.code_challenge}`,
      code_challenge: params.code_challenge,
      code_challenge_method: "S256",
    }));
    login.mockResolvedValue({
      account_id: "acct-h5",
      email: "h5-pkce@example.com",
      display_name: "H5 PKCE",
      context_id: "ctx-h5",
      tenant_id: "tenant-h5",
    });

    const { wrapper, router } = await mountLogin("/m/p3/files");
    expect(wrapper.get("[data-testid=mobile-pkce-login]").exists()).toBe(true);
    await wrapper.get("[data-testid=mobile-pkce-login]").trigger("click");
    await flushPromises();

    expect(authorizeUrl).toHaveBeenCalledTimes(1);
    const params = authorizeUrl.mock.calls[0][0];
    expect(params.pkce).toBe("s256");
    expect(params.code_challenge).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(router.currentRoute.value.path).toBe("/m/callback");
    expect(router.currentRoute.value.query.code).toBe("h5-pkce-code");
    expect(router.currentRoute.value.query).not.toHaveProperty("code_verifier");
    expect(JSON.parse(sessionStorage.getItem(PENDING_PKCE_KEY)).verifier).toHaveLength(43);

    const callback = mount(CallbackPage, { global: { plugins: [router] } });
    await flushPromises();
    expect(login).toHaveBeenCalledTimes(1);
    const body = login.mock.calls[0][0];
    expect(body.code).toBe("h5-pkce-code");
    expect(body.code_verifier).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(body.redirect_uri).toMatch(/\/m\/callback$/);
    expect(session.loginMethod).toBe("pkce");
    expect(session.contextId).toBe("ctx-h5");
    expect(session.accessToken).toBe("");
    expect(router.currentRoute.value.fullPath).toBe("/m/p3/files");
    expect(sessionStorage.getItem(PENDING_PKCE_KEY)).toBeNull();
    callback.unmount();
    wrapper.unmount();
  });

  it("rejects a callback when state does not match the stored PKCE hop", async () => {
    sessionStorage.setItem(
      PENDING_PKCE_KEY,
      JSON.stringify({
        state: "st-expected",
        verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        challenge: "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
        returnTo: "/m/p3/home",
        savedAt: Date.now(),
      }),
    );
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/m/callback", component: CallbackPage },
        { path: "/m/p3/home", component: HomePage },
      ],
    });
    await router.push({ path: "/m/callback", query: { code: "h5-pkce-code", state: "st-other" } });
    await router.isReady();
    const wrapper = mount(CallbackPage, { global: { plugins: [router] } });
    await flushPromises();
    expect(login).not.toHaveBeenCalled();
    expect(wrapper.get("[data-testid=mobile-callback-error]").text()).toContain("state");
    expect(router.currentRoute.value.path).toBe("/m/callback");
    wrapper.unmount();
  });
});
