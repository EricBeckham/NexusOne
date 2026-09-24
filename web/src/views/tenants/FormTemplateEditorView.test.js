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
  const wrapper = mount(FormTemplateEditorView, { global: { plugins: [router] } });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

describe("form template editor", () => {
  beforeEach(() => {
    p1.formTemplate.mockReset();
    p1.createFormTemplate.mockReset();
    p1.updateFormTemplate.mockReset();
  });

  it("shows an empty field list and keeps validate and publish disabled", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    expect(wrapper.get("[data-testid='template-fields-empty']").text()).toBe("暂无字段");
    expect(wrapper.get("[data-testid='template-preview-empty']").text()).toBe("暂无字段");
    expect(wrapper.get("[data-testid='template-validate']").attributes("disabled")).toBeDefined();
    expect(wrapper.get("[data-testid='template-publish']").attributes("disabled")).toBeDefined();
    expect(p1.formTemplate).not.toHaveBeenCalled();
  });

  it("previews a select field from the list without calling validate or publish", async () => {
    const wrapper = await mountAt("/p1/form-templates/new");
    await wrapper.get("[data-testid='add-field']").trigger("click");
    await wrapper.get("[data-testid='field-label']").setValue("类别");
    await wrapper.get("[data-testid='field-key']").setValue("category");
    await wrapper.get("[data-testid='field-type']").setValue("SELECT");
    await wrapper.get("[data-testid='add-option']").trigger("click");
    await wrapper.get("[data-testid='option-label']").setValue("差旅");
    await wrapper.get("[data-testid='option-value']").setValue("travel");
    await wrapper.get("[data-testid='field-required']").setValue(true);
    const preview = wrapper.get("[data-testid='template-preview']");
    expect(preview.text()).toContain("类别");
    expect(preview.text()).toContain("差旅");
    expect(preview.get("fieldset").attributes("disabled")).toBeDefined();
    expect(p1.createFormTemplate).not.toHaveBeenCalled();
  });

  it("saves the ordered field list and keeps the deep link", async () => {
    p1.formTemplate.mockResolvedValue({
      id: "tpl-1",
      code: "leave",
      name: "请假",
      description: "说明",
      status: "DRAFT",
      fields: [{ key: "reason", label: "事由", type: "TEXT", required: true, options: null }],
    });
    const wrapper = await mountAt("/p1/form-templates/tpl-1");
    expect(wrapper.get("[data-testid='template-code']").element.value).toBe("leave");
    expect(wrapper.get("[data-testid='preview-field']").text()).toContain("事由");
    expect(wrapper.router.currentRoute.value.fullPath).toBe("/p1/form-templates/tpl-1");
    await wrapper.get("[data-testid='add-field']").trigger("click");
    const fields = wrapper.findAll("[data-testid='template-field']");
    await fields[1].get("[data-testid='field-label']").setValue("类别");
    await fields[1].get("[data-testid='field-key']").setValue("category");
    await fields[1].get("[data-testid='field-type']").setValue("SELECT");
    await fields[1].get("[data-testid='add-option']").trigger("click");
    await fields[1].get("[data-testid='option-label']").setValue("年假");
    await fields[1].get("[data-testid='option-value']").setValue("annual");
    p1.updateFormTemplate.mockResolvedValue({
      id: "tpl-1",
      code: "leave",
      name: "请假",
      status: "DRAFT",
      fields: [
        { key: "reason", label: "事由", type: "TEXT", required: true, options: null },
        { key: "category", label: "类别", type: "SELECT", required: false, options: [{ label: "年假", value: "annual" }] },
      ],
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
    expect(wrapper.router.currentRoute.value.fullPath).toBe("/p1/form-templates/tpl-1");
  });

  it("shows the server error text", async () => {
    p1.formTemplate.mockRejectedValue(Object.assign(new Error("form template not found"), { traceId: "trace-9" }));
    const wrapper = await mountAt("/p1/form-templates/missing");
    expect(wrapper.text()).toContain("form template not found");
    expect(wrapper.text()).toContain("trace-9");
  });
});
