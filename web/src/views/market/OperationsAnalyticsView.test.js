/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  catalog: vi.fn(),
  subscriptions: vi.fn(),
  installations: vi.fn(),
  session: { contextId: "ctx-1" },
}));
vi.mock("../../api/client", () => ({
  p4: {
    catalog: mocks.catalog,
    subscriptions: mocks.subscriptions,
    installations: mocks.installations,
  },
}));
vi.mock("../../api/session", () => ({
  session: mocks.session,
}));

import OperationsAnalyticsView from "./OperationsAnalyticsView.vue";

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/operations", name: "p4-operations", component: OperationsAnalyticsView },
      { path: "/p4/market", component: { template: "<div>market</div>" } },
      { path: "/p4/entitlements", component: { template: "<div>entitlements</div>" } },
    ],
  });
  await router.push("/p4/operations");
  const wrapper = mount(OperationsAnalyticsView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("operations analytics (XB3/XB5)", () => {
  beforeEach(() => {
    mocks.catalog.mockReset();
    mocks.subscriptions.mockReset();
    mocks.installations.mockReset();
    mocks.session.contextId = "ctx-1";
  });

  it("aligns page header crumbs with the market path and shows live counts", async () => {
    mocks.catalog.mockResolvedValue([
      { app: { id: "app-1", name: "CRM", code: "crm" }, version: { id: "v-1", version: "1.0.0" }, skus: [{ code: "basic" }] },
      { app: { id: "app-2", name: "HR", code: "hr" }, version: { id: "v-2", version: "2.0.0" }, skus: [] },
    ]);
    mocks.subscriptions.mockResolvedValue([{ id: "sub-1" }, { id: "sub-2" }, { id: "sub-3" }]);
    mocks.installations.mockResolvedValue([{ id: "inst-1" }]);
    const wrapper = await mountPage();
    const crumb = wrapper.get("[data-testid='nx-page-crumb']");
    expect(crumb.get("a").attributes("href")).toBe("/p4/market");
    expect(crumb.get('[aria-current="page"]').text()).toBe("运营分析");
    expect(wrapper.get("[data-testid='nx-page-header']").text()).toContain("轻量运营分析");
    expect(wrapper.get("[data-testid='ops-stat-apps']").text()).toContain("2");
    expect(wrapper.get("[data-testid='ops-stat-subscriptions']").text()).toContain("3");
    expect(wrapper.get("[data-testid='ops-stat-installations']").text()).toContain("1");
    expect(wrapper.get("[data-testid='operations-catalog-table']").text()).toContain("CRM");
    expect(wrapper.get("[data-testid='operations-trend-note']").text()).toContain("趋势暂无读模型");
    expect(wrapper.find("canvas").exists()).toBe(false);
    expect(wrapper.get("[data-testid='nx-page-header']").text()).not.toMatch(/\bAPI\b/);
  });

  it("shows catalog empty and zeros without inventing trend data", async () => {
    mocks.catalog.mockResolvedValue([]);
    mocks.subscriptions.mockResolvedValue([]);
    mocks.installations.mockResolvedValue([]);
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='operations-catalog-empty']").text()).toContain("暂无已上架应用");
    expect(wrapper.get("[data-testid='ops-stat-apps']").text()).toContain("0");
    expect(wrapper.get("[data-testid='operations-trend-note']").exists()).toBe(true);
    expect(wrapper.find("[data-testid='operations-error']").exists()).toBe(false);
  });

  it("surfaces catalog errors with retry", async () => {
    mocks.catalog.mockRejectedValueOnce(Object.assign(new Error("catalog down"), { traceId: "trace-ops" }));
    mocks.catalog.mockResolvedValueOnce([]);
    mocks.subscriptions.mockResolvedValue([]);
    mocks.installations.mockResolvedValue([]);
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='operations-error']").text()).toContain("catalog down");
    expect(wrapper.text()).toContain("trace-ops");
    expect(mocks.subscriptions).not.toHaveBeenCalled();
    await wrapper.get("[data-testid='operations-error'] button").trigger("click");
    await flushPromises();
    expect(wrapper.find("[data-testid='operations-error']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='operations-catalog-empty']").exists()).toBe(true);
  });

  it("hints when tenant context is missing and still shows catalog counts", async () => {
    mocks.session.contextId = "";
    mocks.catalog.mockResolvedValue([
      { app: { id: "app-1", name: "CRM", code: "crm" }, version: { id: "v-1", version: "1.0.0" }, skus: [] },
    ]);
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='operations-tenant-hint']").text()).toContain("未选择租户");
    expect(wrapper.get("[data-testid='ops-stat-apps']").text()).toContain("1");
    expect(wrapper.get("[data-testid='ops-stat-subscriptions']").text()).toContain("0");
    expect(mocks.subscriptions).not.toHaveBeenCalled();
  });
});
