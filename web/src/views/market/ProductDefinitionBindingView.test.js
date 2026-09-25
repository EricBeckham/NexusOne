/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProductDefinitionBindingView from "./ProductDefinitionBindingView.vue";
import { p1, p4 } from "../../api/client";
import { p3 } from "../../api/p3";

vi.mock("../../api/client", () => ({
  p1: { formTemplates: vi.fn() },
  p4: { productDefinitions: vi.fn(), createProductDefinition: vi.fn(), updateProductDefinition: vi.fn() },
}));
vi.mock("../../api/p3", () => ({
  p3: { listProcessDrafts: vi.fn(), getProcessPublication: vi.fn() },
}));

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p4/product-definitions", component: ProductDefinitionBindingView }],
  });
  await router.push("/p4/product-definitions");
  const wrapper = mount(ProductDefinitionBindingView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("product definition binding page", () => {
  beforeEach(() => {
    p1.formTemplates.mockReset();
    p3.listProcessDrafts.mockReset();
    p3.getProcessPublication.mockReset();
    p4.productDefinitions.mockReset();
    p4.createProductDefinition.mockReset();
    p4.updateProductDefinition.mockReset();
    p4.productDefinitions.mockResolvedValue([]);
    p1.formTemplates.mockResolvedValue([]);
    p3.listProcessDrafts.mockResolvedValue([]);
  });

  it("shows 暂无 and the permission note without a tenant count", async () => {
    const wrapper = await mountPage();
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.get("[data-testid='definition-permission']").text()).toContain("租户成员可发起");
    expect(wrapper.text()).not.toContain("已绑定租户");
  });

  it("saves a published template version and process version", async () => {
    p1.formTemplates.mockResolvedValue([
      { id: "tpl-1", code: "leave", name: "请假", status: "DRAFT", runningVersionId: null },
      { id: "tpl-2", code: "leave", name: "请假", status: "PUBLISHED", runningVersionId: "ver-9" },
    ]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "leave_flow" }, { key: "draft_only" }]);
    p3.getProcessPublication.mockImplementation(async (key) => {
      if (key === "draft_only") throw new Error("not found");
      return { key: "leave_flow", name: "请假审批", version: 3 };
    });
    p4.createProductDefinition.mockResolvedValue({ id: "def-1", code: "hr-basic", status: "DRAFT" });

    const wrapper = await mountPage();
    const templateOptions = wrapper.get("[data-testid='definition-template']").findAll("option").map((item) => item.text());
    expect(templateOptions.join(" ")).toContain("请假");
    expect(templateOptions.join(" ")).not.toContain("DRAFT");
    expect(wrapper.get("[data-testid='definition-process']").text()).toContain("版本 3");

    await wrapper.get("[data-testid='definition-code']").setValue("hr-basic");
    await wrapper.get("[data-testid='definition-name']").setValue("HR 基础版");
    await wrapper.get("[data-testid='definition-template']").setValue("ver-9");
    await wrapper.get("[data-testid='definition-process']").setValue("leave_flow");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(p4.createProductDefinition).toHaveBeenCalledWith({
      code: "hr-basic",
      name: "HR 基础版",
      description: null,
      formTemplateVersionId: "ver-9",
      processDefinitionKey: "leave_flow",
      processDefinitionVersion: 3,
      defaultPermissionPack: { summary: "租户成员可发起；审批人来自表单中的成员字段，且必须是本租户成员。" },
    });
    expect(wrapper.get("[data-testid='definition-saved']").text()).toBe("已保存 hr-basic");
  });

  it("shows the server failure text", async () => {
    p1.formTemplates.mockResolvedValue([
      { id: "tpl-2", code: "leave", name: "请假", status: "PUBLISHED", runningVersionId: "ver-9" },
    ]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "leave_flow" }]);
    p3.getProcessPublication.mockResolvedValue({ key: "leave_flow", name: "请假审批", version: 1 });
    p4.createProductDefinition.mockRejectedValue(new Error("form template version is not published or does not exist"));

    const wrapper = await mountPage();
    await wrapper.get("[data-testid='definition-code']").setValue("hr-basic");
    await wrapper.get("[data-testid='definition-name']").setValue("HR 基础版");
    await wrapper.get("[data-testid='definition-template']").setValue("ver-9");
    await wrapper.get("[data-testid='definition-process']").setValue("leave_flow");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.get("[data-testid='definition-save-error']").text())
      .toBe("form template version is not published or does not exist");
  });
});
