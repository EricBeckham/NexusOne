/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it } from "vitest";
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

describe("operations analytics shell (XB1)", () => {
  it("renders page header and honest empty shell without fake KPIs", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='operations-analytics']").exists()).toBe(true);
    expect(wrapper.text()).toContain("运营分析");
    expect(wrapper.text()).toContain("轻量运营分析");
    expect(wrapper.text()).toContain("非独立 BI");
    expect(wrapper.get("[data-testid='operations-empty']").text()).toContain("尚未接线");
    expect(wrapper.find("canvas").exists()).toBe(false);
    expect(wrapper.find("[data-testid='ops-stat-apps']").exists()).toBe(false);
    expect(wrapper.find(".nx-kpi, [data-testid='fake-kpi']").exists()).toBe(false);
    expect(wrapper.get("a[href='/p4/market']").exists()).toBe(true);
  });
});
