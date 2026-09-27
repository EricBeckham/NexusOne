/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ logout: vi.fn(), employments: vi.fn(), positions: vi.fn(), leaveTo: vi.fn(), replaceWith: vi.fn() }));
vi.mock("../api/client", async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    p2: { ...original.p2, logout: mocks.logout, employments: mocks.employments, positions: mocks.positions },
  };
});
// LO5: the full-page navigation to Keycloak is mocked; the URL checks stay real.
vi.mock("../auth/federatedLogout", async (importOriginal) => {
  const original = await importOriginal();
  return { ...original, leaveTo: mocks.leaveTo, replaceWith: mocks.replaceWith };
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
    mocks.replaceWith.mockReset();
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
    expect(mocks.replaceWith).not.toHaveBeenCalled();
  });

  it("replaces the document with /login when the in-app navigation stays on the page", async () => {
    mocks.logout.mockResolvedValue({});
    const { wrapper, router, replace } = await mountAt();
    replace.mockImplementation(() => Promise.resolve({ type: 16 }));
    await clickLogout(wrapper);
    expect(router.currentRoute.value.path).toBe("/p4");
    expect(wrapper.text()).toContain("未登录");
    expect(mocks.replaceWith).toHaveBeenCalledWith("/login");
  });

  it("replaces the document with /login when the in-app navigation rejects", async () => {
    mocks.logout.mockResolvedValue({});
    const { wrapper, replace } = await mountAt();
    replace.mockRejectedValue(new Error("navigation aborted"));
    await clickLogout(wrapper);
    expect(mocks.replaceWith).toHaveBeenCalledWith("/login");
  });
});

describe("LO5 federated logout", () => {
  const ISSUER = "https://id.example.test/realms/nexus";
  const END_SESSION = `${ISSUER}/protocol/openid-connect/logout?client_id=nexus-web&id_token_hint=h.i.t&post_logout_redirect_uri=https%3A%2F%2Fapp.example.test%2Flogin`;

  beforeEach(() => {
    mocks.logout.mockReset();
    mocks.leaveTo.mockReset();
    mocks.replaceWith.mockReset();
    mocks.employments.mockResolvedValue([]);
    mocks.positions.mockResolvedValue([]);
    localStorage.clear();
    vi.stubEnv("VITE_NEXUS_KEYCLOAK_ISSUER", ISSUER);
    signIn();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  async function clickLogout(wrapper) {
    await wrapper.get("[data-testid='shell-user']").trigger("click");
    await wrapper.findAll("button.link").find((b) => b.text() === "退出").trigger("click");
    await flushPromises();
  }

  function expectSignedOut() {
    for (const key of ["accessToken", "contextId", "tenantId", "accountId", "personId", "email", "displayName"]) {
      expect(session[key]).toBe("");
    }
    expect(loadSettings().accessToken || "").toBe("");
  }

  it("leaves through the Keycloak end_session URL after clearing the local session", async () => {
    mocks.logout.mockResolvedValue({ end_session_url: END_SESSION });
    mocks.leaveTo.mockImplementation(() => {
      // By the time the browser leaves, nothing of the signed-out user is left locally.
      expect(session.accessToken).toBe("");
    });
    const { wrapper, replace } = await mountAt();
    await clickLogout(wrapper);
    expect(mocks.logout).toHaveBeenCalledTimes(1);
    expect(mocks.leaveTo).toHaveBeenCalledTimes(1);
    expect(mocks.leaveTo).toHaveBeenCalledWith(END_SESSION);
    expect(replace).not.toHaveBeenCalled();
    expectSignedOut();
  });

  it("accepts the camelCase field as well", async () => {
    mocks.logout.mockResolvedValue({ endSessionUrl: END_SESSION });
    const { wrapper } = await mountAt();
    await clickLogout(wrapper);
    expect(mocks.leaveTo).toHaveBeenCalledWith(END_SESSION);
  });

  it("falls back to /login when the server returns no URL", async () => {
    mocks.logout.mockResolvedValue({ end_session_url: "" });
    const { wrapper, router, replace } = await mountAt();
    await clickLogout(wrapper);
    expect(mocks.leaveTo).not.toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith("/login");
    expect(router.currentRoute.value.path).toBe("/login");
    expectSignedOut();
  });

  it("still clears the session and goes to /login when the logout API fails", async () => {
    mocks.logout.mockRejectedValue(new Error("network down"));
    const { wrapper, router } = await mountAt();
    await clickLogout(wrapper);
    expect(mocks.leaveTo).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/login");
    expectSignedOut();
  });

  it("does not call the logout API without a token and goes to /login", async () => {
    session.accessToken = "";
    const { wrapper, router } = await mountAt();
    await clickLogout(wrapper);
    expect(mocks.logout).not.toHaveBeenCalled();
    expect(mocks.leaveTo).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe("/login");
  });

  it("refuses a URL carrying token fields or another host and goes to /login instead", async () => {
    for (const bad of [`${END_SESSION}&access_token=x`, END_SESSION.replace("id.example.test", "evil.example.test")]) {
      signIn();
      mocks.logout.mockResolvedValue({ end_session_url: bad });
      const { wrapper, router } = await mountAt();
      await clickLogout(wrapper);
      expect(mocks.leaveTo).not.toHaveBeenCalled();
      expect(router.currentRoute.value.path).toBe("/login");
      expectSignedOut();
    }
  });

  it("does not print the end_session URL to the console", async () => {
    const spies = ["log", "info", "warn", "error", "debug"].map((level) => vi.spyOn(console, level).mockImplementation(() => {}));
    mocks.logout.mockResolvedValue({ end_session_url: END_SESSION });
    const { wrapper } = await mountAt();
    await clickLogout(wrapper);
    for (const spy of spies) {
      expect(spy.mock.calls.flat().join(" ")).not.toContain("id_token_hint");
      spy.mockRestore();
    }
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
describe("DEMO-U6 header tenant labels", () => {
  const ACME = "01900000-0000-7000-8000-000000000101";
  const PLATFORM = "01900000-0000-7000-8000-000000000001";

  beforeEach(() => {
    localStorage.clear();
    signIn();
    mocks.employments.mockResolvedValue([]);
    mocks.positions.mockResolvedValue([]);
    session.tenantId = ACME;
    session.memberships = [
      { tenant_id: ACME, status: "ACTIVE", tenant_code: "demo_acme", tenant_name: "演示租户 · 星河科技" },
      { tenant_id: PLATFORM, status: "ACTIVE", tenant_code: "platform" },
    ];
  });

  it("shows names in the tenant row and the switch options while values stay tenant ids", async () => {
    const { wrapper } = await mountAt();
    await flushPromises();
    await wrapper.get("[data-testid='shell-user']").trigger("click");
    expect(wrapper.get("[data-testid='shell-tenant']").text()).toBe("演示租户 · 星河科技");
    const options = wrapper.findAll("[data-testid='shell-tenant-switch'] option").filter((o) => o.attributes("value"));
    expect(options.map((o) => o.text())).toEqual(["演示租户 · 星河科技", "platform"]);
    expect(options.map((o) => o.attributes("value"))).toEqual([ACME, PLATFORM]);
    expect(wrapper.get("[data-testid='shell-tenant']").text()).not.toContain(ACME);
    expect(options.every((o) => !o.text().includes("01900000"))).toBe(true);
  });
});