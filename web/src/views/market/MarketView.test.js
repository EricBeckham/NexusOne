/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ catalog: vi.fn(), subscribe: vi.fn() }));
vi.mock("../../api/client", () => ({ p4: mocks }));
vi.mock("../../api/session", () => ({ session: { tenantId: "tenant-1" } }));

import MarketView from "./MarketView.vue";

async function mountCatalog() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/market", component: MarketView },
      { path: "/p4/installations", component: { template: "<div>opened</div>" } },
    ],
  });
  await router.push("/p4/market");
  const wrapper = mount(MarketView, { global: { plugins: [router] } });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

describe("P4 catalog V11", () => {
  beforeEach(() => {
    mocks.catalog.mockReset(); mocks.subscribe.mockReset();
    mocks.catalog.mockResolvedValue([
      { app: { id: "app-1", name: "星辰 CRM", code: "crm", summary: "客户关系管理" }, version: { id: "v1", version: "1.2.0", status: "PUBLISHED" }, skus: [{ code: "basic", name: "基础版" }] },
      { app: { id: "app-2", name: "财务平台", code: "finance", summary: "账务与报表" }, version: { id: "v2", version: "2.0.0", status: "PUBLISHED" }, skus: [] },
    ]);
  });

  it("renders published catalog cards and filters without category data", async () => {
    const wrapper = await mountCatalog();
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("星辰 CRM");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("已发布");
    expect(wrapper.get(".app-logo").attributes("aria-label")).toBe("应用标识占位");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("暂无 SKU");
    expect(wrapper.text()).not.toContain("分类");
    await wrapper.get('[data-testid="catalog-search"]').setValue("BASIC");
    await flushPromises();
    expect(wrapper.get('[data-testid="catalog-filter"]').classes()).not.toContain("nx-card");
    expect(wrapper.router.currentRoute.value.query.q).toBe("BASIC");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("星辰 CRM");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).not.toContain("财务平台");
    expect(wrapper.text()).toContain("1 个可用版本");
    await wrapper.get('[data-testid="catalog-reset"]').trigger("click");
    await flushPromises();
    expect(wrapper.router.currentRoute.value.query.q).toBeUndefined();
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("财务平台");
  });

  it("subscribes without payment and opens 我的已开通", async () => {
    mocks.subscribe.mockResolvedValue({ id: "install-1" });
    const wrapper = await mountCatalog();
    expect(wrapper.text()).toContain("无支付");
    expect(wrapper.get(".install-button").text()).toContain("订阅开通");
    await wrapper.get(".install-button").trigger("click");
    await flushPromises();
    expect(mocks.subscribe).toHaveBeenCalledWith("tenant-1", { skuCode: "basic", versionId: "v1" });
    expect(wrapper.router.currentRoute.value.path).toBe("/p4/installations");
  });

  it("shows empty and the server failure text", async () => {
    mocks.catalog.mockResolvedValueOnce([]);
    const empty = await mountCatalog();
    expect(empty.text()).toContain("暂无");
    empty.unmount();

    mocks.catalog.mockResolvedValueOnce([
      { app: { id: "app-1", name: "星辰 CRM", code: "crm", summary: "客户关系管理" }, version: { id: "v1", version: "1.2.0", status: "PUBLISHED" }, skus: [{ code: "basic", name: "基础版" }] },
    ]);
    mocks.subscribe.mockRejectedValueOnce(new Error("tenant already has an active subscription for this SKU"));
    const failed = await mountCatalog();
    await failed.get(".install-button").trigger("click");
    await flushPromises();
    expect(failed.text()).toContain("tenant already has an active subscription for this SKU");
    expect(failed.router.currentRoute.value.path).toBe("/p4/market");
  });
});
