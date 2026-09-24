/**
 * @vitest-environment happy-dom
 */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { p1 } from "../../api/client";
import TenantEntitlementView from "./TenantEntitlementView.vue";

vi.mock("../../api/client", () => ({
  p1: {
    listTenants: vi.fn(),
    entitlement: vi.fn(),
    quotas: vi.fn(),
  },
}));

const routes = [
  { path: "/p1/quotas", name: "p1-quotas", component: TenantEntitlementView },
  { path: "/p1/tenants/:id", name: "tenant-detail", component: { template: "<p>detail</p>" } },
  { path: "/p1/tenants/:id/entitlement", name: "tenant-entitlement", component: TenantEntitlementView },
];

async function open(path) {
  const router = createRouter({ history: createMemoryHistory(), routes });
  router.push(path);
  await router.isReady();
  const wrapper = mount(TenantEntitlementView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("P1 entitlement page", () => {
  beforeEach(() => {
    p1.listTenants.mockReset();
    p1.entitlement.mockReset();
    p1.quotas.mockReset();
  });

  it("lists real tenants and does not invent quota numbers", async () => {
    p1.listTenants.mockResolvedValue([{ id: "t-1", code: "alpha", name: "Alpha", status: "ENABLED" }]);
    const wrapper = await open("/p1/quotas");
    expect(wrapper.text()).toContain("alpha");
    expect(wrapper.text()).toContain("Alpha");
    expect(wrapper.text()).not.toMatch(/已用|上限/);
    expect(wrapper.get("a[href='/p1/tenants/t-1/entitlement']").text()).toBe("查看");
  });

  it("shows 暂无 when there is no tenant", async () => {
    p1.listTenants.mockResolvedValue([]);
    const wrapper = await open("/p1/quotas");
    expect(wrapper.text()).toContain("暂无");
  });

  it("shows entitlement and quota fields returned by the API", async () => {
    p1.entitlement.mockResolvedValue({
      source: "DEFAULT_PACK",
      pack_code: "default",
      payload: { schema: "p4.runtime-entitlement.v1", entitlements: { apps: { enabled: true, quota: 3 } } },
    });
    p1.quotas.mockResolvedValue([{ metric: "APPS", used: 1, limit: 3, warn_percent: 80, over_limit_policy: "REJECT", warning: false }]);
    const wrapper = await open("/p1/tenants/t-1/entitlement");
    expect(wrapper.text()).toContain("DEFAULT_PACK");
    expect(wrapper.text()).toContain("apps");
    expect(wrapper.text()).toContain("3");
    expect(wrapper.text()).toContain("APPS");
    expect(wrapper.text()).toContain("REJECT");
    expect(p1.entitlement).toHaveBeenCalledWith("t-1");
  });

  it("shows an error instead of numbers when the quota API fails", async () => {
    p1.entitlement.mockResolvedValue(null);
    p1.quotas.mockRejectedValue(Object.assign(new Error("down"), { traceId: "trace-1" }));
    const wrapper = await open("/p1/tenants/t-1/entitlement");
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).toContain("down");
    expect(wrapper.text()).toContain("trace-1");
    expect(wrapper.text()).not.toMatch(/\/\d+/);
  });
});
