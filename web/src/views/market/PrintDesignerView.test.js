/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  productDefinitions: vi.fn(),
  productPrintSample: vi.fn(),
}));
vi.mock("../../api/client", () => ({
  p4: { productDefinitions: mocks.productDefinitions, productPrintSample: mocks.productPrintSample },
}));

import PrintDesignerView from "./PrintDesignerView.vue";
import { designerFromSample } from "./printDesigner";

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