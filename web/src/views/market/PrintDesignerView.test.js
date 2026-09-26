/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  productDefinitions: vi.fn(),
  productPrintSample: vi.fn(),
  printMappingContract: vi.fn(),
  productPrintMapping: vi.fn(),
  saveProductPrintMapping: vi.fn(),
}));
vi.mock("../../api/client", () => ({ p4: mocks }));

import PrintDesignerView from "./PrintDesignerView.vue";
import { bindingRows, designerFromSample, mappingsFromRows, sameMappings, validateBindings } from "./printDesigner";

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
    { key: "form", label: "申请内容", fields: [{ key: "title", label: "事由", type: "TEXT" }, { key: "days", label: "天数", type: "NUMBER" }] },
    { key: "approval", label: "审批", fields: [{ key: "opinion", label: "审批意见", type: "TEXTAREA" }] },
  ],
  note: "内置样例版式。",
};

const CONTRACT = {
  contract_version: "1.0",
  builtin_template: {
    code: "builtin.approval-form.a4.v1",
    slots: [
      { id: "header.application_no", label: "单号", section: "header", accepts: ["TEXT"], repeatable: false },
      { id: "header.applicant", label: "申请人", section: "header", accepts: ["PERSON", "TEXT"], repeatable: false },
      { id: "form.fields", label: "表单字段区", section: "form", accepts: ["*"], repeatable: true },
      { id: "approval.opinion", label: "审批意见", section: "approval", accepts: ["TEXTAREA", "TEXT"], repeatable: false },
    ],
  },
  mapping_rules: { one_field_one_slot: true, unmapped_spill_slot_id: "form.fields" },
};

const DEFAULT_MAPPING = {
  product_definition_id: "d1",
  source: "DEFAULT",
  paper: "A4",
  orientation: "PORTRAIT",
  mappings: [
    { field_key: "application_no", slot_id: "header.application_no" },
    { field_key: "opinion", slot_id: "approval.opinion" },
  ],
};

async function mountAt(path = "/p4/product-samples/print/designer") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/product-samples/print/designer", component: PrintDesignerView },
      { path: "/p4/product-samples/print", component: { template: "<div />" } },
      { path: "/p4/product-config-wizard", component: { template: "<div />" } },
      { path: "/p4/product-definitions", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  const wrapper = mount(PrintDesignerView, { global: { plugins: [router] } });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

describe("print designer model (CF-RD1)", () => {
  it("turns sample sections into slots and keeps system slots apart from form fields", () => {
    const designer = designerFromSample(PRINT);
    expect(designer.sections.map((s) => [s.key, s.kind])).toEqual([["header", "system"], ["form", "form"], ["approval", "system"]]);
    expect(designer.sections[1].slots.map((s) => s.id)).toEqual(["form.title", "form.days"]);
    expect(designer.formFields.map((f) => f.key)).toEqual(["title", "days"]);
    expect(designerFromSample(null).sections).toEqual([]);
  });
});

describe("print designer shell (CF-RD1)", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.productDefinitions.mockResolvedValue([{ id: "d1", code: "leave", name: "请假报销" }, { id: "d2", code: "crm", name: "CRM" }]);
    mocks.productPrintSample.mockResolvedValue(PRINT);
    mocks.printMappingContract.mockResolvedValue(CONTRACT);
    mocks.productPrintMapping.mockResolvedValue(DEFAULT_MAPPING);
  });

  it("shows the slot list, board zones and properties for the selected slot", async () => {
    const wrapper = await mountAt("/p4/product-samples/print/designer?definition=d1");
    expect(mocks.productPrintSample).toHaveBeenCalledWith("d1");
    expect(wrapper.findAll("[data-testid^='designer-zone-']").map((z) => z.get("h4").text())).toEqual(["单据信息", "申请内容", "审批"]);
    expect(wrapper.findAll("[data-testid='designer-slot-list'] .slot-item")).toHaveLength(4);
    expect(wrapper.get("[data-testid='designer-props-detail']").text()).toContain("单号");
    await wrapper.get("[data-testid='designer-board-form.days']").trigger("click");
    const props = wrapper.get("[data-testid='designer-props-detail']").text();
    expect(props).toContain("天数");
    expect(props).toContain("申请内容");
    expect(props).toContain("产品表单字段");
    expect(wrapper.get("[data-testid='designer-slot-form.days']").attributes("aria-pressed")).toBe("true");
    expect(wrapper.get("[data-testid='designer-template']").text()).toContain("A4 纵向");
    expect(wrapper.find("[draggable='true']").exists()).toBe(false);
  });

  it("follows ?definition= and links back to the print sample of the same product", async () => {
    const wrapper = await mountAt("/p4/product-samples/print/designer?definition=d2");
    expect(mocks.productPrintSample).toHaveBeenLastCalledWith("d2");
    await wrapper.get("[data-testid='designer-product-select']").setValue("d1");
    await flushPromises();
    expect(wrapper.router.currentRoute.value.query.definition).toBe("d1");
    expect(wrapper.get("[data-testid='designer-back-sample']").attributes("href")).toBe("/p4/product-samples/print?definition=d1");
  });

  it("has honest empty and error states", async () => {
    mocks.productPrintSample.mockResolvedValueOnce({ ...PRINT, sections: [] });
    const empty = await mountAt();
    expect(empty.get("[data-testid='designer-no-slots']").text()).toContain("暂无打印槽位");

    mocks.productPrintSample.mockRejectedValueOnce(Object.assign(new Error("print down"), { traceId: "tr-rd1" }));
    const failing = await mountAt();
    expect(failing.get("[data-testid='designer-read-error']").text()).toContain("print down");
    await failing.get("[data-testid='designer-read-error'] button").trigger("click");
    await flushPromises();
    expect(failing.find("[data-testid='designer-stage']").exists()).toBe(true);

    mocks.productDefinitions.mockResolvedValueOnce([]);
    const none = await mountAt();
    expect(none.get("[data-testid='designer-no-product'] a").attributes("href")).toBe("/p4/product-definitions");
  });
});
describe("field → slot binding model (CF-RD2)", () => {
  const designer = designerFromSample(PRINT);

  it("reads explicit mappings and puts unmapped form fields in the spill slot", () => {
    const rows = bindingRows(designer, CONTRACT, DEFAULT_MAPPING);
    expect(rows.map((r) => [r.fieldKey, r.slotId])).toEqual([
      ["application_no", "header.application_no"],
      ["title", "form.fields"],
      ["days", "form.fields"],
      ["opinion", "approval.opinion"],
    ]);
    expect(sameMappings(mappingsFromRows(rows), [...mappingsFromRows(rows)].reverse())).toBe(true);
  });

  it("rejects type mismatches and two fields in a single-use slot", () => {
    const rows = bindingRows(designer, CONTRACT, DEFAULT_MAPPING);
    rows[1].slotId = "header.application_no"; // title (TEXT) joins application_no in a single slot
    rows[2].slotId = "header.applicant"; // days is NUMBER; applicant accepts PERSON/TEXT
    const errors = validateBindings(rows, CONTRACT);
    expect(errors.application_no).toContain("只能放一个字段");
    expect(errors.title).toContain("只能放一个字段");
    expect(errors.days).toContain("不接受数字字段");
    expect(errors.opinion).toBeUndefined();
  });
});

describe("field → slot binding UI (CF-RD2)", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.productDefinitions.mockResolvedValue([{ id: "d1", code: "leave", name: "请假报销" }]);
    mocks.productPrintSample.mockResolvedValue(PRINT);
    mocks.printMappingContract.mockResolvedValue(CONTRACT);
    mocks.productPrintMapping.mockResolvedValue(DEFAULT_MAPPING);
  });

  it("reads the product mapping back into the table and offers only slots that accept the field type", async () => {
    const wrapper = await mountAt("/p4/product-samples/print/designer?definition=d1");
    expect(mocks.productPrintMapping).toHaveBeenCalledWith("d1");
    expect(wrapper.get("[data-testid='designer-binding-source']").text()).toContain("契约默认映射");
    expect(wrapper.get("[data-testid='designer-binding-title']").element.value).toBe("form.fields");
    const dayOptions = wrapper.findAll("[data-testid='designer-binding-days'] option").map((o) => o.attributes("value"));
    expect(dayOptions).toEqual(["", "form.fields"]);
    expect(wrapper.get("[data-testid='designer-binding-save']").attributes("disabled")).toBeDefined();
  });

  it("saves the draft with PUT and reports the read-back", async () => {
    mocks.saveProductPrintMapping.mockResolvedValue({});
    const saved = {
      ...DEFAULT_MAPPING,
      source: "IN_MEMORY",
      mappings: [...DEFAULT_MAPPING.mappings, { field_key: "title", slot_id: "form.fields" }, { field_key: "days", slot_id: "form.fields" }],
    };
    const wrapper = await mountAt();
    await wrapper.get("[data-testid='designer-binding-opinion']").setValue("");
    mocks.productPrintMapping.mockResolvedValueOnce({ ...saved, mappings: saved.mappings.filter((m) => m.field_key !== "opinion") });
    await wrapper.get("[data-testid='designer-binding-save']").trigger("click");
    await flushPromises();
    const [id, body] = mocks.saveProductPrintMapping.mock.calls[0];
    expect(id).toBe("d1");
    expect(body.paper).toBe("A4");
    expect(body.mappings).toContainEqual({ field_key: "title", slot_id: "form.fields" });
    expect(body.mappings.find((m) => m.field_key === "opinion")).toBeUndefined();
    expect(wrapper.get("[data-testid='designer-binding-readback']").text()).toContain("读回，一致");
    expect(wrapper.get("[data-testid='designer-binding-source']").text()).toContain("进程内映射");
  });

  it("blocks saving while a slot is invalid and shows the reason on the field", async () => {
    const wrapper = await mountAt();
    await wrapper.get("[data-testid='designer-binding-title']").setValue("header.application_no");
    expect(wrapper.get("[data-testid='designer-binding-error-title']").text()).toContain("只能放一个字段");
    expect(wrapper.get("[data-testid='designer-binding-title']").attributes("aria-invalid")).toBe("true");
    expect(wrapper.get("[data-testid='designer-binding-save']").attributes("disabled")).toBeDefined();
    await wrapper.get("[data-testid='designer-binding-reset']").trigger("click");
    expect(wrapper.find("[data-testid='designer-binding-error-title']").exists()).toBe(false);
  });

  it("keeps the designer usable when the mapping cannot be read", async () => {
    mocks.productPrintMapping.mockRejectedValueOnce(new Error("mapping down"));
    const wrapper = await mountAt();
    expect(wrapper.find("[data-testid='designer-stage']").exists()).toBe(true);
    expect(wrapper.get("[data-testid='designer-binding-error']").text()).toContain("mapping down");
    await wrapper.get("[data-testid='designer-binding-error'] button").trigger("click");
    await flushPromises();
    expect(wrapper.find("[data-testid='designer-binding-table']").exists()).toBe(true);
  });
});