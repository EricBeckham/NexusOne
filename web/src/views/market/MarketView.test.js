/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";

const mocks = vi.hoisted(() => ({ catalog: vi.fn(), install: vi.fn() }));
vi.mock("../../api/client", () => ({ p4: mocks }));
vi.mock("../../api/session", () => ({ session: { tenantId: "tenant-1" } }));

import MarketView from "./MarketView.vue";

describe("P4 catalog V11", () => {
  beforeEach(() => {
    mocks.catalog.mockReset(); mocks.install.mockReset();
    mocks.catalog.mockResolvedValue([
      { app: { id: "app-1", name: "星辰 CRM", code: "crm", summary: "客户关系管理" }, version: { id: "v1", version: "1.2.0", status: "PUBLISHED" }, skus: [{ code: "basic", name: "基础版" }] },
      { app: { id: "app-2", name: "财务平台", code: "finance", summary: "账务与报表" }, version: { id: "v2", version: "2.0.0", status: "PUBLISHED" }, skus: [] },
    ]);
  });

  it("renders published catalog cards and filters without category data", async () => {
    const wrapper = mount(MarketView); await flushPromises();
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("星辰 CRM");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("已发布");
    expect(wrapper.get(".app-logo").attributes("aria-label")).toBe("应用标识占位");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("暂无 SKU");
    expect(wrapper.text()).not.toContain("分类");
    await wrapper.get('[data-testid="catalog-search"]').setValue("BASIC");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("星辰 CRM");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).not.toContain("财务平台");
    expect(wrapper.text()).toContain("1 个可用版本");
  });

  it("preserves the tenant-scoped SKU installation action", async () => {
    mocks.install.mockResolvedValue({});
    vi.stubGlobal("alert", vi.fn());
    const wrapper = mount(MarketView); await flushPromises();
    await wrapper.get(".install-button").trigger("click"); await flushPromises();
    expect(mocks.install).toHaveBeenCalledWith("tenant-1", { skuCode: "basic", versionId: "v1" });
    vi.unstubAllGlobals();
  });

  it("shows empty and retryable error states", async () => {
    mocks.catalog.mockResolvedValueOnce([]);
    const empty = mount(MarketView); await flushPromises(); expect(empty.text()).toContain("暂无已发布应用"); empty.unmount();
    mocks.catalog.mockRejectedValueOnce(new Error("目录不可用"));
    const failed = mount(MarketView); await flushPromises(); expect(failed.get('[data-testid="nx-error-state"]').text()).toContain("目录不可用");
  });
});
