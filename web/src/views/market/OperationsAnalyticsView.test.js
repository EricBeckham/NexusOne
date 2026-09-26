/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  catalog: vi.fn(),
  subscriptions: vi.fn(),
  installations: vi.fn(),
}));
vi.mock("../../api/client", () => ({
  p4: {
    catalog: mocks.catalog,
    subscriptions: mocks.subscriptions,
    installations: mocks.installations,
  },
}));
vi.mock("../../api/session", () => ({
  session: { contextId: "ctx-1" },
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

describe("operations analytics counts (XB3)", () => {
  beforeEach(() => {
    mocks.catalog.mockReset();
    mocks.subscriptions.mockReset();
    mocks.installations.mockReset();
  });

  it("shows live counts from catalog/subscriptions/installations without a trend chart", async () => {
    mocks.catalog.mockResolvedValue([
      { app: { id: "app-1", name: "CRM", code: "crm" }, version: { id: "v-1", version: "1.0.0" }, skus: [{ code: "basic" }] },
      { app: { id: "app-2", name: "HR", code: "hr" }, version: { id: "v-2", version: "2.0.0" }, skus: [] },
    ]);
    mocks.subscriptions.mockResolvedValue([{ id: "sub-1" }, { id: "sub-2" }, { id: "sub-3" }]);
    mocks.installations.mockResolvedValue([{ id: "inst-1" }]);
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='ops-stat-apps']").text()).toContain("2");
    expect(wrapper.get("[data-testid='ops-stat-subscriptions']").text()).toContain("3");
    expect(wrapper.get("[data-testid='ops-stat-installations']").text()).toContain("1");
    expect(wrapper.get("[data-testid='operations-catalog-table']").text()).toContain("CRM");
    expect(wrapper.get("[data-testid='operations-trend-note']").text()).toContain("趋势暂无读模型");
    expect(wrapper.find("canvas").exists()).toBe(false);
    expect(wrapper.find("[data-testid='operations-empty']").exists()).toBe(false);
  });

  it("surfaces catalog errors with retry and keeps trend honest", async () => {
    mocks.catalog.mockRejectedValue(Object.assign(new Error("catalog down"), { traceId: "trace-ops" }));
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='operations-error']").text()).toContain("catalog down");
    expect(wrapper.text()).toContain("trace-ops");
    expect(mocks.subscriptions).not.toHaveBeenCalled();
  });
});
