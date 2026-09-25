/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FormTemplateEditorView from "./FormTemplateEditorView.vue";
import { p1 } from "../../api/client";

vi.mock("../../api/client", () => ({
  p1: {
    formTemplate: vi.fn(),
    createFormTemplate: vi.fn(),
    updateFormTemplate: vi.fn(),
    validateFormTemplate: vi.fn(),
    publishFormTemplate: vi.fn(),
  },
}));

async function mountAt(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p1/form-templates", component: { template: "<div>list</div>" } },
      { path: "/p1/form-templates/new", name: "form-template-new", component: FormTemplateEditorView },
      { path: "/p1/form-templates/:id", name: "form-template", component: FormTemplateEditorView },
    ],
  });
  await router.push(path);
  const wrapper = mount(FormTemplateEditorView, { global: { plugins: [router] }, attachTo: document.body });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

function transfer() {
  return { setData: vi.fn(), effectAllowed: "", dropEffect: "" };
}

function paletteItem(wrapper, type) {
  return wrapper.get(`[data-testid='palette-item'][data-type='${type}']`);
}

function cardNames(wrapper) {
  return wrapper.findAll("[data-testid='template-field'] .field-name").map((node) => node.text().replace("*", ""));
}

async function dragPaletteOnto(wrapper, type, target) {
  await paletteItem(wrapper, type).trigger("dragstart", { dataTransfer: transfer() });
  await target.trigger("dragover", { dataTransfer: transfer() });
  await target.trigger("drop", { dataTransfer: transfer() });
}

const LEAVE = {
  id: "tpl-1",
  code: "leave",
  name: "请假",
  description: "说明",
  status: "DRAFT",
  fields: [
    { key: "reason", label: "事由", type: "TEXT", required: true, options: null },
    { key: "days", label: "天数", type: "NUMBER", required: false, options: null },
  ],
};

describe("form template designer", () => {
  beforeEach(() => {
    for (const fn of Object.values(p1)) fn.mockReset();
  });

  it("shows the palette, an empty drop zone, and keeps validate and publish disabled", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    expect(wrapper.findAll("[data-testid='palette-item']")).toHaveLength(8);
    expect(wrapper.get("[data-testid='field-palette']").text()).toContain("业务");
    expect(wrapper.get("[data-testid='template-fields-empty']").text()).toContain("暂无字段");
    expect(wrapper.get("[data-testid='template-preview-empty']").text()).toBe("暂无字段");
    expect(wrapper.get("[data-testid='template-validate']").attributes("disabled")).toBeDefined();
    expect(wrapper.get("[data-testid='template-publish']").attributes("disabled")).toBeDefined();
    expect(p1.formTemplate).not.toHaveBeenCalled();
  });

  it("drops a palette type into the empty canvas with a valid key and opens its properties", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    await dragPaletteOnto(wrapper, "SELECT", wrapper.get("[data-testid='template-fields']"));
    const cards = wrapper.findAll("[data-testid='template-field']");
    expect(cards).toHaveLength(1);
    expect(cards[0].classes()).toContain("selected");
    expect(cards[0].text()).toContain("choice_1");
    expect(wrapper.get("[data-testid='tab-field']").classes()).toContain("active");
    expect(wrapper.get("[data-testid='field-key']").element.value).toBe("choice_1");
    expect(wrapper.findAll("[data-testid='option-label']")).toHaveLength(1);
    expect(wrapper.find("[data-testid='drop-indicator']").exists()).toBe(false);
  });

  it("inserts a dragged type after the card it is dropped on and shows the drop indicator while dragging", async () => {
    p1.formTemplate.mockResolvedValue(LEAVE);
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    const first = wrapper.findAll("[data-testid='template-field']")[0];
    await paletteItem(wrapper, "DATE").trigger("dragstart", { dataTransfer: transfer() });
    await first.trigger("dragover", { dataTransfer: transfer() });
    expect(wrapper.findAll("[data-testid='drop-indicator']")).toHaveLength(1);
    await first.trigger("drop", { dataTransfer: transfer() });
    expect(cardNames(wrapper)).toEqual(["事由", "日期", "天数"]);
    expect(wrapper.find("[data-testid='drop-indicator']").exists()).toBe(false);
  });

  it("reorders cards by dragging within the list and saves the new order", async () => {
    p1.formTemplate.mockResolvedValue(LEAVE);
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    const [first, second] = wrapper.findAll("[data-testid='template-field']");
    await first.trigger("dragstart", { dataTransfer: transfer() });
    expect(first.classes()).toContain("dragging");
    await second.trigger("dragover", { dataTransfer: transfer() });
    await second.trigger("drop", { dataTransfer: transfer() });
    expect(cardNames(wrapper)).toEqual(["天数", "事由"]);
    expect(wrapper.get("[data-testid='template-dirty']").text()).toContain("未保存");

    p1.updateFormTemplate.mockResolvedValue({ ...LEAVE, fields: [LEAVE.fields[1], LEAVE.fields[0]] });
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    expect(p1.updateFormTemplate.mock.calls[0][1].fields.map((field) => field.key)).toEqual(["days", "reason"]);
    expect(wrapper.find("[data-testid='template-dirty']").exists()).toBe(false);
  });

  it("appends a type on click and moves cards with the keyboard-reachable buttons", async () => {
    p1.formTemplate.mockResolvedValue(LEAVE);
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    await paletteItem(wrapper, "PERSON").trigger("click");
    expect(cardNames(wrapper)).toEqual(["事由", "天数", "成员选择"]);
    await wrapper.findAll("[data-testid='move-up']")[2].trigger("click");
    expect(cardNames(wrapper)).toEqual(["事由", "成员选择", "天数"]);
    await wrapper.findAll("[data-testid='move-down']")[0].trigger("click");
    expect(cardNames(wrapper)).toEqual(["成员选择", "事由", "天数"]);
    expect(wrapper.findAll("[data-testid='move-up']")[0].attributes("disabled")).toBeDefined();
    await wrapper.findAll("[data-testid='remove-field']")[1].trigger("click");
    expect(cardNames(wrapper)).toEqual(["成员选择", "天数"]);
  });

  it("edits the selected field in the property panel and mirrors it in the canvas and preview", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    await wrapper.get("[data-testid='add-field']").trigger("click");
    await wrapper.get("[data-testid='field-label']").setValue("类别");
    await wrapper.get("[data-testid='field-key']").setValue("category");
    await wrapper.get("[data-testid='field-type']").setValue("SELECT");
    await wrapper.get("[data-testid='add-option']").trigger("click");
    await wrapper.get("[data-testid='option-label']").setValue("差旅");
    await wrapper.get("[data-testid='option-value']").setValue("travel");
    await wrapper.get("[data-testid='field-required']").setValue(true);
    const card = wrapper.get("[data-testid='template-field']");
    expect(card.text()).toContain("类别");
    expect(card.text()).toContain("category");
    await wrapper.get("[data-testid='tab-preview']").trigger("click");
    const preview = wrapper.get("[data-testid='template-preview']");
    expect(preview.text()).toContain("类别");
    expect(preview.text()).toContain("差旅");
    expect(preview.get("fieldset").attributes("disabled")).toBeDefined();
    await wrapper.get("[data-testid='preview-device-mobile']").trigger("click");
    expect(preview.get(".frame").attributes("data-device")).toBe("mobile");
    expect(p1.createFormTemplate).not.toHaveBeenCalled();
  });

  it("saves the ordered field list without internal ids and keeps the deep link", async () => {
    p1.formTemplate.mockResolvedValue({ ...LEAVE, fields: [LEAVE.fields[0]] });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    expect(wrapper.get("[data-testid='tab-preview']").classes()).toContain("active");
    expect(wrapper.get("[data-testid='template-code']").element.value).toBe("leave");
    expect(wrapper.get("[data-testid='preview-field']").text()).toContain("事由");
    await wrapper.get("[data-testid='add-field']").trigger("click");
    await wrapper.get("[data-testid='field-label']").setValue("类别");
    await wrapper.get("[data-testid='field-key']").setValue("category");
    await wrapper.get("[data-testid='field-type']").setValue("SELECT");
    await wrapper.get("[data-testid='add-option']").trigger("click");
    await wrapper.get("[data-testid='option-label']").setValue("年假");
    await wrapper.get("[data-testid='option-value']").setValue("annual");
    p1.updateFormTemplate.mockResolvedValue({
      ...LEAVE,
      fields: [LEAVE.fields[0], { key: "category", label: "类别", type: "SELECT", required: false, options: [{ label: "年假", value: "annual" }] }],
    });
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    expect(p1.updateFormTemplate).toHaveBeenCalledWith("tpl-1", {
      code: "leave",
      name: "请假",
      description: "说明",
      fields: [
        { key: "reason", label: "事由", type: "TEXT", required: true, options: null },
        { key: "category", label: "类别", type: "SELECT", required: false, options: [{ label: "年假", value: "annual" }] },
      ],
    });
    expect(wrapper.get("[data-testid='field-key']").element.value).toBe("category");
    expect(wrapper.router.currentRoute.value.fullPath).toBe("/p1/form-templates/tpl-1");
  });

  it("shows validate errors on the canvas cards and form-level errors above the designer", async () => {
    p1.formTemplate.mockResolvedValue({
      id: "tpl-1",
      code: "leave",
      name: "请假",
      status: "DRAFT",
      fields: [
        { key: "Reason", label: "事由", type: "TEXT", required: false, options: null },
        { key: "category", label: "类别", type: "SELECT", required: false, options: [] },
      ],
    });
    p1.validateFormTemplate.mockResolvedValueOnce({
      templateId: "tpl-1",
      valid: false,
      status: "DRAFT",
      errors: [
        { fieldKey: "Reason", code: "field.key.invalid", message: "字段 key 须以小写字母开头，只能包含小写字母、数字和下划线" },
        { fieldKey: "category", code: "field.options.required", message: "单选字段至少需要一个选项" },
        { fieldKey: null, code: "fields.required", message: "至少需要一个字段" },
      ],
    });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    await wrapper.get("[data-testid='template-validate']").trigger("click");
    await flushPromises();
    expect(wrapper.findAll("[data-testid='field-error']").map((node) => node.text())).toEqual([
      "字段 key 须以小写字母开头，只能包含小写字母、数字和下划线",
      "单选字段至少需要一个选项",
    ]);
    expect(wrapper.findAll("[data-testid='template-field']")[0].classes()).toContain("invalid");
    await wrapper.findAll("[data-testid='template-field']")[1].trigger("click");
    expect(wrapper.get("[data-testid='field-prop-error']").text()).toBe("单选字段至少需要一个选项");
    expect(wrapper.get("[data-testid='template-validate-errors']").text()).toContain("至少需要一个字段");
    expect(p1.validateFormTemplate).toHaveBeenCalledWith("tpl-1");

    p1.validateFormTemplate.mockResolvedValueOnce({ templateId: "tpl-1", valid: true, status: "VALIDATED", errors: [] });
    await wrapper.get("[data-testid='template-validate']").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='template-validate-ok']").text()).toBe("校验通过");
    expect(wrapper.get("[data-testid='template-status']").text()).toContain("已校验");
    expect(wrapper.find("[data-testid='field-error']").exists()).toBe(false);
  });

  it("confirms publish, shows the version, and keeps the server failure text", async () => {
    p1.formTemplate.mockResolvedValue({ ...LEAVE, status: "VALIDATED", fields: [LEAVE.fields[0]] });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    await wrapper.get("[data-testid='template-publish']").trigger("click");
    expect(wrapper.get("[data-testid='template-publish-confirm']").text()).toContain("确认发布");
    expect(p1.publishFormTemplate).not.toHaveBeenCalled();
    await wrapper.get("[data-testid='template-publish-cancel']").trigger("click");
    expect(wrapper.find("[data-testid='template-publish-confirm']").exists()).toBe(false);

    p1.publishFormTemplate.mockRejectedValueOnce(new Error("publish requires VALIDATED template status"));
    await wrapper.get("[data-testid='template-publish']").trigger("click");
    await wrapper.get("[data-testid='template-publish-submit']").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='template-publish-error']").text()).toBe("publish requires VALIDATED template status");

    p1.publishFormTemplate.mockResolvedValueOnce({ templateId: "tpl-1", versionId: "ver-1", version: 2 });
    await wrapper.get("[data-testid='template-publish']").trigger("click");
    await wrapper.get("[data-testid='template-publish-submit']").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='template-publish-version']").text()).toBe("已发布版本 2");
    expect(wrapper.get("[data-testid='template-next-bind']").attributes("href")).toBe("/p4/product-definitions");
    expect(wrapper.get("[data-testid='template-status']").text()).toContain("已发布");
    expect(wrapper.router.currentRoute.value.fullPath).toBe("/p1/form-templates/tpl-1");
  });

  it("shows the server error text", async () => {
    p1.formTemplate.mockRejectedValue(Object.assign(new Error("form template not found"), { traceId: "trace-9" }));
    const wrapper = await mountAt("/p1/form-templates/missing");
    expect(wrapper.text()).toContain("form template not found");
    expect(wrapper.text()).toContain("trace-9");
  });
});
