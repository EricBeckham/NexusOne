/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ catalog: vi.fn() }));
vi.mock("../../api/client", () => ({ p4: { catalog: mocks.catalog } }));

import MarketHomeView from "./MarketHomeView.vue";

async function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p4", component: MarketHomeView }, { path: "/p4/market", component: { template: "<div />" } }],
  });
  await router.push("/p4");
  await router.isReady();
  const wrapper = mount(MarketHomeView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("W4 Market home", () => {
  beforeEach(() => mocks.catalog.mockReset());

  it("summarizes live published catalog data and links to the existing catalog", async () => {
    mocks.catalog.mockResolvedValue([
      { app: { id: "app-1", name: "CRM", code: "crm" }, version: { id: "v-1", version: "1.0.0" }, skus: [{ code: "basic" }, { code: "pro" }] },
      { app: { id: "app-1", name: "CRM", code: "crm" }, version: { id: "v-2", version: "1.1.0" }, skus: [{ code: "basic" }] },
      { app: { id: "app-2", name: "HR", code: "hr" }, version: { id: "v-3", version: "1.0.0" }, skus: [] },
    ]);
    const wrapper = await mountHome();

    expect(wrapper.get("h1").text()).toBe("星市工作台");
    expect(wrapper.text()).toContain("已上架应用");
    expect(wrapper.text()).toContain("3");
    expect(wrapper.text()).toContain("可选 SKU");
    expect(wrapper.get("a.primary").attributes("href")).toBe("/p4/market");
    expect(wrapper.text()).toContain("CRM");
    expect(wrapper.text()).toContain("HR");
    expect(wrapper.text()).not.toContain("暂无已发布应用");
  });

  it("shows empty state with zero values for an empty catalog", async () => {
    mocks.catalog.mockResolvedValue([]);
    const wrapper = await mountHome();
    expect(wrapper.text()).toContain("暂无已发布应用");
    expect(wrapper.text()).toContain("0");
  });

  it("does not show zero KPI values while the catalog is loading", async () => {
    let resolveCatalog;
    mocks.catalog.mockReturnValue(new Promise((resolve) => {
      resolveCatalog = resolve;
    }));
    const wrapper = await mountHome();

    expect(wrapper.findAll(".value").map((value) => value.text())).toEqual(["暂无", "暂无", "暂无"]);

    resolveCatalog([]);
    await flushPromises();
    expect(wrapper.findAll(".value").map((value) => value.text())).toEqual(["0", "0", "0"]);
  });

  it("shows no placeholder charts or empty stub panels, and offers the tenant path (S8)", async () => {
    mocks.catalog.mockResolvedValue([{ app: { id: "app-1", name: "CRM", code: "crm" }, version: { id: "v-1", version: "1.0.0" }, skus: [] }]);
    const wrapper = await mountHome();
    for (const noise of ["聚合接口", "上架趋势", "目录构成", "运行健康", "待处理", "目录接口"]) {
      expect(wrapper.text()).not.toContain(noise);
    }
    expect(wrapper.find("[data-testid='nx-chart-placeholder']").exists()).toBe(false);
    expect(wrapper.findAll("[data-testid='market-home-start'] a").map((link) => link.attributes("href"))).toEqual([
      "/p4/market",
      "/p4/installations",
      "/p3/todos?box=requests",
    ]);
    expect(wrapper.get("[data-testid='market-home-versions'] [data-testid='nx-status-tag']").text()).toBe("已发布");
  });
});
