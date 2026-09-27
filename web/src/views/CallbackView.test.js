/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const completeDesktopPkceCallback = vi.fn();
const completeProductAuthCodeCallback = vi.fn();
const readDesktopPkcePending = vi.fn();
const sessionMock = vi.hoisted(() => ({ session: { contextId: "" }, autoSelectTenant: vi.fn() }));

vi.mock("../api/client", () => ({ p2: { login: vi.fn() } }));
vi.mock("../api/session", () => ({
  applySessionView: vi.fn(),
  rememberTrace: vi.fn(),
  autoSelectTenant: sessionMock.autoSelectTenant,
  session: sessionMock.session,
}));
vi.mock("../api/pkce-desktop", () => ({
  DESKTOP_PKCE_AFTER: "/p2/permissions/datascope",
  completeDesktopPkceCallback: (...args) => completeDesktopPkceCallback(...args),
  readDesktopPkcePending: (...args) => readDesktopPkcePending(...args),
}));
vi.mock("../auth/productAuthCode", () => ({
  PRODUCT_AUTH_AFTER: "/",
  completeProductAuthCodeCallback: (...args) => completeProductAuthCodeCallback(...args),
}));

import CallbackView from "./CallbackView.vue";

async function mountAt(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/callback", component: CallbackView },
      { path: "/login", component: { template: "<div>login</div>" } },
      { path: "/", component: { template: "<div>home</div>" } },
      { path: "/p2/permissions/datascope", component: { template: "<div>datascope</div>" } },
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
    readDesktopPkcePending.mockReset();
    completeDesktopPkceCallback.mockReset();
    completeProductAuthCodeCallback.mockReset();
    // Default: product path (no leftover PKCE verifier).
    readDesktopPkcePending.mockReturnValue(null);
  });

  it("shows a missing TenantContext shell and does not enter the app", async () => {
    completeProductAuthCodeCallback.mockResolvedValue({
      view: { display_name: "Admin", context_id: "" },
      pending: { trace_id: "trace-1" },
    });
    const { wrapper, router } = await mountAt("/callback?code=auth-code&state=abc");
    expect(wrapper.get("[data-testid=tenant-context-missing]").text()).toContain("缺少 TenantContext");
    expect(wrapper.get("[data-testid=callback-back-login]").attributes("href")).toBe("/login");
    expect(router.currentRoute.value.path).toBe("/callback");
    expect(wrapper.text()).not.toContain("auth-code");
    expect(completeDesktopPkceCallback).not.toHaveBeenCalled();
  });

  it("shows a callback failure category and hides token text from the error", async () => {
    completeProductAuthCodeCallback.mockRejectedValue(
      new Error("state mismatch Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaa.bbb"),
    );
    const { wrapper } = await mountAt("/callback?code=auth-code&state=abc&error_description=access_token%3Dsecret");
    const shell = wrapper.get("[data-testid=callback-failure]");
    expect(shell.text()).toContain("state 不一致");
    expect(shell.text()).toContain("/callback");
    expect(wrapper.text()).not.toMatch(/eyJ|secret|auth-code/);
  });

  it("routes an MFA refusal to the product login without sensitive query data", async () => {
    const error = new Error("sensitive detail must not be forwarded");
    error.code = "P2.IAM.MFA_REQUIRED";
    completeProductAuthCodeCallback.mockRejectedValue(error);
    const { router } = await mountAt("/callback?code=auth-code&state=abc");
    expect(router.currentRoute.value.path).toBe("/login");
    expect(router.currentRoute.value.query).toEqual({ reason: "mfa_required" });
  });

  it("uses the product auth-code path when no desktop PKCE verifier is stored", async () => {
    completeProductAuthCodeCallback.mockResolvedValue({
      view: { display_name: "Demo User", context_id: "ctx-1" },
      pending: { trace_id: "" },
    });
    sessionMock.session.contextId = "ctx-1";
    const { wrapper, router } = await mountAt("/callback?code=auth-code&state=dpo-c1");
    expect(completeProductAuthCodeCallback).toHaveBeenCalledTimes(1);
    expect(completeDesktopPkceCallback).not.toHaveBeenCalled();
    expect(completeProductAuthCodeCallback.mock.calls[0][0].code).toBe("auth-code");
    expect(wrapper.get("[data-testid=callback-lead]").text()).toContain("正在完成登录");
    expect(wrapper.text()).not.toMatch(/leftover|code_verifier|G4/);
    expect(router.currentRoute.value.path).toBe("/");
  });

  it("keeps the desktop PKCE leftover path when a verifier is pending", async () => {
    readDesktopPkcePending.mockReturnValue({ code_verifier: "verifier", state: "abc" });
    completeDesktopPkceCallback.mockResolvedValue({
      view: { display_name: "Dev", context_id: "ctx-dev" },
      pending: { trace_id: "trace-pkce" },
    });
    sessionMock.session.contextId = "ctx-dev";
    const { router } = await mountAt("/callback?code=auth-code&state=abc");
    expect(completeDesktopPkceCallback).toHaveBeenCalledTimes(1);
    expect(completeProductAuthCodeCallback).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/p2/permissions/datascope");
    expect(router.currentRoute.value.query.trace).toBe("trace-pkce");
  });
});

describe("AUTH5 automatic tenant after the Keycloak callback", () => {
  beforeEach(() => {
    sessionMock.session.contextId = "";
    sessionMock.autoSelectTenant.mockReset();
    readDesktopPkcePending.mockReset();
    completeDesktopPkceCallback.mockReset();
    completeProductAuthCodeCallback.mockReset();
    readDesktopPkcePending.mockReturnValue(null);
  });

  it("enters the chosen tenant and continues when several ACTIVE memberships left no context", async () => {
    completeProductAuthCodeCallback.mockResolvedValue({
      view: { display_name: "Lin", context_id: "", memberships: [{}, {}] },
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
    completeProductAuthCodeCallback.mockResolvedValue({
      view: { display_name: "Lin", context_id: "ctx-1" },
      pending: { trace_id: "trace-3" },
    });
    sessionMock.session.contextId = "ctx-1";
    const { router } = await mountAt("/callback?code=auth-code&state=abc");
    expect(sessionMock.autoSelectTenant).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/");
  });
});