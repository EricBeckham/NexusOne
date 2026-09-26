/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  listLedgers: vi.fn(),
  listApplications: vi.fn(),
  listBusinessTypes: vi.fn(),
  quotas: vi.fn(),
}));
vi.mock("../../api/p3", () => ({ p3: mocks }));
vi.mock("../../api/client", () => ({ p1: { quotas: mocks.quotas } }));
vi.mock("../../api/session", () => ({ session: { contextId: "ctx-1", tenantId: "tenant-1" } }));

import LedgerListView from "./LedgerListView.vue";

async function mountList() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p3/ledgers", component: LedgerListView }, { path: "/p3/ledgers/:id", component: { template: "<div />" } }],
  });
  await router.push("/p3/ledgers");
  const wrapper = mount(LedgerListView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("ledger list visual standard (R13)", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.listLedgers.mockResolvedValue({ items: [] });
    mocks.listApplications.mockResolvedValue({ items: [{ id: "app-1", display_name: "客户管理" }] });
    mocks.listBusinessTypes.mockResolvedValue({ items: [] });
    mocks.quotas.mockResolvedValue([{ metric: "CUSTOM_LEDGER", used: 3, limit: 10 }]);
  });

  it("loads with a skeleton, then an empty state that opens the create panel", async () => {
    let resolve;
    mocks.listLedgers.mockReturnValue(new Promise((done) => { resolve = done; }));
    const wrapper = await mountList();
    expect(wrapper.find("[data-testid='ledger-loading'] [data-testid='nx-skeleton']").exists()).toBe(true);
    resolve({ items: [] });
    await flushPromises();
    const empty = wrapper.get("[data-testid='ledger-empty']");
    expect(empty.text()).toContain("还没有台账");
    expect(wrapper.text()).not.toContain("M0-06");
    await empty.get("button").trigger("click");
    expect(wrapper.find("[data-testid='ledger-create-form']").exists()).toBe(true);
    expect(wrapper.findAll(".primary, .nx-btn--primary").map((node) => node.text())).toEqual(["创建应用和台账"]);
  });

  it("shows the quota as one line without API paths", async () => {
    const wrapper = await mountList();
    const quota = wrapper.get("[data-testid='ledger-quota']");
    expect(quota.text()).toContain("已用 3 / 上限 10");
    expect(quota.get(".quota-bar span").attributes("style")).toContain("30%");
    expect(wrapper.text()).not.toContain("/api/v1");
  });

  it("renders readable columns, status tags and a 查看 entry per row", async () => {
    mocks.listLedgers.mockResolvedValue({
      items: [
        { id: "l-1", name: "客户台账", code: "customers", application_id: "app-1", source: "TENANT_CUSTOM", field_count: 4, running_schema_version: 2, status: "READY" },
        { id: "l-2", name: "共享台账", code: "shared", application_id: "app-x", source: "APP_SHARED", field_count: 1, running_schema_version: 1, status: "DISABLED" },
      ],
    });
    const wrapper = await mountList();
    expect(wrapper.findAll("thead th").map((th) => th.text())).toEqual(["台账", "代码", "应用", "来源", "字段", "结构版本", "状态", "操作"]);
    const [first, second] = wrapper.findAll("[data-testid='ledger-row']");
    expect(first.text()).toContain("客户管理");
    expect(first.text()).toContain("租户自建");
    expect(first.get("[data-testid='nx-status-tag']").text()).toBe("就绪");
    expect(second.text()).toContain("应用共享");
    expect(second.get("[data-testid='nx-status-tag']").text()).toBe("已停用");
    expect(first.get(".open-link").attributes("href")).toBe("/p3/ledgers/l-1");
    expect(wrapper.findAll("tbody .primary, tbody .nx-btn--primary")).toHaveLength(0);
  });
});