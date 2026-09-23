// @vitest-environment happy-dom
import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it } from "vitest";
import HomeView from "./HomeView.vue";
import ProductShellView from "./ProductShellView.vue";

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: HomeView, meta: { title: "选择产品" } },
      { path: "/p1", component: ProductShellView, meta: { title: "星域", product: "p1" } },
      { path: "/p2", component: ProductShellView, meta: { title: "星权", product: "p2" } },
      { path: "/p3", component: ProductShellView, meta: { title: "星构", product: "p3" } },
    ],
  });
}

describe("T1-L3 post-login product shells", () => {
  it("shows three reachable product entries on the post-login landing page", async () => {
    const router = makeRouter();
    await router.push("/");
    await router.isReady();
    const wrapper = mount(HomeView, { global: { plugins: [router] } });

    expect(wrapper.get("[data-testid=p1-entry]").attributes("href")).toBe("/p1");
    expect(wrapper.get("[data-testid=p2-entry]").attributes("href")).toBe("/p2");
    expect(wrapper.get("[data-testid=p3-entry]").attributes("href")).toBe("/p3");
  });

  it.each([
    ["p1", "星域"],
    ["p2", "星权"],
    ["p3", "星构"],
  ])("opens the %s empty shell", async (id, name) => {
    const router = makeRouter();
    await router.push(`/${id}`);
    await router.isReady();
    const wrapper = mount(ProductShellView, { global: { plugins: [router] } });

    expect(wrapper.get(`[data-testid=${id}-product-shell] h1`).text()).toBe(name);
    expect(wrapper.text()).toContain("产品壳已接通");
  });
});
