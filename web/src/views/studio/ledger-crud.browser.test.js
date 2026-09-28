/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter, RouterView } from "vue-router";

const mocks = vi.hoisted(() => ({
  listApplications: vi.fn(),
  createApplication: vi.fn(),
  listLedgers: vi.fn(),
  createLedger: vi.fn(),
  getLedger: vi.fn(),
  getRecord: vi.fn(),
  listFields: vi.fn(),
  addField: vi.fn(),
  listRecords: vi.fn(),
  createRecord: vi.fn(),
  updateRecord: vi.fn(),
  deleteRecord: vi.fn(),
  listBusinessTypes: vi.fn(),
  provisionBusinessType: vi.fn(),
  quotas: vi.fn(),
  check: vi.fn(),
}));

vi.mock("../../api/p3", () => ({ p3: mocks }));
vi.mock("../../api/client", () => ({
  p1: { quotas: mocks.quotas },
  perm: { check: mocks.check },
}));
vi.mock("../../api/session", () => ({
  session: { contextId: "t3e-context", tenantId: "tenant-1" },
}));

import LedgerListView from "./LedgerListView.vue";
import LedgerDetailView from "./LedgerDetailView.vue";
import LedgerRecordDetailView from "./LedgerRecordDetailView.vue";

const fields = [
  { id: "field-title", code: "title", name: "标题", type: "text", required: true },
  { id: "field-amount", code: "amount", name: "金额", type: "numeric", required: false },
];
const ledger = {
  id: "ledger-1",
  name: "客户台账",
  code: "customers",
  physical_table: "l_tenant_crm_customers",
  running_schema_version: 1,
  application_id: "app-1",
  fields,
  status: "READY",
};

async function mountAt(path, component) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p3/ledgers", component: LedgerListView },
      { path: "/p3/ledgers/:id", component: LedgerDetailView },
      { path: "/p3/ledgers/:id/records/:recordId", component: LedgerRecordDetailView },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(component, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("T3-E application and ledger CRUD", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.listApplications.mockResolvedValue({ items: [] });
    mocks.listLedgers.mockResolvedValue({ items: [] });
    mocks.listBusinessTypes.mockResolvedValue({ items: [] });
    mocks.quotas.mockResolvedValue([{ metric: "CUSTOM_LEDGER", used: 0, limit: 10 }]);
    mocks.check.mockResolvedValue({ allowed: true });
  });

  it("creates one custom application and its first ledger", async () => {
    mocks.createApplication.mockResolvedValue({ id: "app-1", app_code: "crm", display_name: "客户管理" });
    mocks.createLedger.mockResolvedValue(ledger);
    const { wrapper, router } = await mountAt("/p3/ledgers", LedgerListView);

    await wrapper.get('[data-testid="ledger-create-toggle"]').trigger("click");
    await wrapper.get('[data-testid="application-code"]').setValue("crm");
    await wrapper.get('[data-testid="application-name"]').setValue("客户管理");
    await wrapper.get('[data-testid="ledger-code"]').setValue("customers");
    await wrapper.get('[data-testid="ledger-name"]').setValue("客户台账");
    await wrapper.get('[data-testid="ledger-create-form"]').trigger("submit");
    await flushPromises();

    expect(mocks.createApplication).toHaveBeenCalledWith({
      app_code: "crm",
      display_name: "客户管理",
      kind: "CUSTOM",
    });
    expect(mocks.createLedger).toHaveBeenCalledWith(expect.objectContaining({
      application_id: "app-1",
      ledger_code: "customers",
      display_name: "客户台账",
      scope: "TENANT",
      fields: [expect.objectContaining({ code: "title", name: "标题", type: "text", required: true })],
    }));
    expect(router.currentRoute.value.fullPath).toBe("/p3/ledgers/ledger-1");
  });

  it("validates all three identifier codes before creating an application", async () => {
    const { wrapper } = await mountAt("/p3/ledgers", LedgerListView);
    await wrapper.get('[data-testid="ledger-create-toggle"]').trigger("click");
    await wrapper.get('[data-testid="application-code"]').setValue("费用应用");
    await wrapper.get('[data-testid="application-name"]').setValue("费用应用");
    await wrapper.get('[data-testid="ledger-code"]').setValue("expense");
    await wrapper.get('[data-testid="ledger-name"]').setValue("付款台账");
    await wrapper.get('[data-testid="ledger-create-form"]').trigger("submit");
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("应用代码须以英文字母开头");
    expect(mocks.createApplication).not.toHaveBeenCalled();
    expect(mocks.createLedger).not.toHaveBeenCalled();

    await wrapper.get('[data-testid="application-code"]').setValue("expense");
    await wrapper.get('[data-testid="ledger-code"]').setValue("付款台账");
    await wrapper.get('[data-testid="ledger-create-form"]').trigger("submit");
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("台账代码须以英文字母开头");
    expect(mocks.createApplication).not.toHaveBeenCalled();

    await wrapper.get('[data-testid="ledger-code"]').setValue("payments");
    await wrapper.get('[data-testid="ledger-field-code"]').setValue("tenant_id");
    await wrapper.get('[data-testid="ledger-create-form"]').trigger("submit");
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("系统保留字段");
    expect(mocks.createApplication).not.toHaveBeenCalled();
  });

  it("reuses the same tenant application after a ledger request fails, without a duplicate POST", async () => {
    mocks.createApplication.mockResolvedValue({ id: "app-retry", app_code: "expense", display_name: "费用应用", kind: "CUSTOM" });
    mocks.createLedger.mockRejectedValueOnce(Object.assign(new Error("illegal identifier"), { code: "P3.SCHEMA.0001" }))
      .mockResolvedValueOnce(ledger);
    const { wrapper, router } = await mountAt("/p3/ledgers", LedgerListView);
    await wrapper.get('[data-testid="ledger-create-toggle"]').trigger("click");
    await wrapper.get('[data-testid="application-code"]').setValue("EXPENSE");
    await wrapper.get('[data-testid="application-name"]').setValue("费用应用");
    await wrapper.get('[data-testid="ledger-code"]').setValue("payments");
    await wrapper.get('[data-testid="ledger-name"]').setValue("付款台账");
    await wrapper.get('[data-testid="ledger-create-form"]').trigger("submit");
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("代码不符合台账标识符规则");
    expect(mocks.createApplication).toHaveBeenCalledTimes(1);
    expect(mocks.createLedger).toHaveBeenCalledWith(expect.objectContaining({ ledger_code: "payments", application_id: "app-retry" }));
    await wrapper.get('[data-testid="ledger-create-form"]').trigger("submit");
    await flushPromises();
    expect(mocks.createApplication).toHaveBeenCalledTimes(1);
    expect(mocks.createLedger).toHaveBeenCalledTimes(2);
    expect(router.currentRoute.value.fullPath).toBe("/p3/ledgers/ledger-1");
  });

  it("creates, updates with version, and deletes a ledger record", async () => {
    let records = [{ id: "record-1", title: "旧标题", amount: 10, version: 3 }];
    mocks.getLedger.mockResolvedValue(ledger);
    mocks.listFields.mockResolvedValue({ items: fields, system: [] });
    mocks.listRecords.mockImplementation(async () => ({ items: records }));
    mocks.createRecord.mockImplementation(async (_ledgerId, values) => {
      records = [...records, { id: "record-2", ...values, version: 1 }];
    });
    mocks.updateRecord.mockImplementation(async (_ledgerId, id, values) => {
      records = records.map((row) => row.id === id ? { ...row, ...values, version: row.version + 1 } : row);
    });
    mocks.deleteRecord.mockImplementation(async (_ledgerId, id) => {
      records = records.filter((row) => row.id !== id);
    });
    vi.stubGlobal("confirm", vi.fn(() => true));

    const { wrapper } = await mountAt("/p3/ledgers/ledger-1", LedgerDetailView);
    await wrapper.get('[data-testid="record-create-toggle"]').trigger("click");
    await wrapper.get('[data-testid="record-field-title"]').setValue("新标题");
    await wrapper.get('[data-testid="record-field-amount"]').setValue("25");
    await wrapper.get('[data-testid="record-editor"]').trigger("submit");
    await flushPromises();
    expect(mocks.createRecord).toHaveBeenCalledWith("ledger-1", { title: "新标题", amount: 25 });

    await wrapper.findAll('[data-testid="record-edit"]')[0].trigger("click");
    await wrapper.get('[data-testid="record-field-title"]').setValue("已更新");
    await wrapper.get('[data-testid="record-editor"]').trigger("submit");
    await flushPromises();
    expect(mocks.updateRecord).toHaveBeenCalledWith(
      "ledger-1", "record-1", { title: "已更新", amount: 10 }, 3,
    );

    await wrapper.findAll('[data-testid="record-delete"]')[0].trigger("click");
    await flushPromises();
    expect(mocks.deleteRecord).toHaveBeenCalledWith("ledger-1", "record-1");
    expect(wrapper.text()).not.toContain("已更新");
    vi.unstubAllGlobals();
  });

  it("opens a tenant-scoped record detail from the list", async () => {
    const row = { id: "record-1", title: "客户甲", amount: 88, version: 2 };
    mocks.getLedger.mockResolvedValue(ledger);
    mocks.listFields.mockResolvedValue({ items: fields, system: [] });
    mocks.listRecords.mockResolvedValue({ items: [row] });
    mocks.getRecord.mockResolvedValue(row);

    const { wrapper, router } = await mountAt("/p3/ledgers/ledger-1", RouterView);
    await wrapper.get('[data-testid="record-detail-link"]').trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.fullPath).toBe("/p3/ledgers/ledger-1/records/record-1");
    expect(mocks.getRecord).toHaveBeenCalledWith("ledger-1", "record-1");
    expect(wrapper.get('[data-testid="record-detail"]').text()).toContain("客户甲");
  });
});
