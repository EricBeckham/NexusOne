/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  catalog: vi.fn(),
  catalogGroups: vi.fn(),
  subscriptions: vi.fn(),
  activationApplications: vi.fn(),
  requestActivation: vi.fn(),
  session: { tenantId: "tenant-1", contextId: "ctx-1" },
}));
vi.mock("../../api/client", () => ({ p4: mocks }));
vi.mock("../../api/session", () => ({ session: mocks.session }));

import MarketView from "./MarketView.vue";

describe("PLB6 catalog groups", () => {
  beforeEach(() => {
    mocks.catalog.mockResolvedValue([]);
    mocks.subscriptions.mockResolvedValue([]);
    mocks.activationApplications.mockResolvedValue([]);
    mocks.requestActivation.mockResolvedValue({ status: "PENDING" });
    mocks.catalogGroups.mockResolvedValue([{
      category_code: "oa",
      category_name: "办公",
      products: [{ id: "p1", code: "pl_leave", name: "请假", description: "请假产品", skus: [{ code: "pl_leave", name: "请假" }] }],
    }]);
  });

  it("shows product shelves under their category and requests activation without a version", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/p4/market", component: MarketView }, { path: "/p4/installations", component: { template: "<div />" } }],
    });
    await router.push("/p4/market");
    const wrapper = mount(MarketView, { global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.get("[data-testid='catalog-group-oa']").text()).toContain("办公");
    expect(wrapper.get("[data-testid='catalog-product']").text()).toContain("请假");
    await wrapper.get("[data-testid='catalog-product-subscribe']").trigger("click");
    await flushPromises();
    expect(mocks.requestActivation).toHaveBeenCalledWith("tenant-1", { sku_code: "pl_leave", version_id: null });
  });
});
