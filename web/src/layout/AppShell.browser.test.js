/**
 * @vitest-environment happy-dom
 */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import App from "../App.vue";
import { NAV_BY_PRODUCT, collectLinks } from "../nav";
import AppShell from "./AppShell.vue";

const baseRoutes = [
  { path: "/login", component: { template: "<p>login-page</p>" }, meta: { public: true, title: "登录" } },
  { path: "/callback", component: { template: "<p>callback-page</p>" }, meta: { public: true, title: "授权码回跳" } },
  { path: "/", component: { template: "<p>picker-page</p>" }, meta: { title: "产品选择" } },
  { path: "/p1", component: { template: "<p>p1-page</p>" }, meta: { title: "星域", product: "p1" } },
  { path: "/p4", component: { template: "<p>p4-page</p>" }, meta: { title: "星市", product: "p4" } },
  { path: "/p3/ledgers/:id", component: { template: "<p>ledger-page</p>" }, meta: { title: "台账详情", product: "p3" } },
  { path: "/p3/notifications", component: { template: "<p>notify-page</p>" }, meta: { title: "站内通知", product: "p3" } },
];
const menuPaths = [...new Set(Object.values(NAV_BY_PRODUCT).flatMap((items) => collectLinks(items)))];
const routes = [
  ...baseRoutes,
  ...menuPaths
    .filter((path) => !baseRoutes.some((route) => route.path === path))
    .map((path) => ({ path, component: { template: "<p>menu-page</p>" } })),
];

async function boot(path) {
  const router = createRouter({ history: createMemoryHistory(), routes });
  router.push(path);
  await router.isReady();
  return router;
}

describe("W0 product shell", () => {
  it("registers a route for every sidebar link", async () => {
    const router = await boot("/");
    for (const path of menuPaths) {
      expect(router.resolve(path).matched, path).not.toHaveLength(0);
    }
  });

  it("switches product color, sidebar, and home", async () => {
    const router = await boot("/p1");
    const wrapper = mount(AppShell, { global: { plugins: [router] } });
    expect(wrapper.attributes("data-product")).toBe("p1");
    expect(wrapper.text()).toContain("星域");
    expect(wrapper.text()).toContain("Nexus Tenant");
    expect(wrapper.text()).toContain("租户列表");
    expect(wrapper.text()).toContain("后续");
    expect(wrapper.text()).not.toContain("用户");
    expect(wrapper.text()).not.toMatch(new RegExp(["M0-12", "壳"].join("\\s+")));

    await router.push("/p4");
    await wrapper.vm.$nextTick();
    expect(wrapper.attributes("data-product")).toBe("p4");
    expect(wrapper.text()).toContain("星市");
    expect(wrapper.text()).toContain("Nexus Market");
    expect(wrapper.text()).toContain("应用目录");
    expect(wrapper.text()).not.toContain("租户列表");
  });

  it("keeps a deep link inside the product frame", async () => {
    const router = await boot("/p3/ledgers/ledger-1");
    const wrapper = mount(AppShell, { global: { plugins: [router] } });
    expect(wrapper.text()).toContain("ledger-page");
    expect(wrapper.text()).toContain("星构");
    expect(wrapper.text()).toContain("审计哈希链");
    const current = wrapper.findAll(".menu a.current").map((item) => item.attributes("title"));
    expect(current).toEqual(["台账"]);
  });

  it("does not wrap login or callback in the product frame", async () => {
    const loginRouter = await boot("/login");
    const login = mount(App, { global: { plugins: [loginRouter] } });
    expect(login.text()).toContain("login-page");
    expect(login.text()).not.toContain("Nexus Tenant");

    const callbackRouter = await boot("/callback");
    const callback = mount(App, { global: { plugins: [callbackRouter] } });
    expect(callback.text()).toContain("callback-page");
    expect(callback.text()).not.toContain("选择产品");
  });
});
