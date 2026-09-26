/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProductDefinitionBindingView from "./ProductDefinitionBindingView.vue";
import { p1, p4 } from "../../api/client";
import { p3 } from "../../api/p3";

vi.mock("../../api/client", () => ({
  p1: { formTemplates: vi.fn() },
  p4: {
    productDefinitions: vi.fn(), createProductDefinition: vi.fn(), updateProductDefinition: vi.fn(),
    bindProductDefinitionSku: vi.fn(), skus: vi.fn(), catalog: vi.fn(),
  },
}));
vi.mock("../../api/p3", () => ({
  p3: { listProcessDrafts: vi.fn(), listProcessDefinitions: vi.fn(), getProcessPublication: vi.fn() },
}));

// Real wire shapes: the product mapper is SNAKE_CASE (pinned by PlatformBindingWireNamingTest).
const PUBLISHED_TEMPLATE = { id: "tpl-2", code: "leave", name: "请假", status: "PUBLISHED", running_version_id: "ver-9" };
const DEFINITION = {
  id: "def-1", code: "hr-basic", name: "HR 基础版", status: "DRAFT",
  form_template_version_id: "ver-9", process_definition_key: "leave_flow", process_definition_version: 3,
};

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/product-definitions", component: ProductDefinitionBindingView },
      { path: "/:rest(.*)*", component: { template: "<div />" } },
    ],
  });
  await router.push("/p4/product-definitions");
  const wrapper = mount(ProductDefinitionBindingView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

function publishedProcess() {
  p3.listProcessDrafts.mockResolvedValue([{ key: "leave_flow" }, { key: "draft_only" }]);
  p3.listProcessDefinitions.mockResolvedValue([
    { key: "leave_flow", name: "请假审批", source: "tenant-deployment", version: 3 },
  ]);
  p3.getProcessPublication.mockImplementation(async (key) => {
    if (key === "draft_only") throw new Error("not found");
    return { key: "leave_flow", name: "请假审批", version: 3 };
  });
}

describe("product definition binding page", () => {
  beforeEach(() => {
    for (const fn of [...Object.values(p1), ...Object.values(p4), ...Object.values(p3)]) fn.mockReset();
    p4.productDefinitions.mockResolvedValue([]);
    p4.skus.mockResolvedValue([]);
    p4.catalog.mockResolvedValue([]);
    p1.formTemplates.mockResolvedValue([]);
    p3.listProcessDrafts.mockResolvedValue([]);
    p3.listProcessDefinitions.mockResolvedValue([]);
  });

  it("guides an empty platform to publish a template and a process first", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='open-config-wizard']").attributes("href")).toBe("/p4/product-config-wizard");
    expect(wrapper.text()).toContain("暂无产品定义");
    expect(wrapper.get("[data-testid='nx-empty-next'] a").attributes("href")).toBe("#new-definition");
    await wrapper.get("[data-testid='definition-code']").trigger("blur");
    expect(wrapper.get("[data-testid='definition-code']").attributes("aria-invalid")).toBe("true");
    expect(wrapper.get("[data-testid='definition-code']").element.closest(".nx-field").querySelector(".nx-field-error").textContent).toBe("请填写产品编码");
    expect(wrapper.get("[data-testid='definition-permission']").text()).toContain("租户成员可发起");
    expect(wrapper.get("[data-testid='journey-template']").attributes("data-done")).toBe("false");
    expect(wrapper.get("[data-testid='definition-template-empty']").text()).toContain("下拉已禁用");
    expect(wrapper.get("[data-testid='definition-template-empty'] a").attributes("href")).toBe("/p1/form-templates");
    expect(wrapper.get("[data-testid='definition-process-empty']").text()).toContain("classpath 只读目录不能直接绑定");
    expect(wrapper.get("[data-testid='definition-process-empty'] a").attributes("href")).toBe("/p3/workflow");
    expect(wrapper.get("[data-testid='definition-template']").attributes("disabled")).toBeDefined();
    expect(wrapper.get("[data-testid='definition-process']").attributes("disabled")).toBeDefined();
    expect(wrapper.get("[data-testid='definition-save']").attributes("disabled")).toBeDefined();
    expect(wrapper.text()).not.toContain("已绑定租户");
  });

  it("reads snake_case templates, saves with snake_case keys, and offers the SKU binding next", async () => {
    p1.formTemplates.mockResolvedValue([
      { id: "tpl-1", code: "leave", name: "请假", status: "DRAFT", running_version_id: null },
      PUBLISHED_TEMPLATE,
    ]);
    publishedProcess();
    p4.createProductDefinition.mockResolvedValue({ ...DEFINITION });

    const wrapper = await mountPage();
    const templateOptions = wrapper.get("[data-testid='definition-template']").findAll("option").map((item) => item.text());
    expect(templateOptions.join(" ")).toContain("请假");
    expect(templateOptions).toHaveLength(2);
    expect(wrapper.get("[data-testid='definition-process']").text()).toContain("版本 3");
    expect(wrapper.get("[data-testid='journey-template']").attributes("data-done")).toBe("true");

    await wrapper.get("[data-testid='definition-code']").setValue("hr-basic");
    await wrapper.get("[data-testid='definition-name']").setValue("HR 基础版");
    await wrapper.get("[data-testid='definition-template']").setValue("ver-9");
    await wrapper.get("[data-testid='definition-process']").setValue("leave_flow");
    p4.productDefinitions.mockResolvedValue([DEFINITION]);
    await wrapper.get("form[data-testid='product-definition-form']").trigger("submit");
    await flushPromises();

    expect(p4.createProductDefinition).toHaveBeenCalledWith({
      code: "hr-basic",
      name: "HR 基础版",
      description: null,
      form_template_version_id: "ver-9",
      process_definition_key: "leave_flow",
      process_definition_version: 3,
      default_permission_pack: { summary: "租户成员可发起；审批人来自表单中的成员字段，且必须是本租户成员。" },
    });
    expect(wrapper.get("[data-testid='definition-saved']").text()).toBe("已保存 hr-basic");
    const list = wrapper.get("[data-testid='product-definition-list']");
    expect(list.classes()).toContain("nx-table--compact");
    expect(list.text()).toContain("请假");
    expect(list.text()).toContain("草稿");
    expect(list.text()).toContain("leave_flow · 版本 3");
    expect(list.text()).toContain("未绑定");
    await wrapper.get("[data-testid='notice-bind']").trigger("click");
    expect(wrapper.get("[data-testid='bind-panel']").text()).toContain("HR 基础版");
  });

  it("binds a SKU, warns before rebinding, and shows the result", async () => {
    p1.formTemplates.mockResolvedValue([PUBLISHED_TEMPLATE]);
    publishedProcess();
    p4.productDefinitions.mockResolvedValue([DEFINITION, { ...DEFINITION, id: "def-2", code: "hr-pro", name: "HR 专业版" }]);
    p4.skus.mockResolvedValue([{ code: "hr.std", name: "标准版", app_id: "app-1" }, { code: "hr.pro", name: "专业版", app_id: "app-1" }]);
    p4.catalog.mockResolvedValue([{ app: { id: "app-1" }, version: { id: "v1" }, skus: [
      { code: "hr.pro", name: "专业版", product_definition: { id: "def-2", code: "hr-pro", name: "HR 专业版" } },
    ] }]);

    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='journey-sku']").attributes("data-done")).toBe("true");
    expect(wrapper.get("[data-testid='product-definition-list']").text()).toContain("专业版");
    await wrapper.get("[data-testid='bind-open-hr-basic']").trigger("click");
    await wrapper.get("[data-testid='bind-sku']").setValue("hr.pro");
    expect(wrapper.get("[data-testid='bind-rebind-warning']").text()).toContain("HR 专业版");
    await wrapper.get("[data-testid='bind-sku']").setValue("hr.std");
    expect(wrapper.find("[data-testid='bind-rebind-warning']").exists()).toBe(false);

    p4.bindProductDefinitionSku.mockResolvedValue({ ...DEFINITION });
    p4.catalog.mockResolvedValue([{ app: { id: "app-1" }, version: { id: "v1" }, skus: [
      { code: "hr.std", name: "标准版", product_definition: { id: "def-1", code: "hr-basic", name: "HR 基础版" } },
    ] }]);
    await wrapper.get("[data-testid='bind-panel']").trigger("submit");
    await flushPromises();
    expect(p4.bindProductDefinitionSku).toHaveBeenCalledWith("def-1", "hr.std");
    expect(wrapper.find("[data-testid='bind-panel']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='binding-notice']").text()).toContain("已把 SKU hr.std 绑定到「HR 基础版」");
    expect(wrapper.get("[data-testid='binding-notice'] a").attributes("href")).toBe("/p4/market");
  });

  it("explains unpublished templates and keeps the page when process drafts need a tenant", async () => {
    p1.formTemplates.mockResolvedValue([
      { id: "tpl-1", code: "leave", name: "请假", status: "DRAFT", running_version_id: null },
      { id: "tpl-3", code: "trip", name: "出差", status: "VALIDATED", running_version_id: null },
    ]);
    p3.listProcessDrafts.mockRejectedValue(Object.assign(new Error("TenantContext is required"), { status: 403 }));
    p3.listProcessDefinitions.mockRejectedValue(Object.assign(new Error("TenantContext is required"), { status: 403 }));
    p4.productDefinitions.mockResolvedValue([DEFINITION]);

    const wrapper = await mountPage();
    expect(wrapper.find("[data-testid='nx-error-state']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='product-definition-list']").text()).toContain("HR 基础版");
    expect(wrapper.get("[data-testid='definition-template-empty']").text()).toContain("有 2 个模板还是草稿或仅通过校验");
    expect(wrapper.get("[data-testid='definition-process-unavailable']").text()).toContain("没有租户身份");
    expect(wrapper.find("[data-testid='definition-process-empty']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='journey-process']").text()).toContain("需要租户身份才能读取");
    expect(wrapper.text()).not.toContain("TenantContext is required");
  });

  it("asks an anonymous platform operator to sign in as a tenant when drafts return 401", async () => {
    p1.formTemplates.mockResolvedValue([PUBLISHED_TEMPLATE]);
    // Real J3 response without a session: 401 P2.IAM.UNAUTHORIZED "missing bearer token".
    const unauthorized = Object.assign(new Error("missing bearer token"), {
      status: 401, code: "P2.IAM.UNAUTHORIZED", traceId: "01a0d8c6-4103-7f6c-a3eb-f9d8181966da",
    });
    p3.listProcessDrafts.mockRejectedValue(unauthorized);
    p3.listProcessDefinitions.mockRejectedValue(unauthorized);
    const wrapper = await mountPage();
    const hint = wrapper.get("[data-testid='definition-process-unavailable']").text();
    expect(hint).toContain("当前未登录租户账号");
    expect(hint).not.toContain("暂时读不到");
    expect(wrapper.get("[data-testid='journey-process']").text()).toContain("需要租户身份才能读取");
    expect(wrapper.get("[data-testid='definition-template']").text()).toContain("请假");
  });

  it("lists a tenant-deployed process even when the draft row is missing (N7 / J3 B)", async () => {
    p1.formTemplates.mockResolvedValue([PUBLISHED_TEMPLATE]);
    p3.listProcessDrafts.mockResolvedValue([]);
    p3.listProcessDefinitions.mockResolvedValue([
      { key: "classpath_only", name: "Classpath", source: "classpath" },
      { key: "demoSimpleApproval", name: "演示简单审批", source: "tenant-deployment", tenant_id: "t1" },
    ]);
    p3.getProcessPublication.mockImplementation(async (key) => {
      if (key !== "demoSimpleApproval") throw new Error("not found");
      return { key: "demoSimpleApproval", name: "演示简单审批", version: 1 };
    });
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='definition-process']").text()).toContain("demoSimpleApproval");
    expect(wrapper.get("[data-testid='definition-process']").text()).toContain("版本 1");
    expect(wrapper.get("[data-testid='definition-process']").attributes("disabled")).toBeUndefined();
    expect(wrapper.find("[data-testid='definition-process-empty']").exists()).toBe(false);
  });

  it("explains unpublished process drafts when the catalog has no tenant deployment", async () => {
    p1.formTemplates.mockResolvedValue([PUBLISHED_TEMPLATE]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "draft_only", name: "未发布草稿" }]);
    p3.listProcessDefinitions.mockResolvedValue([{ key: "classpath_only", name: "Classpath", source: "classpath" }]);
    p3.getProcessPublication.mockRejectedValue(new Error("not found"));
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='definition-process-empty']").text()).toContain("有 1 个流程草稿尚未发布");
    expect(wrapper.get("[data-testid='definition-process']").attributes("disabled")).toBeDefined();
  });

  it("shows the server failure text for save and bind", async () => {
    p1.formTemplates.mockResolvedValue([PUBLISHED_TEMPLATE]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "leave_flow" }]);
    p3.listProcessDefinitions.mockResolvedValue([{ key: "leave_flow", source: "tenant-deployment" }]);
    p3.getProcessPublication.mockResolvedValue({ key: "leave_flow", name: "请假审批", version: 1 });
    p4.createProductDefinition.mockRejectedValue(new Error("form template version is not published or does not exist"));
    p4.productDefinitions.mockResolvedValue([DEFINITION]);
    p4.skus.mockResolvedValue([{ code: "hr.std", name: "标准版" }]);
    p4.bindProductDefinitionSku.mockRejectedValue(new Error("sku not found"));

    const wrapper = await mountPage();
    await wrapper.get("[data-testid='definition-code']").setValue("hr-basic");
    await wrapper.get("[data-testid='definition-name']").setValue("HR 基础版");
    await wrapper.get("[data-testid='definition-template']").setValue("ver-9");
    await wrapper.get("[data-testid='definition-process']").setValue("leave_flow");
    await wrapper.get("form[data-testid='product-definition-form']").trigger("submit");
    await flushPromises();
    expect(wrapper.get("[data-testid='definition-save-error']").text())
      .toBe("form template version is not published or does not exist");

    await wrapper.get("[data-testid='bind-open-hr-basic']").trigger("click");
    await wrapper.get("[data-testid='bind-sku']").setValue("hr.std");
    await wrapper.get("[data-testid='bind-panel']").trigger("submit");
    await flushPromises();
    expect(wrapper.get("[data-testid='bind-error']").text()).toBe("sku not found");
  });
});
