/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ logout: vi.fn(), employments: vi.fn(), positions: vi.fn() }));
vi.mock("../api/client", async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    p2: { ...original.p2, logout: mocks.logout, employments: mocks.employments, positions: mocks.positions },
  };
});

import GlobalHeader from "./GlobalHeader.vue";
import { session } from "../api/session";
import { loadSettings } from "../api/client";

async function mountAt(path = "/p4") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4", component: { template: "<div />" }, meta: { product: "p4", title: "首页" } },
      { path: "/login", component: { template: "<div />" }, meta: { public: true } },
      { path: "/p3/notifications", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  const replace = vi.spyOn(router, "replace");
  const push = vi.spyOn(router, "push");
  const wrapper = mount(GlobalHeader, { global: { plugins: [router] } });
  return { wrapper, router, replace, push };
}

function signIn() {
  Object.assign(session, {
    accessToken: "stub-token",
    contextId: "ctx-1",
    tenantId: "01900000-0000-7000-8000-000000000101",
    accountId: "acc-1",
    personId: "p-1",
    email: "lin.xiao@demo.invalid",
    displayName: "林晓",
    memberships: [],
  });
}

describe("AUTH3 logout", () => {
  beforeEach(() => {
    mocks.logout.mockReset();
    mocks.employments.mockResolvedValue([]);
    mocks.positions.mockResolvedValue([]);
    localStorage.clear();
    signIn();
  });

  async function clickLogout(wrapper) {
    await wrapper.get("[data-testid='shell-user']").trigger("click");
    const button = wrapper.findAll("button.link").find((b) => b.text() === "退出");
    await button.trigger("click");
    await flushPromises();
  }

  it("ends the server session, clears the local session and replaces the route with /login", async () => {
    mocks.logout.mockResolvedValue({});
    const { wrapper, router, replace, push } = await mountAt();
    await clickLogout(wrapper);
    expect(mocks.logout).toHaveBeenCalledTimes(1);
    expect(replace).toHaveBeenCalledWith("/login");
    expect(push).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/login");
    for (const key of ["accessToken", "contextId", "tenantId", "accountId", "personId", "email", "displayName"]) {
      expect(session[key]).toBe("");
    }
    expect(session.memberships).toEqual([]);
    expect(loadSettings().accessToken || "").toBe("");
    expect(loadSettings().accountId || "").toBe("");
  });

  it("still clears the session and leaves when the server logout fails", async () => {
    mocks.logout.mockRejectedValue(new Error("network down"));
    const { wrapper, router } = await mountAt();
    await clickLogout(wrapper);
    expect(session.accessToken).toBe("");
    expect(session.displayName).toBe("");
    expect(router.currentRoute.value.path).toBe("/login");
  });

  it("does not call the server when there is no token", async () => {
    session.accessToken = "";
    const { wrapper, router } = await mountAt();
    await clickLogout(wrapper);
    expect(mocks.logout).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/login");
  });
});

describe("AUTH4 header position", () => {
  beforeEach(() => {
    localStorage.clear();
    signIn();
    mocks.positions.mockResolvedValue([{ id: "pos-manager", code: "engineering_manager", name: "研发经理" }]);
  });

  it("shows the primary position name loaded for the tenant context", async () => {
    mocks.employments.mockResolvedValue([{ tenant_id: session.tenantId, position_id: "pos-manager", kind: "PRIMARY", status: "ACTIVE" }]);
    session.positionName = "";
    const { wrapper } = await mountAt();
    await flushPromises();
    await wrapper.get("[data-testid='shell-user']").trigger("click");
    expect(wrapper.get("[data-testid='shell-position']").text()).toBe("研发经理");
  });

  it("shows 暂无 only when there is no primary position", async () => {
    mocks.employments.mockResolvedValue([]);
    session.positionName = "";
    const { wrapper } = await mountAt();
    await flushPromises();
    await wrapper.get("[data-testid='shell-user']").trigger("click");
    expect(wrapper.get("[data-testid='shell-position']").text()).toBe("暂无");
  });
});