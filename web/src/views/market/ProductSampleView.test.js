/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  productDefinitions: vi.fn(),
  productLedgerSample: vi.fn(),
  productPrintSample: vi.fn(),
  productReportSample: vi.fn(),
  printMappingContract: vi.fn(),
  productPrintMapping: vi.fn(),
  putProductPrintMapping: vi.fn(),
}));
vi.mock("../../api/client", () => ({
  p4: {
    productDefinitions: mocks.productDefinitions,
    productLedgerSample: mocks.productLedgerSample,
    productPrintSample: mocks.productPrintSample,
    productReportSample: mocks.productReportSample,
    printMappingContract: mocks.printMappingContract,
    productPrintMapping: mocks.productPrintMapping,
    putProductPrintMapping: mocks.putProductPrintMapping,
  },
}));

import ProductSampleView from "./ProductSampleView.vue";

async function mountAt(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/product-samples/ledger", component: ProductSampleView, meta: { sample: "ledger" } },
      { path: "/p4/product-samples/print", component: ProductSampleView, meta: { sample: "print" } },
      { path: "/p4/product-samples/print/designer", component: { template: "<div />" } },
      { path: "/p4/product-samples/report", component: ProductSampleView, meta: { sample: "report" } },
      { path: "/p4/product-config-wizard", component: { template: "<div />" } },
      { path: "/p4/product-definitions", component: { template: "<div />" } },
      { path: "/p1/form-templates", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  const wrapper = mount(ProductSampleView, { global: { plugins: [router] } });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

const LEDGER = {
  product_definition_id: "d1",
  product_name: "请假报销",
  source: "FORM_TEMPLATE_VERSION",
  form_template_name: "请假单",
  form_template_version: 2,
  columns: [
    { key: "title", label: "事由", type: "TEXT", required: true },
    { key: "days", label: "天数", type: "NUMBER", required: false },
  ],
  rows: [],
  note: "平台不保存租户业务数据，所以这里没有记录。",
};

describe("product sample ledger (PC8)", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.productDefinitions.mockResolvedValue([
      { id: "d1", code: "leave", name: "请假报销" },
      { id: "d2", code: "crm", name: "CRM" },
    ]);
    mocks.productLedgerSample.mockResolvedValue(LEDGER);
  });

  it("shows the product's form fields as read-only columns and an honest empty row", async () => {
    const wrapper = await mountAt("/p4/product-samples/ledger");
    expect(mocks.productLedgerSample).toHaveBeenCalledWith("d1");
    const headers = wrapper.findAll("[data-testid='sample-ledger-table'] th").map((th) => th.text());
    expect(headers).toHaveLength(2);
    expect(headers[0]).toMatch(/事由\s*\*\s*文本/);
    expect(headers[1]).toMatch(/天数\s*数字/);
    expect(wrapper.get("[data-testid='sample-ledger-col-days']").text()).toContain("数字");
    expect(wrapper.get("[data-testid='sample-ledger-source']").text()).toContain("请假单」v2，共 2 列");
    const empty = wrapper.get("[data-testid='sample-ledger-empty-row']");
    expect(empty.attributes("colspan")).toBe("2");
    expect(empty.text()).toContain("暂无记录");
    expect(wrapper.findAll("tbody tr")).toHaveLength(1);
    expect(wrapper.find("input, textarea, button[type='submit']").exists()).toBe(false);
  });

  it("follows ?definition= and rewrites it when the product changes", async () => {
    const wrapper = await mountAt("/p4/product-samples/ledger?definition=d2");
    expect(mocks.productLedgerSample).toHaveBeenLastCalledWith("d2");
    await wrapper.get("[data-testid='sample-product-select']").setValue("d1");
    await flushPromises();
    expect(mocks.productLedgerSample).toHaveBeenLastCalledWith("d1");
    expect(wrapper.router.currentRoute.value.query.definition).toBe("d1");
    expect(wrapper.get("[data-testid='sample-back-wizard']").attributes("href")).toBe("/p4/product-config-wizard?definition=d1");
  });

  it("says so when the form version has no columns", async () => {
    mocks.productLedgerSample.mockResolvedValue({ ...LEDGER, columns: [], form_template_name: undefined, note: "读不到产品绑定的表单版本，暂无样例列。" });
    const wrapper = await mountAt("/p4/product-samples/ledger");
    expect(wrapper.get("[data-testid='sample-ledger-no-columns']").text()).toContain("读不到产品绑定的表单版本");
    expect(wrapper.find("[data-testid='sample-ledger-table']").exists()).toBe(false);
  });

  it("shows an empty state without products and an error with retry when the sample fails", async () => {
    mocks.productDefinitions.mockResolvedValueOnce([]);
    const empty = await mountAt("/p4/product-samples/ledger");
    expect(empty.get("[data-testid='sample-no-product'] a").attributes("href")).toBe("/p4/product-definitions");
    expect(mocks.productLedgerSample).not.toHaveBeenCalled();

    mocks.productLedgerSample.mockRejectedValueOnce(Object.assign(new Error("sample down"), { traceId: "tr-pc8" }));
    const failing = await mountAt("/p4/product-samples/ledger");
    expect(failing.get("[data-testid='sample-read-error']").text()).toContain("sample down");
    await failing.get("[data-testid='sample-read-error'] button").trigger("click");
    await flushPromises();
    expect(failing.find("[data-testid='sample-ledger-table']").exists()).toBe(true);
  });
});
const PRINT = {
  product_definition_id: "d1",
  product_name: "请假报销",
  source: "BUILTIN",
  template_code: "builtin.approval-form.a4.v1",
  template_name: "内置样例 · 审批单（A4 纵向）",
  paper: "A4",
  orientation: "PORTRAIT",
  title: "请假报销 申请单",
  sections: [
    { key: "header", label: "单据信息", fields: [{ key: "application_no", label: "单号", type: "TEXT" }] },
    { key: "form", label: "申请内容", fields: [{ key: "title", label: "事由", type: "TEXT" }, { key: "reason", label: "说明", type: "TEXTAREA" }] },
    { key: "approval", label: "审批", fields: [{ key: "opinion", label: "审批意见", type: "TEXTAREA" }] },
  ],
  note: "内置样例版式，字段值留空，只用于预览打印效果；不是打印设计器。",
};

const CONTRACT = {
  paper_sizes: [{ code: "A4", label: "A4" }, { code: "A5", label: "A5" }],
  orientations: [{ code: "PORTRAIT", label: "纵向" }, { code: "LANDSCAPE", label: "横向" }],
  builtin_template: {
    slots: [
      { id: "header.application_no", label: "单号" },
      { id: "form.fields", label: "表单字段区" },
      { id: "approval.opinion", label: "审批意见" },
    ],
  },
};

const MAPPING = {
  paper: "A4",
  orientation: "PORTRAIT",
  mappings: [
    { field_key: "application_no", slot_id: "header.application_no" },
    { field_key: "opinion", slot_id: "approval.opinion" },
  ],
};

describe("product sample print (PC10 / CF-R1)", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.productDefinitions.mockResolvedValue([{ id: "d1", code: "leave", name: "请假报销" }]);
    mocks.productPrintSample.mockResolvedValue(PRINT);
    mocks.printMappingContract.mockResolvedValue(CONTRACT);
    mocks.productPrintMapping.mockResolvedValue(MAPPING);
  });

  it("previews the built-in layout on a portrait page with blank values", async () => {
    const wrapper = await mountAt("/p4/product-samples/print?definition=d1");
    expect(mocks.productPrintSample).toHaveBeenCalledWith("d1");
    expect(mocks.productLedgerSample).not.toHaveBeenCalled();
    const paper = wrapper.get("[data-testid='sample-print-paper']");
    expect(paper.classes()).toContain("portrait");
    expect(paper.get(".paper-title").text()).toBe("请假报销 申请单");
    expect(wrapper.findAll("[data-testid='sample-print-section-form'] dt").map((dt) => dt.text())).toEqual(["事由", "说明"]);
    expect(wrapper.findAll("dd").every((dd) => dd.text() === "")).toBe(true);
    expect(wrapper.get("[data-testid='sample-print-meta']").text()).toContain("A4 纵向");
    expect(wrapper.get("[data-testid='sample-print-later']").text()).toMatch(/完整打印设计器.*later/);
  });

  it("shows field→slot mapping and paper enum on the same print sample route (CF-R1)", async () => {
    const wrapper = await mountAt("/p4/product-samples/print?definition=d1");
    expect(mocks.printMappingContract).toHaveBeenCalled();
    expect(mocks.productPrintMapping).toHaveBeenCalledWith("d1");
    expect(wrapper.get("[data-testid='sample-print-config']").exists()).toBe(true);
    expect(wrapper.get("[data-testid='sample-print-paper-size']").element.value).toBe("A4");
    expect(wrapper.get("[data-testid='sample-print-slot-application_no']").element.value).toBe("header.application_no");
    expect(wrapper.get("[data-testid='sample-print-mapping-table']").text()).toContain("opinion");
    await wrapper.get("[data-testid='sample-print-paper-size']").setValue("A5");
    await wrapper.get("[data-testid='sample-print-orientation']").setValue("LANDSCAPE");
    expect(wrapper.get("[data-testid='sample-print-meta']").text()).toContain("A5 横向");
    expect(wrapper.get("[data-testid='sample-print-paper']").classes()).toContain("landscape");
  });

  it("marks an empty form section with the S empty shell", async () => {
    mocks.productPrintSample.mockResolvedValue({ ...PRINT, sections: [PRINT.sections[0], { key: "form", label: "申请内容", fields: [] }] });
    const wrapper = await mountAt("/p4/product-samples/print");
    expect(wrapper.get("[data-testid='sample-print-section-empty-form']").text()).toContain("本分区暂无字段");
    expect(wrapper.find("[data-testid='sample-print-section-approval']").exists()).toBe(false);
  });

  it("shows an empty S shell when the print sample has no sections", async () => {
    mocks.productPrintSample.mockResolvedValue({ ...PRINT, sections: [], note: "读不到可套用的表单字段分区。" });
    const wrapper = await mountAt("/p4/product-samples/print");
    expect(wrapper.get("[data-testid='sample-print-no-sections']").text()).toContain("暂无打印分区");
    expect(wrapper.get("[data-testid='sample-print-no-sections'] a").attributes("href")).toBe("/p4/product-definitions");
    expect(wrapper.find("[data-testid='sample-print-paper']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='sample-print-later']").text()).toContain("later");
  });

  it("uses a print-specific error shell with later honesty when the sample fails", async () => {
    mocks.productPrintSample.mockRejectedValueOnce(Object.assign(new Error("print down"), { traceId: "tr-r4" }));
    const wrapper = await mountAt("/p4/product-samples/print");
    const err = wrapper.get("[data-testid='sample-read-error']");
    expect(err.text()).toContain("打印样例暂时无法读取");
    expect(err.text()).toContain("print down");
    expect(err.text()).toContain("不是完整打印设计器");
    await err.get("button").trigger("click");
    await flushPromises();
    expect(wrapper.find("[data-testid='sample-print-paper']").exists()).toBe(true);
  });
});
const REPORT = {
  product_definition_id: "d1",
  product_name: "请假报销",
  as_of: "2026-09-26T12:00:00Z",
  metrics: [
    { key: "form_fields", label: "表单字段", value: 4, note: "字段数。" },
    { key: "bound_skus", label: "绑定 SKU", value: 0, note: "SKU 数。" },
    { key: "active_subscriptions", label: "生效订阅", note: "暂时无法统计。" },
    { key: "installed_tenants", label: "已开通租户", value: null, note: "暂时无法统计。" },
  ],
  trend_available: false,
  trend_note: "没有按时间聚合的统计数据，趋势暂无。",
  note: "只统计平台现有记录的当前数量（跨租户合计，不含租户明细、不含金额）。",
};

describe("product sample report (PC12)", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.productDefinitions.mockResolvedValue([{ id: "d1", code: "leave", name: "请假报销" }]);
    mocks.productReportSample.mockResolvedValue(REPORT);
  });

  it("shows real zeros as 0 and uncountable values as 暂无, never 0", async () => {
    const wrapper = await mountAt("/p4/product-samples/report?definition=d1");
    expect(mocks.productReportSample).toHaveBeenCalledWith("d1");
    const value = (key) => wrapper.get(`[data-testid='sample-report-metric-${key}'] .metric-value`).text();
    expect(value("form_fields")).toBe("4");
    expect(value("bound_skus")).toBe("0");
    expect(value("active_subscriptions")).toBe("暂无");
    expect(value("installed_tenants")).toBe("暂无");
    expect(wrapper.get("[data-testid='sample-report-meta']").text()).toContain("不含金额");
  });

  it("states that there is no trend and draws no chart", async () => {
    const wrapper = await mountAt("/p4/product-samples/report");
    expect(wrapper.get("[data-testid='sample-report-trend']").text()).toContain("趋势暂无");
    expect(wrapper.find("svg, canvas, polyline, [data-testid*='chart']").exists()).toBe(false);
    expect(wrapper.text()).not.toMatch(/同比|环比|增长|%/);
  });
});