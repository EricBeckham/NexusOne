/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  check: vi.fn(),
  reportDecision: vi.fn(),
  listLedgers: vi.fn(),
  queryReport: vi.fn(),
  exportReport: vi.fn(),
}));
vi.mock("../../api/client", () => ({ perm: { check: mocks.check } }));
vi.mock("../../api/p3", () => ({
  p3: { reportDecision: mocks.reportDecision, listLedgers: mocks.listLedgers, queryReport: mocks.queryReport, exportReport: mocks.exportReport },
}));
vi.mock("../../api/session", () => ({ session: { contextId: "ctx-1", tenantId: "tenant-1" } }));

import ReportView from "./ReportView.vue";

async function mountReport() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p3/reports", component: ReportView }, { path: "/p3/ledgers", component: { template: "<div />" } }],
  });
  await router.push("/p3/reports");
  const wrapper = mount(ReportView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("report page visual standard (R15)", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.check.mockResolvedValue({ allowed: true, reason: "allowed", engine: "PermissionEngine" });
    mocks.reportDecision.mockResolvedValue({ query: { allowed: true }, export: { allowed: false, reason: "no_grant" } });
    mocks.listLedgers.mockResolvedValue({ items: [{ id: "l-1", name: "客户台账", code: "customers" }, { id: "l-2", name: "订单", code: "orders" }] });
  });

  it("shows permissions as tags, an idle state, and no raw decision JSON", async () => {
    const wrapper = await mountReport();
    expect(wrapper.get("[data-testid='report-query-permission']").text()).toBe("允许");
    expect(wrapper.get("[data-testid='report-export-permission']").text()).toBe("未获授权（no_grant）");
    expect(wrapper.text()).not.toContain("JSON");
    expect(wrapper.text()).not.toContain("/api/v1");
    expect(wrapper.find("[data-testid='g2-report-query-cell']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='report-idle']").text()).toContain("选择台账后查询");
    expect(wrapper.get("[data-testid='g2-report-ledger']").element.value).toBe("l-1");
    expect(wrapper.findAll(".nx-btn--primary, .primary").map((node) => node.text())).toEqual(["查询"]);
  });

  it("queries through the filter bar, shows a skeleton, the count and the table; reset goes back to defaults", async () => {
    let resolve;
    mocks.queryReport.mockReturnValue(new Promise((done) => { resolve = done; }));
    const wrapper = await mountReport();
    await wrapper.get("[data-testid='g2-report-ledger']").setValue("l-2");
    await wrapper.get("[data-testid='g2-report-search']").trigger("click");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.find("[data-testid='report-loading']").exists()).toBe(true);
    resolve({ items: [{ id: "r-1", title: "A", tenant_id: "t" }], total: 1 });
    await flushPromises();
    expect(mocks.queryReport).toHaveBeenCalledWith({ ledger_id: "l-2", filters: {}, page: 0, size: 20 });
    expect(wrapper.get("[data-testid='g2-report-total']").text()).toBe("共 1 条");
    expect(wrapper.findAll("[data-testid='g2-report-table'] thead th").map((th) => th.text())).toEqual(["id", "title"]);

    await wrapper.get("[data-testid='g2-report-reset']").trigger("click");
    expect(wrapper.get("[data-testid='g2-report-ledger']").element.value).toBe("l-1");
    expect(wrapper.find("[data-testid='g2-report-table']").exists()).toBe(false);
    expect(wrapper.find("[data-testid='report-idle']").exists()).toBe(true);
  });

  it("shows an empty result and a created export task as secondary information", async () => {
    mocks.queryReport.mockResolvedValue({ items: [], total: 0 });
    mocks.exportReport.mockResolvedValue({ task_type: "ledger.export.csv", physical_dump: false });
    const wrapper = await mountReport();
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get("[data-testid='report-empty']").text()).toContain("没有匹配记录");
    await wrapper.get("[data-testid='g2-report-export']").trigger("click");
    await flushPromises();
    const result = wrapper.get("[data-testid='g2-report-export-result']");
    expect(result.text()).toContain("已创建导出任务");
    expect(result.text()).toContain("ledger.export.csv");
    expect(result.text()).not.toContain("physical_dump");
    expect(wrapper.get("[data-testid='g2-report-export']").classes()).toContain("nx-btn--default");
  });

  it("does not call a failed query an empty result", async () => {
    // Live case: a DRAFT ledger answers P3.LEDGER.0003 "ledger physical table is not ready".
    mocks.queryReport.mockRejectedValue(Object.assign(new Error("ledger physical table is not ready"), { status: 409, code: "P3.LEDGER.0003" }));
    const wrapper = await mountReport();
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get("[data-testid='g2-report-error']").text()).toContain("P3.LEDGER.0003");
    expect(wrapper.get("[data-testid='report-failed']").text()).toContain("查询没有完成");
    expect(wrapper.find("[data-testid='report-empty']").exists()).toBe(false);
    expect(wrapper.find("[data-testid='g2-report-total']").exists()).toBe(false);
  });
});
