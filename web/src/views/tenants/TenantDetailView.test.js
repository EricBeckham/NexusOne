/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  getTenant: vi.fn(),
  getConfig: vi.fn(),
  entitlement: vi.fn(),
  quotas: vi.fn(),
  audit: vi.fn(),
  putConfig: vi.fn(),
  enable: vi.fn(),
  disable: vi.fn(),
}));

vi.mock("../../api/client", () => ({ p1: mocks }));

import TenantDetailView from "./TenantDetailView.vue";

async function mountDetail(id = "tenant-1") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p1/tenants/:id", component: TenantDetailView },
      { path: "/p1/tenants", component: { template: "<div />" } },
      { path: "/p1/tenants/:id/entitlement", component: { template: "<div />" } },
    ],
  });
  await router.push(`/p1/tenants/${id}`);
  await router.isReady();
  const wrapper = mount(TenantDetailView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("R7 TenantDetailView", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.getTenant.mockResolvedValue({ id: "tenant-1", name: "Acme", code: "acme", status: "ENABLED" });
    mocks.getConfig.mockResolvedValue({ display_name: "Acme", locale: "zh-CN", timezone: "Asia/Shanghai", currency: "CNY" });
    mocks.entitlement.mockResolvedValue({ source: "pack", pack_code: "std", pack_version: "1" });
    mocks.quotas.mockResolvedValue([{ metric: "users", used: 2, limit: 10, warning: false }]);
    mocks.audit.mockResolvedValue([{ event_id: "e1", occurred_at: "2026-01-01", event_type: "TENANT_UPDATE", outcome: "ACCEPTED", trace_id: "t1" }]);
  });

  it("renders page header crumbs, one header lifecycle control, and one config Primary", async () => {
    const wrapper = await mountDetail();
    expect(wrapper.get("[data-testid='nx-page-crumb']").text()).toContain("租户目录");
    expect(wrapper.get("[data-testid='tenant-disable']").exists()).toBe(true);
    expect(wrapper.find("[data-testid='tenant-enable']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='tenant-config-section']").findAll("button.primary")).toHaveLength(1);
    expect(wrapper.text()).toContain("基础配置");
    expect(wrapper.text()).toContain("审计摘要");
  });

  it("shows skeleton while loading then the compact audit table", async () => {
    let resolveTenant;
    mocks.getTenant.mockReturnValueOnce(new Promise((resolve) => { resolveTenant = resolve; }));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/p1/tenants/:id", component: TenantDetailView }],
    });
    await router.push("/p1/tenants/tenant-1");
    await router.isReady();
    const wrapper = mount(TenantDetailView, { global: { plugins: [router] } });
    expect(wrapper.get("[data-testid='tenant-detail-loading']").exists()).toBe(true);
    resolveTenant({ id: "tenant-1", name: "Acme", code: "acme", status: "DISABLED" });
    await flushPromises();
    expect(wrapper.find("[data-testid='tenant-detail-loading']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='tenant-enable']").exists()).toBe(true);
  });
});
