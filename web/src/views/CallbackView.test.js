/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const completeDesktopPkceCallback = vi.fn();
const sessionMock = vi.hoisted(() => ({ session: { contextId: "" }, autoSelectTenant: vi.fn() }));

vi.mock("../api/client", () => ({ p2: { login: vi.fn() } }));
vi.mock("../api/session", () => ({
  applySessionView: vi.fn(),
  rememberTrace: vi.fn(),
  autoSelectTenant: sessionMock.autoSelectTenant,
  session: sessionMock.session,
}));
vi.mock("../api/pkce-desktop", () => ({
  DESKTOP_PKCE_AFTER: "/",
  completeDesktopPkceCallback: (...args) => completeDesktopPkceCallback(...args),
}));

import CallbackView from "./CallbackView.vue";

async function mountAt(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/callback", component: CallbackView },
      { path: "/login", component: { template: "<div>login</div>" } },
      { path: "/", component: { template: "<div>home</div>" } },
    ],
  });
  await router.push(path);
  const wrapper = mount(CallbackView, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("callback tenant context shell", () => {
  beforeEach(() => {
    sessionMock.session.contextId = "";
    sessionMock.autoSelectTenant.mockReset();
    sessionMock.autoSelectTenant.mockResolvedValue(null);
  });

  it("shows a missing TenantContext shell and does not enter the app", async () => {
    completeDesktopPkceCallback.mockResolvedValue({
      view: { display_name: "Admin", context_id: "" },
      pending: { trace_id: "trace-1" },
    });
    const { wrapper, router } = await mountAt("/callback?code=auth-code&state=abc");
    expect(wrapper.get("[data-testid=tenant-context-missing]").text()).toContain("缺少 TenantContext");
    expect(wrapper.get("[data-testid=callback-back-login]").attributes("href")).toBe("/login");
    expect(router.currentRoute.value.path).toBe("/callback");
    expect(wrapper.text()).not.toContain("auth-code");
  });

  it("shows a callback failure category and hides token text from the error", async () => {
    completeDesktopPkceCallback.mockRejectedValue(new Error("state mismatch Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaa.bbb"));
    const { wrapper } = await mountAt("/callback?code=auth-code&state=abc&error_description=access_token%3Dsecret");
    const shell = wrapper.get("[data-testid=callback-failure]");
    expect(shell.text()).toContain("state 不一致");
    expect(shell.text()).toContain("/callback");
    expect(wrapper.text()).not.toMatch(/eyJ|secret|auth-code/);
  });
});

describe("AUTH5 automatic tenant after the Keycloak callback", () => {
  beforeEach(() => {
    sessionMock.session.contextId = "";
    sessionMock.autoSelectTenant.mockReset();
  });

  it("enters the chosen tenant and continues when several ACTIVE memberships left no context", async () => {
    completeDesktopPkceCallback.mockResolvedValue({
      view: { display_name: "林晓", context_id: "", memberships: [{}, {}] },
      pending: { trace_id: "trace-2" },
    });
    sessionMock.autoSelectTenant.mockImplementation(async () => {
      sessionMock.session.contextId = "ctx-demo";
      return "01900000-0000-7000-8000-000000000101";
    });
    const { router } = await mountAt("/callback?code=auth-code&state=abc");
    expect(sessionMock.autoSelectTenant).toHaveBeenCalledTimes(1);
    expect(router.currentRoute.value.path).toBe("/");
  });

  it("does not switch when the login already has a context", async () => {
    completeDesktopPkceCallback.mockResolvedValue({
      view: { display_name: "林晓", context_id: "ctx-1" },
      pending: { trace_id: "trace-3" },
    });
    sessionMock.session.contextId = "ctx-1";
    const { router } = await mountAt("/callback?code=auth-code&state=abc");
    expect(sessionMock.autoSelectTenant).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/");
  });
});