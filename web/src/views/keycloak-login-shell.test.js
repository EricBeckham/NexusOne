/** @vitest-environment happy-dom */
import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

vi.mock("../api/client", () => ({
  p2: { authorizeUrl: vi.fn() },
  stubAccessToken: vi.fn(() => "stub.token"),
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
  loginWithAccessToken: vi.fn(),
  rememberTrace: vi.fn(),
}));
vi.mock("../api/pkce-desktop", () => ({
  DESKTOP_PKCE_TRACE: "trace",
  startDesktopPkceLogin: vi.fn(),
}));

import LoginView from "./LoginProductView.vue";

async function mountLogin() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/login", component: LoginView },
      { path: "/register", component: { template: "<div />" } },
    ],
  });
  await router.push("/login");
  const wrapper = mount(LoginView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("product login shell issuer branches (LOGIN2)", () => {
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); });

  it("shows a friendly unconfigured state without debug fallback", async () => {
    vi.stubEnv("VITE_NEXUS_KEYCLOAK_ISSUER", "");
    const wrapper = await mountLogin();
    expect(wrapper.get("[data-testid='idp-unconfigured']").text()).toContain("登录服务暂未配置");
    expect(wrapper.find("[data-testid='stub-login']").exists()).toBe(false);
    expect(wrapper.find("[data-testid='keycloak-login']").exists()).toBe(false);
    expect(wrapper.find("[data-testid='stub-login-demoted']").exists()).toBe(false);
    expect(wrapper.text()).not.toMatch(/CLIENT_SECRET|REPLACE_FROM_SECRET/);
  });

  it("shows the Keycloak login entry when the public issuer is set", async () => {
    vi.stubEnv("VITE_NEXUS_KEYCLOAK_ISSUER", "https://id.example.test/realms/nexus");
    vi.stubEnv("VITE_NEXUS_KEYCLOAK_CLIENT_ID", "nexus-web");
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, origin: "http://127.0.0.1:4173", assign });
    const wrapper = await mountLogin();
    expect(wrapper.get("[data-testid='keycloak-login']").classes()).toContain("nx-btn--primary");
    expect(wrapper.find("[data-testid='stub-login']").exists()).toBe(false);
    expect(wrapper.findAll("button.nx-btn--primary").map((button) => button.attributes("data-testid"))).toEqual(["keycloak-login"]);
    expect(wrapper.text()).not.toMatch(/CLIENT_SECRET|must-not-leak|REPLACE_FROM_SECRET/);
    await wrapper.get("[data-testid='keycloak-login']").trigger("click");
    expect(assign).toHaveBeenCalled();
    const href = assign.mock.calls[0][0];
    expect(href).toContain("https://id.example.test/realms/nexus/protocol/openid-connect/auth");
    expect(href).toContain("client_id=nexus-web");
    expect(href).not.toMatch(/secret/i);
    vi.unstubAllGlobals();
  });

  it("uses product error copy when navigation cannot start", async () => {
    vi.stubEnv("VITE_NEXUS_KEYCLOAK_ISSUER", "invalid issuer");
    const wrapper = await mountLogin();
    await wrapper.get("[data-testid=keycloak-login]").trigger("click");
    expect(wrapper.get("[data-testid=login-error]").text()).toContain("暂时无法打开登录服务");
    expect(wrapper.text()).not.toContain("invalid issuer");
  });
});
