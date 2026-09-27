/** @vitest-environment happy-dom */
import { mount, flushPromises } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
const { login } = vi.hoisted(() => ({ login: vi.fn() }));
vi.mock("../api/session", () => ({
  session: { accountId: "", demoActorId: "", demoReason: "", bootstrapToken: "" },
  loginWithAccessToken: login, persist: vi.fn(), rememberTrace: vi.fn(),
}));
vi.mock("../api/client", () => ({ p2: {}, stubAccessToken: () => "stub.test-token" }));
import LoginView from "./LoginView.vue";
import LoginProductView from "./LoginProductView.vue";

describe("LOGIN3 development route", () => {
  beforeEach(() => login.mockReset());
  async function setup() {
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: "/login/dev", component: LoginView },
      { path: "/login", component: LoginProductView },
      { path: "/register", component: { template: "<div />" } },
      { path: "/", component: { template: "<div />" } },
    ] });
    await router.push("/login/dev");
    return { router, wrapper: mount(LoginView, { global: { plugins: [router] } }) };
  }
  it("keeps stub login functional", async () => {
    const { wrapper, router } = await setup();
    await wrapper.get("[data-testid=stub-login]").trigger("submit");
    await flushPromises();
    expect(login).toHaveBeenCalledWith("stub.test-token");
    expect(router.currentRoute.value.path).toBe("/");
  });
  it("keeps paste-token login functional", async () => {
    const { wrapper, router } = await setup();
    await wrapper.get("[data-testid=login-token]").setValue("  test.access-token  ");
    await wrapper.get("[data-testid=token-login]").trigger("submit");
    await flushPromises();
    expect(login).toHaveBeenCalledWith("test.access-token");
    expect(router.currentRoute.value.path).toBe("/");
  });
});
