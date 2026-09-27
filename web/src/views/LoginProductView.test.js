/** @vitest-environment happy-dom */
import { mount, flushPromises } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import source from "./LoginProductView.vue?raw";
import { readFileSync } from "node:fs";
vi.mock("../api/session", async () => {
  const { reactive } = await import("vue");
  return { session: reactive({ accountId: "", email: "private@example.test", contextId: "private-context" }) };
});
import { session } from "../api/session";
import LoginProductView from "./LoginProductView.vue";

async function setup(path = "/login") {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: "/login", component: LoginProductView },
    { path: "/login/dev", component: { template: "<div />" } },
    { path: "/register", component: { template: "<div />" } },
    { path: "/", component: { template: "<div />" } },
    { path: "/p2/mfa/enroll", component: { template: "<div />" } },
  ] });
  await router.push(path);
  return { wrapper: mount(LoginProductView, { global: { plugins: [router] } }), router };
}

describe("LOGIN1 product auth shell", () => {
  beforeEach(() => { session.accountId = ""; });
  it("shows the brand without developer controls", async () => {
    const { wrapper } = await setup();
    expect(wrapper.get("h1").text()).toBe("星枢 NexusOne");
    expect(wrapper.text()).toContain("登录进入工作台");
    expect(wrapper.find("textarea").exists()).toBe(false);
    expect(wrapper.text()).not.toMatch(/Stub|Bearer|PKCE|平台操作头|会话与登录/);
    expect(wrapper.get("[data-testid=product-login]").attributes("data-product")).toBe("p2");
  });
  it("offers secondary self registration", async () => {
    const { wrapper, router } = await setup();
    const link = wrapper.get("[data-testid=goto-register] a");
    expect(link.attributes("href")).toBe("/register");
    expect(link.classes()).not.toContain("nx-btn--primary");
    await link.trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/register");
  });
  it("shows a clean signed-in state and workspace entry", async () => {
    session.accountId = "account";
    const { wrapper, router } = await setup();
    expect(wrapper.get("[data-testid=signed-in]").text()).toContain("你已登录");
    expect(wrapper.find("[data-testid=keycloak-login]").exists()).toBe(false);
    expect(wrapper.text()).not.toContain(session.email);
    expect(wrapper.text()).not.toContain(session.contextId);
    await wrapper.get("[data-testid=goto-workspace]").trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/");
  });
  it("shows product MFA guidance and only enrollment as the primary action", async () => {
    session.accountId = "stale-account";
    const { wrapper, router } = await setup("/login?reason=mfa_required");
    expect(wrapper.get("[data-testid=mfa-required-panel]").text()).toContain("多因素验证");
    expect(wrapper.find("[data-testid=goto-workspace]").exists()).toBe(false);
    expect(wrapper.findAll(".nx-btn--primary")).toHaveLength(1);
    expect(wrapper.text()).not.toMatch(/leftover|acr=|Stub|G18/);
    expect(wrapper.find("[data-testid=goto-recovery-codes]").exists()).toBe(false);
    expect(wrapper.find("[data-testid=goto-email-mfa]").exists()).toBe(false);
    await wrapper.get("[data-testid=goto-totp-enroll]").trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.path).toBe("/p2/mfa/enroll");
    expect(router.currentRoute.value.query.reason).toBe("mfa_required");
  });
  it("keeps scoped visual values token-based", () => {
    const tokens = readFileSync("src/styles/tokens.css", "utf8");
    const css = source.split("<style scoped>")[1].split("</style>")[0];
    expect(css).not.toMatch(/#[\da-f]{3,8}\b|\b\d+(?:px|rem|em)\b/i);
    for (const token of css.matchAll(/var\((--nx-[\w-]+)\)/g)) {
      expect(tokens).toContain(`${token[1]}:`);
    }
  });
});
