/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { p4 } from "../../api/client";
import CommercialEntitlementsView from "./CommercialEntitlementsView.vue";

vi.mock("../../api/client", () => ({ p4: {
  commercialEntitlements: vi.fn(),
  commercialEntitlement: vi.fn(),
} }));

const row = {
  subscription_id: "sub-1",
  sku_code: "LEAVE_TEAM",
  sku_name: "团队版",
  subscription_status: "ACTIVE",
  installation_status: "INSTALLED",
  lines: [{ key: "apps", enabled: true, quota: 10, price: 999, amount: 50 }],
};

async function open(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/entitlements", name: "p4-entitlements", component: CommercialEntitlementsView },
      { path: "/p4/entitlements/:id", name: "p4-entitlement", component: CommercialEntitlementsView },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(CommercialEntitlementsView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("P4 commercial entitlements page", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows the projection and hides prices", async () => {
    p4.commercialEntitlements.mockResolvedValue([row]);
    const wrapper = await open("/p4/entitlements");
    expect(wrapper.text()).toContain("团队版");
    expect(wrapper.text()).toContain("有效");
    expect(wrapper.text()).toContain("已安装");
    expect(wrapper.text()).toContain("apps");
    expect(wrapper.text()).not.toContain("999");
    expect(wrapper.text()).not.toContain("金额");
  });

  it("shows 暂无 when the tenant has no projection", async () => {
    p4.commercialEntitlements.mockResolvedValue([]);
    const wrapper = await open("/p4/entitlements");
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.find("table").exists()).toBe(false);
  });

  it("shows a missing quota as 暂无 on the detail page", async () => {
    p4.commercialEntitlement.mockResolvedValue({
      ...row,
      lines: [{ key: "seats", enabled: false, quota: null }],
      installation_status: null,
    });
    const wrapper = await open("/p4/entitlements/sub-1");
    expect(wrapper.text()).toContain("停用");
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).not.toContain("999");
    expect(p4.commercialEntitlement).toHaveBeenCalledWith("sub-1");
  });
});
