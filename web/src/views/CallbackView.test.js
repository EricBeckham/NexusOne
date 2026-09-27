/** @vitest-environment happy-dom */
import { describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const completeDesktopPkceCallback = vi.fn();

vi.mock("../api/client", () => ({ p2: { login: vi.fn() } }));
vi.mock("../api/session", () => ({
  applySessionView: vi.fn(),
  rememberTrace: vi.fn(),
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
