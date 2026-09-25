/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ catalog: vi.fn(), subscribe: vi.fn() }));
vi.mock("../../api/client", () => ({ p4: mocks }));
vi.mock("../../api/session", () => ({ session: { tenantId: "tenant-1" } }));

import MarketView from "./MarketView.vue";

// Real GET /api/v1/p4/catalog SKU shape: CatalogSku { sku, product_definition } in SNAKE_CASE.
const BOUND_BASIC = {
  sku: { code: "basic", app_id: "app-1", name: "基础版", entitlements: { approval: { enabled: true, quota: 10, attributes: {} } } },
  product_definition: { id: "pd-1", code: "crm_basic", name: "CRM 基础审批", status: "PUBLISHED" },
};

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
      { app: { id: "app-1", name: "星辰 CRM", code: "crm", summary: "客户关系管理" }, version: { id: "v1", version: "1.2.0", status: "PUBLISHED" }, skus: [BOUND_BASIC] },
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
    expect(mocks.subscribe).toHaveBeenCalledWith("tenant-1", { sku_code: "basic", version_id: "v1" });
    expect(wrapper.router.currentRoute.value.path).toBe("/p4/installations");
  });

  it("reads the nested SKU fields instead of rendering undefined", async () => {
    const wrapper = await mountCatalog();
    const sku = wrapper.get('[data-testid="catalog-sku"]');
    expect(wrapper.text()).not.toContain("undefined");
    expect(sku.get('[data-testid="catalog-sku-name"]').text()).toBe("基础版");
    expect(sku.text()).toContain("basic");
    expect(sku.get('[data-testid="catalog-sku-product"]').text()).toContain("CRM 基础审批");
    expect(sku.text()).toContain("approval · 配额 10");
    expect(sku.get('[data-testid="catalog-subscribe"]').text()).toBe("订阅开通");
    expect(sku.get('[data-testid="catalog-subscribe"]').attributes("aria-label")).toBe("订阅开通 基础版");
    expect(wrapper.get('[data-testid="catalog-sku-count"]').text()).toBe("1 个");
    await wrapper.get('[data-testid="catalog-search"]').setValue("基础版");
    await flushPromises();
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("基础版");
  });

  it("marks a SKU without a product definition as not subscribable instead of letting subscribe 404", async () => {
    mocks.catalog.mockResolvedValue([
      {
        app: { id: "app-1", name: "演示数据 · 简单审批", code: "demo_simple_approval" },
        version: { id: "v1", version: "1.0.0", status: "PUBLISHED" },
        skus: [{ sku: { code: "demo.simple-approval", name: "演示数据 · 标准", entitlements: {} } }],
      },
    ]);
    const wrapper = await mountCatalog();
    expect(wrapper.get('[data-testid="catalog-sku-unbound"]').text()).toContain("未绑定产品定义");
    const button = wrapper.get('[data-testid="catalog-subscribe"]');
    expect(button.attributes("disabled")).toBeDefined();
    await button.trigger("click");
    expect(mocks.subscribe).not.toHaveBeenCalled();
  });

  it("shows empty and the server failure text", async () => {
    mocks.catalog.mockResolvedValueOnce([]);
    const empty = await mountCatalog();
    expect(empty.text()).toContain("暂无");
    empty.unmount();

    mocks.catalog.mockResolvedValueOnce([
      { app: { id: "app-1", name: "星辰 CRM", code: "crm", summary: "客户关系管理" }, version: { id: "v1", version: "1.2.0", status: "PUBLISHED" }, skus: [BOUND_BASIC] },
    ]);
    mocks.subscribe.mockRejectedValueOnce(new Error("tenant already has an active subscription for this SKU"));
    const failed = await mountCatalog();
    await failed.get(".install-button").trigger("click");
    await flushPromises();
    expect(failed.text()).toContain("tenant already has an active subscription for this SKU");
    expect(failed.router.currentRoute.value.path).toBe("/p4/market");
  });
});
