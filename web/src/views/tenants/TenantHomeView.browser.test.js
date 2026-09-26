/**
 * @vitest-environment happy-dom
 */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { p1 } from "../../api/client";
import TenantHomeView from "./TenantHomeView.vue";

async function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p1", component: TenantHomeView },
      { path: "/p1/tenants", component: { template: "<div />" } },
    ],
  });
  await router.push("/p1");
  await router.isReady();
  return mount(TenantHomeView, { global: { plugins: [router] } });
}

vi.mock("../../api/client", () => ({
  p1: {
    listTenants: vi.fn(),
    platformTenant: vi.fn(),
  },
}));

describe("P1 home", () => {
  beforeEach(() => {
    p1.listTenants.mockReset();
    p1.platformTenant.mockReset();
  });

  it("shows counts from the tenant APIs", async () => {
    p1.listTenants.mockResolvedValue([{ id: "a" }, { id: "b" }]);
    p1.platformTenant.mockResolvedValue({ name: "平台租户" });
    const wrapper = await mountHome();
    await flushPromises();
    expect(wrapper.find("[data-testid='dashboard-layers']").exists()).toBe(true);
    expect(wrapper.text()).toContain("星域");
    expect(wrapper.text()).toContain("2");
    expect(wrapper.text()).toContain("平台租户");
    expect(wrapper.text()).not.toContain("12");
    expect(wrapper.get("a.text-link[href='/p1/tenants']").text()).toContain("查看全部");
    expect(wrapper.get(".page-header a.primary[href='/p1/tenants']").text()).toContain("租户目录");
  });

  it("shows 暂无 when the APIs fail", async () => {
    p1.listTenants.mockRejectedValue(new Error("down"));
    p1.platformTenant.mockResolvedValue({});
    const wrapper = await mountHome();
    await flushPromises();
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).toContain("接口不可用");
    expect(wrapper.text()).not.toMatch(/\d+/);
  });
});
