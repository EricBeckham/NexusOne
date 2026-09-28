/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  apps: vi.fn(), skus: vi.fn(), versions: vi.fn(), subscriptions: vi.fn(), installations: vi.fn(),
  products: vi.fn(), archiveProduct: vi.fn(), publishProduct: vi.fn(),
  commercialEntitlements: vi.fn(),
  session: { contextId: "ctx-1", tenantId: "tenant-1", platformMode: true, products: ["p4"] },
}));
vi.mock("../../api/client", () => ({ p4: mocks }));
vi.mock("../../api/session", () => ({ session: mocks.session }));

import P4ManagementView from "./P4ManagementView.vue";

describe("PLB6 product shelf", () => {
  beforeEach(() => {
    mocks.apps.mockResolvedValue([{ id: "app-1", code: "leave", name: "请假", created_at: "2026-01-01T00:00:00Z" }]);
    mocks.skus.mockResolvedValue([]);
    mocks.versions.mockResolvedValue([]);
    mocks.subscriptions.mockResolvedValue([]);
    mocks.installations.mockResolvedValue([]);
    mocks.commercialEntitlements.mockResolvedValue([]);
    mocks.archiveProduct.mockResolvedValue({});
    mocks.publishProduct.mockResolvedValue({});
    mocks.products.mockResolvedValue([
      { id: "p1", code: "pl_leave", name: "请假", status: "PUBLISHED", version: 3, auto_upgrade_minor: false },
    ]);
  });

  it("archives a published product from the package page", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/p4/packages", name: "p4-packages", component: P4ManagementView },
        { path: "/p4/installations", component: { template: "<div />" } },
        { path: "/p4/market", component: { template: "<div />" } },
      ],
    });
    await router.push("/p4/packages");
    const wrapper = mount(P4ManagementView, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.get("[data-testid='archive-pl_leave']").trigger("click");
    await flushPromises();
    expect(mocks.archiveProduct).toHaveBeenCalledWith("p1", 3);
    expect(wrapper.text()).toContain("已下架 请假");
  });
});
