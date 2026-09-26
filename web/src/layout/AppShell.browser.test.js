/**
 * @vitest-environment happy-dom
 */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import App from "../App.vue";
import { NAV_BY_PRODUCT, collectLinks } from "../nav";
import { PRODUCTS } from "../nav/products";
import AppShell from "./AppShell.vue";
import ProductPickerView from "../views/ProductPickerView.vue";

const baseRoutes = [
  { path: "/login", component: { template: "<p>login-page</p>" }, meta: { public: true, title: "登录" } },
  { path: "/callback", component: { template: "<p>callback-page</p>" }, meta: { public: true, title: "授权码回跳" } },
  { path: "/", component: { template: "<p>picker-page</p>" }, meta: { title: "产品选择" } },
  { path: "/p1", component: { template: "<p>p1-page</p>" }, meta: { title: "星域", product: "p1" } },
  { path: "/p2", component: { template: "<p>p2-page</p>" }, meta: { title: "星权", product: "p2" } },
  { path: "/p3", component: { template: "<p>p3-page</p>" }, meta: { title: "星构", product: "p3" } },
  { path: "/p4", component: { template: "<p>p4-page</p>" }, meta: { title: "星市", product: "p4" } },
  { path: "/p5", component: { template: "<p>p5-page</p>" }, meta: { title: "星联", product: "p5" } },
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
    expect(wrapper.text()).toContain("租户关系");
    expect(wrapper.html()).toContain('href="/p1/relations"');
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

  it("shows SVG product icons on the product picker cards", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/", component: ProductPickerView }, ...PRODUCTS.map((product) => ({ path: product.home, component: { template: "<div />" } }))],
    });
    await router.push("/"); await router.isReady();
    const wrapper = mount(ProductPickerView, { global: { plugins: [router] } });
    expect(wrapper.findAll(".card .mark svg")).toHaveLength(5);
  });

  it("uses recognizable SVG icons for all five product menus and keeps them when collapsed", async () => {
    const router = await boot("/p1");
    const wrapper = mount(AppShell, { global: { plugins: [router] } });
    for (const product of ["p1", "p2", "p3", "p4", "p5"]) {
      await router.push(`/${product}`);
      await wrapper.vm.$nextTick();
      expect(wrapper.attributes("data-product")).toBe(product);
      expect(wrapper.find(".side-head .mark svg").exists()).toBe(true);
      expect(wrapper.findAll(".menu svg.icon").length).toBeGreaterThan(0);
      expect(wrapper.findAll(".switcher .mark svg")).toHaveLength(5);
    }
    await wrapper.get(".fold").trigger("click");
    expect(wrapper.classes()).toContain("collapsed");
    expect(wrapper.find(".menu a[title='首页'] svg.icon").exists()).toBe(true);
  });

  it("uses icons for global search and notifications while retaining environment status", async () => {
    const router = await boot("/p1");
    const wrapper = mount(AppShell, { global: { plugins: [router] } });
    expect(wrapper.find(".search svg").exists()).toBe(true);
    expect(wrapper.find(".notify svg").exists()).toBe(true);
    expect(wrapper.text()).toContain("测试环境");
  });

  it("shows a product › page breadcrumb without repeating the product on its home (O7)", async () => {
    const router = await boot("/p3/ledgers/ledger-1");
    const wrapper = mount(AppShell, { global: { plugins: [router] } });
    const crumb = () => wrapper.get("[data-testid='shell-crumb']");
    expect(crumb().get("a").text()).toBe("星构");
    expect(crumb().get("a").attributes("href")).toBe("/p3");
    expect(crumb().get("[aria-current='page']").text()).toBe("台账详情");

    await router.push("/p4");
    await wrapper.vm.$nextTick();
    expect(crumb().text()).toBe("星市");
    expect(crumb().find("a").exists()).toBe(false);

    await router.push("/");
    await wrapper.vm.$nextTick();
    expect(crumb().text()).toBe("产品选择");
    expect(wrapper.get("[data-testid='shell-brand']").text()).toContain("星枢");
    expect(wrapper.find(".content > .content-inner").exists()).toBe(true);
  });
  it("renders section titles and a compact product row, collapsing titles to dividers (O8)", async () => {
    const router = await boot("/p4");
    const wrapper = mount(AppShell, { global: { plugins: [router] } });
    expect(wrapper.findAll("[data-testid='nav-section']").map((node) => node.text())).toEqual(["目录与开通", "目录配置"]);
    expect(wrapper.find(".later").exists()).toBe(false);
    expect(wrapper.get('a[href="/p4/operations"]').text()).toContain("运营分析");
    expect(wrapper.get(".switcher").text()).toContain("切换产品");
    expect(wrapper.get(".switcher a[aria-current='true']").attributes("data-product")).toBe("p4");
    await wrapper.get(".fold").trigger("click");
    expect(wrapper.findAll("[data-testid='nav-section']").every((node) => node.text() === "")).toBe(true);
    expect(wrapper.get(".switcher").classes()).toContain("collapsed");
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
