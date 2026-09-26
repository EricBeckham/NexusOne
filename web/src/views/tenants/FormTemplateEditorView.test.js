/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FormTemplateEditorView from "./FormTemplateEditorView.vue";
import { p1 } from "../../api/client";
import { nxMessage } from "../../feedback/nxMessage";

vi.mock("../../feedback/nxMessage", () => ({ nxMessage: { success: vi.fn(), error: vi.fn() } }));

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
  running_version: 2,
  fields: [
    { key: "reason", label: "事由", type: "TEXT", required: true, options: null },
    { key: "days", label: "天数", type: "NUMBER", required: false, options: null },
  ],
};

describe("form template designer", () => {
  beforeEach(() => {
    for (const fn of Object.values(p1)) fn.mockReset();
    nxMessage.success.mockReset();
    nxMessage.error.mockReset();
  });

  it("uses short shared Toast feedback for save and keeps trace details separate", async () => {
    p1.formTemplate.mockResolvedValue(LEAVE);
    p1.updateFormTemplate.mockResolvedValue(LEAVE);
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    expect(nxMessage.success).toHaveBeenCalledWith("草稿已保存");
    p1.updateFormTemplate.mockRejectedValue(new Error("internal stack must not become a toast"));
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    expect(nxMessage.error).toHaveBeenCalledWith("草稿未保存，修改已保留", { traceId: "" });
  });

  it("shows the palette, an empty drop zone, and keeps validate and publish disabled", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    expect(wrapper.findAll(".template-editor-page .primary")).toHaveLength(1);
    expect(wrapper.get(".editor-actions [data-testid='template-save']").text()).toBe("保存草稿");
    expect(wrapper.get("[data-testid='template-fields'] h2").text()).toBe("字段顺序");
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
    expect(wrapper.get("[data-testid='field-label']").element.closest(".nx-field")).not.toBeNull();
    expect(wrapper.get("[data-testid='field-label']").element.closest(".nx-field").querySelector(".nx-req")).not.toBeNull();
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

  it("shows publish status and running version on the editor status bar (CF-T3)", async () => {
    p1.formTemplate.mockResolvedValue({ ...LEAVE });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    const bar = wrapper.get("[data-testid='template-editor-statusbar']");
    expect(bar.get("[data-testid='template-status']").text()).toContain("发布态：草稿");
    expect(bar.get("[data-testid='template-running-version']").text()).toContain("运行版本 v2");
    expect(bar.text()).not.toContain("针对已保存的草稿");
  });

  it("opens a read-only applicant preview drawer without a second editor (CF-F2)", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    await wrapper.get("[data-testid='add-field']").trigger("click");
    await wrapper.get("[data-testid='field-label']").setValue("事由");
    await wrapper.get("[data-testid='applicant-preview-open']").trigger("click");
    const drawer = wrapper.get("[data-testid='applicant-preview-drawer']");
    expect(drawer.text()).toContain("申请人预览");
    expect(drawer.text()).toContain("此处不能编辑");
    expect(wrapper.get("[data-testid='applicant-preview-form']").attributes("disabled")).toBeDefined();
    expect(wrapper.get("[data-testid='applicant-preview-field']").text()).toContain("事由");
    expect(drawer.find("[data-testid='field-label']").exists()).toBe(false);
    expect(drawer.find("[data-testid='add-field']").exists()).toBe(false);
    expect(drawer.find("[data-testid='remove-selected']").exists()).toBe(false);
    await wrapper.get("[data-testid='applicant-preview-close']").trigger("click");
    expect(wrapper.find("[data-testid='applicant-preview-drawer']").exists()).toBe(false);
  });

  it("edits the selected field in the property panel and mirrors it in the canvas and preview", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    await wrapper.get("[data-testid='add-field']").trigger("click");
    expect(wrapper.get("[data-testid='nx-property-tab-basic']").classes()).toContain("active");
    await wrapper.get("[data-testid='field-label']").setValue("类别");
    await wrapper.get("[data-testid='field-key']").setValue("category");
    await wrapper.get("[data-testid='field-type']").setValue("SELECT");
    await wrapper.get("[data-testid='add-option']").trigger("click");
    await wrapper.get("[data-testid='option-label']").setValue("差旅");
    await wrapper.get("[data-testid='option-value']").setValue("travel");
    await wrapper.get("[data-testid='nx-property-tab-validation']").trigger("click");
    await wrapper.get("[data-testid='field-required']").setValue(true);
    await wrapper.get("[data-testid='nx-property-tab-appearance']").trigger("click");
    expect(wrapper.get("[data-testid='field-appearance-hint']").text()).toContain("画布拖拽");
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
    await wrapper.get("[data-testid='nx-property-tab-validation']").trigger("click");
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

  it("pins field errors when the validate API returns only snake_case field_key (N6)", async () => {
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
      template_id: "tpl-1",
      valid: false,
      status: "DRAFT",
      errors: [
        { field_id: "f1", field_key: "Reason", code: "field.key.invalid", message: "字段 key 须以小写字母开头，只能包含小写字母、数字和下划线" },
        { field_key: "category", code: "field.options.required", message: "单选字段至少需要一个选项" },
        { field_key: null, code: "fields.required", message: "至少需要一个字段" },
      ],
    });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    await wrapper.get("[data-testid='template-validate']").trigger("click");
    await flushPromises();
    expect(wrapper.findAll("[data-testid='field-error']").map((node) => node.text())).toEqual([
      "字段 key 须以小写字母开头，只能包含小写字母、数字和下划线",
      "单选字段至少需要一个选项",
    ]);
    expect(wrapper.get("[data-testid='template-validate-errors']").text()).toContain("至少需要一个字段");
  });

  it("confirms publish, shows the version, and keeps the server failure text", async () => {
    p1.formTemplate.mockResolvedValue({ ...LEAVE, status: "VALIDATED", fields: [LEAVE.fields[0]] });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    await wrapper.get("[data-testid='template-publish']").trigger("click");
    expect(wrapper.get("[data-testid='template-publish-confirm']").text()).toContain("确认发布");
    expect(p1.publishFormTemplate).not.toHaveBeenCalled();
    await wrapper.get("[data-testid='template-publish-cancel']").trigger("click");
    expect(wrapper.find("[data-testid='template-publish-confirm']").exists()).toBe(false);

    p1.publishFormTemplate.mockRejectedValueOnce(Object.assign(new Error("publish requires VALIDATED template status"), { status: 409 }));
    await wrapper.get("[data-testid='template-publish']").trigger("click");
    await wrapper.get("[data-testid='template-publish-submit']").trigger("click");
    await flushPromises();
    const failure = wrapper.get("[data-testid='template-action-failure']");
    expect(failure.text()).toContain("发布失败");
    expect(failure.get("[data-testid='nx-request-alert-hint']").text()).toContain("先保存并通过校验");
    expect(failure.get("[data-testid='nx-request-alert-server']").text()).toContain("publish requires VALIDATED template status");
    expect(failure.find("[data-testid='nx-request-alert-retry']").exists()).toBe(false);
    expect(wrapper.find("[data-testid='nx-result']").exists()).toBe(false);

    p1.publishFormTemplate.mockResolvedValueOnce({ templateId: "tpl-1", versionId: "ver-1", version: 2 });
    await wrapper.get("[data-testid='template-publish']").trigger("click");
    await wrapper.get("[data-testid='template-publish-submit']").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='template-publish-version']").text()).toBe("已发布版本 2");
    expect(wrapper.get("[data-testid='nx-result-title']").text()).toBe("模板已发布");
    expect(wrapper.get("[data-testid='nx-result-next']").text()).toContain("下一步：绑定到产品");
    expect(wrapper.get("[data-testid='template-next-bind']").attributes("href")).toBe("/p4/product-definitions");
    expect(wrapper.get("[data-testid='template-status']").text()).toContain("已发布");
    expect(nxMessage.success).toHaveBeenCalledWith("模板已发布");
    expect(wrapper.router.currentRoute.value.fullPath).toBe("/p1/form-templates/tpl-1");
  });

  it("keeps the canvas on an opaque save failure, shows the trace, and retries the save", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    await wrapper.get("[data-testid='template-code']").setValue("leave");
    await wrapper.get("[data-testid='template-name']").setValue("请假");
    await paletteItem(wrapper, "TEXT").trigger("click");
    p1.createFormTemplate.mockRejectedValueOnce(Object.assign(new Error("Internal error"), {
      status: 500, code: "CORE.API.0099", traceId: "trace-h11",
    }));
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    const failure = wrapper.get("[data-testid='template-action-failure']");
    expect(failure.text()).toContain("草稿未保存，修改已保留");
    expect(failure.get("[data-testid='nx-request-alert-trace']").text()).toContain("trace-h11");
    expect(wrapper.text()).not.toContain("Internal error");
    expect(wrapper.find("[data-testid='nx-error-state']").exists()).toBe(false);
    expect(wrapper.findAll("[data-testid='template-field']")).toHaveLength(1);

    p1.createFormTemplate.mockResolvedValueOnce({ id: "tpl-new", code: "leave", name: "请假", status: "DRAFT", fields: [
      { key: "text_1", label: "文本", type: "TEXT", required: false, options: null },
    ] });
    await failure.get("[data-testid='nx-request-alert-retry']").trigger("click");
    await flushPromises();
    expect(p1.createFormTemplate).toHaveBeenCalledTimes(2);
    expect(wrapper.find("[data-testid='template-action-failure']").exists()).toBe(false);
    expect(wrapper.router.currentRoute.value.fullPath).toBe("/p1/form-templates/tpl-new");
  });

  it("shows the I9 audit tenant_id failure explicitly and a duplicate code as a readable conflict", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    await wrapper.get("[data-testid='template-code']").setValue("i9_platform_smoke");
    await wrapper.get("[data-testid='template-name']").setValue("I9 平台配置复验模板");
    await paletteItem(wrapper, "TEXT").trigger("click");
    p1.createFormTemplate.mockRejectedValueOnce(Object.assign(
      new Error("not-null constraint violated: platform.audit_event.tenant_id"),
      { status: 500, code: "CORE.API.0006", traceId: "01a0d885-ac82-7f70-aff3-b24eb39859ae" },
    ));
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    let failure = wrapper.get("[data-testid='template-action-failure']");
    expect(failure.get("[data-testid='nx-request-alert-hint']").text()).toContain("本次操作未完成，修改已保留");
    expect(failure.get("[data-testid='nx-request-alert-server']").text()).toContain("platform.audit_event.tenant_id");
    expect(failure.get("[data-testid='nx-request-alert-server']").text()).toContain("CORE.API.0006");
    expect(failure.get("[data-testid='nx-request-alert-trace']").text()).toContain("01a0d885-ac82-7f70-aff3-b24eb39859ae");
    expect(failure.find("[data-testid='nx-request-alert-retry']").exists()).toBe(true);
    expect(wrapper.text()).not.toContain("Internal error");
    expect(wrapper.findAll("[data-testid='template-field']")).toHaveLength(1);

    p1.createFormTemplate.mockRejectedValueOnce(Object.assign(
      new Error("unique constraint violated: form_template_code_key"), { status: 409, code: "CORE.API.0003" },
    ));
    await failure.get("[data-testid='nx-request-alert-retry']").trigger("click");
    await flushPromises();
    failure = wrapper.get("[data-testid='template-action-failure']");
    expect(failure.get("[data-testid='nx-request-alert-hint']").text()).toContain("模板编码已被其他模板使用");
    expect(failure.get("[data-testid='nx-request-alert-server']").text()).toContain("form_template_code_key");
    expect(failure.find("[data-testid='nx-request-alert-retry']").exists()).toBe(false);
  });

  it("explains a missing-fields save error and retries a failed validate", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    await wrapper.get("[data-testid='template-code']").setValue("leave");
    await wrapper.get("[data-testid='template-name']").setValue("请假");
    p1.createFormTemplate.mockRejectedValueOnce(Object.assign(new Error("code, name, and fields are required"), { status: 400, code: "CORE.API.0001" }));
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='nx-request-alert-hint']").text()).toContain("填写编码和名称");
    expect(wrapper.get("[data-testid='nx-request-alert-server']").text()).toContain("code, name, and fields are required");
    wrapper.unmount();

    p1.formTemplate.mockResolvedValue({ ...LEAVE });
    const editing = await mountAt("/p1/form-templates/tpl-1");
    p1.validateFormTemplate.mockRejectedValueOnce(Object.assign(new Error("Internal error"), { status: 500, traceId: "trace-v" }));
    await editing.get("[data-testid='template-validate']").trigger("click");
    await flushPromises();
    expect(editing.get("[data-testid='template-action-failure']").text()).toContain("校验请求失败");
    p1.validateFormTemplate.mockResolvedValueOnce({ templateId: "tpl-1", valid: true, status: "VALIDATED", errors: [] });
    await editing.get("[data-testid='nx-request-alert-retry']").trigger("click");
    await flushPromises();
    expect(editing.get("[data-testid='template-validate-ok']").text()).toBe("校验通过");
    expect(editing.find("[data-testid='template-action-failure']").exists()).toBe(false);
  });

  it("marks required inputs with a red star and places missing values next to their input before saving (N3)", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    for (const id of ["template-code", "template-name"]) {
      expect(wrapper.get(`[data-testid='${id}']`).element.closest("label").querySelector("em.nx-req").textContent).toBe("*");
    }
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    expect(p1.createFormTemplate).not.toHaveBeenCalled();
    expect(wrapper.get("[data-testid='tab-template']").classes()).toContain("active");
    expect(wrapper.get("[data-testid='template-code-error']").text()).toBe("请填写模板编码");
    expect(wrapper.get("[data-testid='template-name-error']").text()).toBe("请填写模板名称");
    expect(nxMessage.error).not.toHaveBeenCalled();
    expect(wrapper.get("[data-testid='template-code']").attributes("aria-invalid")).toBe("true");
    expect(wrapper.get("[data-testid='template-draft-invalid']").text()).toContain("2 处");

    // Errors clear as the user fixes them; a blank field label then points at the field card and its property.
    await wrapper.get("[data-testid='template-code']").setValue("leave");
    await wrapper.get("[data-testid='template-name']").setValue("请假");
    expect(wrapper.find("[data-testid='template-code-error']").exists()).toBe(false);
    await wrapper.get("[data-testid='add-field']").trigger("click");
    await wrapper.get("[data-testid='field-label']").setValue(" ");
    await wrapper.get("[data-testid='tab-template']").trigger("click");
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    expect(p1.createFormTemplate).not.toHaveBeenCalled();
    expect(wrapper.get("[data-testid='tab-field']").classes()).toContain("active");
    expect(wrapper.get("[data-testid='field-label']").element.closest("label").querySelector("em.nx-req")).not.toBeNull();
    expect(wrapper.get("[data-testid='field-label-error']").text()).toBe("请填写字段名称");
    expect(wrapper.get("[data-testid='template-field']").classes()).toContain("invalid");
    expect(wrapper.get("[data-testid='field-error']").text()).toBe("请填写字段名称");
  });

  it("locks save, validate and publish while any of them is in flight (N3)", async () => {
    p1.formTemplate.mockResolvedValue({ ...LEAVE });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    let finish;
    p1.updateFormTemplate.mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    for (const id of ["template-save", "template-validate", "template-publish"]) {
      expect(wrapper.get(`[data-testid='${id}']`).attributes("disabled")).toBeDefined();
    }
    expect(wrapper.get("[data-testid='template-save']").text()).toBe("保存中…");
    await wrapper.get("[data-testid='template-save']").trigger("click");
    expect(p1.updateFormTemplate).toHaveBeenCalledTimes(1);
    finish({ ...LEAVE });
    await flushPromises();
    expect(wrapper.get("[data-testid='template-validate']").attributes("disabled")).toBeUndefined();

    let validated;
    p1.validateFormTemplate.mockReturnValueOnce(new Promise((resolve) => { validated = resolve; }));
    await wrapper.get("[data-testid='template-validate']").trigger("click");
    expect(wrapper.get("[data-testid='template-save']").attributes("disabled")).toBeDefined();
    expect(wrapper.get("[data-testid='template-publish']").attributes("disabled")).toBeDefined();
    validated({ valid: true, status: "VALIDATED", errors: [] });
    await flushPromises();
    expect(wrapper.get("[data-testid='template-save']").attributes("disabled")).toBeUndefined();
  });
  it("shows the server error text", async () => {
    p1.formTemplate.mockRejectedValue(Object.assign(new Error("form template not found"), { traceId: "trace-9" }));
    const wrapper = await mountAt("/p1/form-templates/missing");
    expect(wrapper.text()).toContain("form template not found");
    expect(wrapper.text()).toContain("trace-9");
  });

  it("switches to free canvas shell and keeps list mode as fallback (Z3)", async () => {
    p1.formTemplate.mockResolvedValue(LEAVE);
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    expect(wrapper.find("[data-testid='template-free-board']").exists()).toBe(false);
    expect(wrapper.find("ol.field-list").exists()).toBe(true);

    await wrapper.get("[data-testid='canvas-mode-free']").trigger("click");
    expect(wrapper.get("[data-testid='template-fields'] h2").text()).toBe("自由画布");
    expect(wrapper.get("[data-testid='template-free-board']").exists()).toBe(true);
    expect(wrapper.find("ol.field-list").exists()).toBe(false);
    const freeCards = wrapper.findAll("[data-testid='template-free-board'] [data-testid='template-field']");
    expect(freeCards).toHaveLength(2);
    expect(freeCards[0].attributes("style")).toMatch(/grid-column/);

    await wrapper.get("[data-testid='canvas-mode-list']").trigger("click");
    expect(wrapper.get("[data-testid='template-fields'] h2").text()).toBe("字段顺序");
    expect(wrapper.find("[data-testid='template-free-board']").exists()).toBe(false);
    expect(wrapper.find("ol.field-list").exists()).toBe(true);
    expect(cardNames(wrapper)).toEqual(["事由", "天数"]);
  });

  it("round-trips layout via options.layout on save and reload (Z4)", async () => {
    p1.formTemplate.mockResolvedValue({
      ...LEAVE,
      fields: [
        {
          key: "reason",
          label: "事由",
          type: "TEXT",
          required: true,
          options: { layout: { x: 1, y: 2, w: 6, h: 2 } },
        },
        {
          key: "category",
          label: "类别",
          type: "SELECT",
          required: false,
          options: {
            layout: { x: 0, y: 4, w: 4, h: 1 },
            choices: [{ label: "年假", value: "annual" }],
          },
        },
      ],
    });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    await wrapper.get("[data-testid='canvas-mode-free']").trigger("click");
    const freeCards = wrapper.findAll("[data-testid='template-free-board'] [data-testid='template-field']");
    expect(freeCards[0].attributes("style")).toContain("grid-column: 2 / span 6");
    expect(freeCards[1].attributes("style")).toContain("grid-column: 1 / span 4");

    p1.updateFormTemplate.mockResolvedValue({ ...LEAVE });
    await wrapper.get("[data-testid='template-save']").trigger("click");
    await flushPromises();
    expect(p1.updateFormTemplate).toHaveBeenCalledWith("tpl-1", {
      code: "leave",
      name: "请假",
      description: "说明",
      fields: [
        {
          key: "reason",
          label: "事由",
          type: "TEXT",
          required: true,
          options: { layout: { x: 1, y: 2, w: 6, h: 2 } },
        },
        {
          key: "category",
          label: "类别",
          type: "SELECT",
          required: false,
          options: {
            layout: { x: 0, y: 4, w: 4, h: 1 },
            choices: [{ label: "年假", value: "annual" }],
          },
        },
      ],
    });
  });

  it("snaps a free-canvas move to a sibling edge then grid (ZA3)", async () => {
    p1.formTemplate.mockResolvedValue({
      ...LEAVE,
      fields: [
        { key: "reason", label: "事由", type: "TEXT", required: true, options: { layout: { x: 4, y: 0, w: 4, h: 1 } } },
        { key: "days", label: "天数", type: "NUMBER", required: false, options: { layout: { x: 0, y: 5, w: 4, h: 1 } } },
      ],
    });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    await wrapper.get("[data-testid='canvas-mode-free']").trigger("click");
    const board = wrapper.get("[data-testid='template-free-board']");
    board.element.getBoundingClientRect = () => ({
      left: 0, top: 0, width: 1200, height: 560, right: 1200, bottom: 560, x: 0, y: 0, toJSON() {},
    });
    const days = wrapper.findAll("[data-testid='template-free-board'] [data-testid='template-field']")[1];
    await days.trigger("dragstart", { dataTransfer: transfer() });
    // raw x≈3.2 cells → edge snap left to sibling x=4
    await board.trigger("drop", { dataTransfer: transfer(), clientX: 320, clientY: 280 });
    expect(days.attributes("style")).toContain("grid-column: 5 / span 4");
  });
});
