/**
 * @vitest-environment happy-dom
 *
 * G17 leftover: password-only admin login surfaces P2.IAM.MFA_REQUIRED
 * and points at the in-app TOTP enrollment page. Default stub-login
 * (acr=mfa) and desktop PKCE leftover stay untouched.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const { loginWithAccessToken, stubAccessToken } = vi.hoisted(() => ({
  loginWithAccessToken: vi.fn(),
  stubAccessToken: vi.fn(),
}));

vi.mock("../api/client", () => ({
  p2: { authorizeUrl: vi.fn() },
  stubAccessToken,
}));

vi.mock("../api/session", () => ({
  session: {
    accountId: "",
    displayName: "",
    email: "",
    contextId: "",
    demoActorId: "",
    demoReason: "",
    bootstrapToken: "",
  },
  persist: vi.fn(),
  loginWithAccessToken,
  rememberTrace: vi.fn(),
}));

vi.mock("../api/pkce-desktop", () => ({
  DESKTOP_PKCE_TRACE: "trace-g4",
  startDesktopPkceLogin: vi.fn(),
}));

import LoginView from "./LoginView.vue";
import { MFA_REQUIRED_COPY } from "../auth/totpEnrollment";

async function mountLogin() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/login/dev", component: LoginView },
      { path: "/p2/mfa/enroll", component: { template: "<div>enroll</div>" } },
      { path: "/p2/mfa/recovery", component: { template: "<div>recovery</div>" } },
      { path: "/p2/mfa/email", component: { template: "<div>email</div>" } },
    ],
  });
  await router.push("/login/dev");
  await router.isReady();
  return { wrapper: mount(LoginView, { global: { plugins: [router] } }), router };
}

describe("login MFA_REQUIRED enrollment path", () => {
  beforeEach(() => {
    loginWithAccessToken.mockReset();
    stubAccessToken.mockReset();
    stubAccessToken.mockReturnValue("stub.password-only");
  });

  it("keeps default stub-login and pkce-login, and routes MFA_REQUIRED to enroll", async () => {
    const { wrapper, router } = await mountLogin();
    expect(wrapper.get("[data-testid=stub-login]").exists()).toBe(true);
    expect(wrapper.get("[data-testid=pkce-login]").exists()).toBe(true);

    const err = new Error("admin MFA required");
    err.code = "P2.IAM.MFA_REQUIRED";
    loginWithAccessToken.mockRejectedValue(err);

    await wrapper.get("[data-testid=admin-password-login]").trigger("click");
    await flushPromises();
    expect(stubAccessToken).toHaveBeenCalledWith(
      expect.objectContaining({ acr: "", amr: "" }),
    );
    expect(wrapper.get("[data-testid=mfa-required-panel]").text()).toContain(
      MFA_REQUIRED_COPY.title,
    );
    expect(wrapper.get("[data-testid=goto-totp-enroll]").attributes("href")).toContain(
      "/p2/mfa/enroll",
    );
    await wrapper.get("[data-testid=goto-totp-enroll]").trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/p2/mfa/enroll");
    expect(router.currentRoute.value.query.reason).toBe("mfa_required");
  });
});
