/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ listTenants: vi.fn() }));
vi.mock("../../api/client", () => ({ p1: { listTenants: mocks.listTenants } }));

import TenantListView from "./TenantListView.vue";

const tenants = [
  { id: "tenant-1", code: "acme", name: "Acme Ltd", kind: "CUSTOMER", status: "ENABLED", legalName: "Acme Limited", version: 3 },
  { id: "tenant-2", code: "north", name: "Northwind", kind: "CUSTOMER", status: "DISABLED", legalName: "Northwind Inc", version: 1 },
];

async function mountList() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p1/tenants", component: TenantListView },
      { path: "/p1/tenants/new", component: { template: "<div />" } },
      { path: "/p1/tenants/:id", component: { template: "<div />" } },
    ],
  });
  await router.push("/p1/tenants"); await router.isReady();
  const wrapper = mount(TenantListView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("P1 tenant directory V4", () => {
  beforeEach(() => { mocks.listTenants.mockReset(); mocks.listTenants.mockResolvedValue(tenants); });

  it("filters the real list by tenant name/code and status", async () => {
    const wrapper = await mountList();
    expect(wrapper.get("[data-testid='nx-data-table']").text()).toContain("Acme Ltd");
    expect(wrapper.text()).toContain("Northwind");
    await wrapper.get("[data-testid='tenant-search']").setValue("ACM");
    expect(wrapper.get("[data-testid='nx-data-table']").text()).toContain("Acme Ltd");
    expect(wrapper.text()).not.toContain("Northwind");
    await wrapper.get("[data-testid='tenant-search']").setValue("");
    await wrapper.get("[data-testid='tenant-status-filter']").setValue("DISABLED");
    expect(wrapper.text()).toContain("Northwind"); expect(wrapper.text()).not.toContain("Acme Ltd");
  });

  it("shows API-available columns, honest unavailable data, and opens a detail drawer", async () => {
    const wrapper = await mountList();
    for (const label of ["管理员", "用户数", "应用数", "配额状态", "创建时间", "最近活动"]) expect(wrapper.text()).toContain(label);
    expect(wrapper.text()).toContain("暂无");
    await wrapper.get("[data-testid='tenant-row-tenant-1'] .tenant-name").trigger("click");
    expect(wrapper.get("[data-testid='tenant-drawer']").text()).toContain("Acme Limited");
    expect(wrapper.get("[data-testid='tenant-drawer']").text()).toContain("tenant-1");
    await wrapper.get("[aria-label='关闭详情']").trigger("click");
    expect(wrapper.find("[data-testid='tenant-drawer']").exists()).toBe(false);
  });

  it("uses NxEmpty for both an empty API response and a filter with no matches", async () => {
    mocks.listTenants.mockResolvedValueOnce([]);
    const empty = await mountList();
    expect(empty.text()).toContain("暂无租户");
    expect(empty.get("[data-testid='nx-empty-next']").text()).toContain("新建租户");
    empty.unmount();
    const filtered = await mountList();
    await filtered.get("[data-testid='tenant-search']").setValue("missing");
    expect(filtered.text()).toContain("没有符合当前筛选条件的租户");
    expect(filtered.get("[data-testid='tenant-empty-reset']").text()).toContain("清空筛选");
  });

  it("shows list-row skeletons while the directory loads", async () => {
    let resolveList;
    mocks.listTenants.mockReturnValueOnce(new Promise((resolve) => { resolveList = resolve; }));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/p1/tenants", component: TenantListView },
        { path: "/p1/tenants/new", component: { template: "<div />" } },
        { path: "/p1/tenants/:id", component: { template: "<div />" } },
      ],
    });
    await router.push("/p1/tenants");
    await router.isReady();
    const wrapper = mount(TenantListView, { global: { plugins: [router] } });
    expect(wrapper.get("[data-testid='nx-data-table-loading']").findAll("[data-testid='nx-skeleton']").length).toBeGreaterThan(0);
    resolveList(tenants);
    await flushPromises();
    expect(wrapper.find("[data-testid='nx-data-table-loading']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='nx-data-table']").exists()).toBe(true);
  });

  it("shows a retryable error when the platform directory is unavailable", async () => {
    mocks.listTenants.mockRejectedValueOnce(new Error("平台服务暂不可用"));
    const wrapper = await mountList();
    expect(wrapper.get("[data-testid='nx-data-table-error']").text()).toContain("平台服务暂不可用");
  });
});
