/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  formTemplates: vi.fn(),
  productDefinitions: vi.fn(),
  skus: vi.fn(),
  bindProductDefinitionSku: vi.fn(),
  listProcessDefinitions: vi.fn(),
  productConfigReadiness: vi.fn(),
}));
vi.mock("../../api/client", () => ({
  p1: { formTemplates: mocks.formTemplates },
  p4: {
    productDefinitions: mocks.productDefinitions,
    skus: mocks.skus,
    bindProductDefinitionSku: mocks.bindProductDefinitionSku,
    productConfigReadiness: mocks.productConfigReadiness,
  },
}));
vi.mock("../../api/p3", () => ({
  p3: { listProcessDefinitions: mocks.listProcessDefinitions },
}));

import ProductConfigWizardView from "./ProductConfigWizardView.vue";

async function mountPage(path = "/p4/product-config-wizard") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/product-config-wizard", name: "p4-product-config-wizard", component: ProductConfigWizardView },
      { path: "/p4/product-definitions", component: { template: "<div />" } },
      { path: "/p4/skus", component: { template: "<div />" } },
      { path: "/p1/form-templates", component: { template: "<div />" } },
      { path: "/p1/form-templates/new", component: { template: "<div />" } },
      { path: "/p3/workflow", component: { template: "<div />" } },
      { path: "/p3/publish-center", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  const wrapper = mount(ProductConfigWizardView, { global: { plugins: [router] } });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

describe("product config wizard empty/error (PC3)", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.formTemplates.mockResolvedValue([]);
    mocks.productDefinitions.mockResolvedValue([]);
    mocks.skus.mockResolvedValue([]);
    mocks.listProcessDefinitions.mockResolvedValue([]);
  });

  it("keeps steps pending and shows form empty with next CTA", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='nx-page-header']").text()).toContain("就绪态来自产品定义读口");
    expect(wrapper.get("[data-testid='nx-page-header']").text()).not.toContain("未接读口");
    const steps = wrapper.findAll("[data-testid='wizard-steps'] [data-testid^='wizard-step-']");
    expect(steps.every((node) => node.attributes("data-status") === "PENDING")).toBe(true);
    expect(wrapper.get("[data-testid='wizard-empty-form']").text()).toContain("还没有已发布的表单模板");
    expect(wrapper.get("[data-testid='wizard-empty-form'] a").attributes("href")).toBe("/p1/form-templates/new");
    expect(wrapper.text()).not.toMatch(/已完成|✓/);
  });

  it("shows bind empty when definitions exist but SKUs do not", async () => {
    mocks.formTemplates.mockResolvedValue([{ id: "t1", status: "PUBLISHED" }]);
    mocks.productDefinitions.mockResolvedValue([{ id: "d1" }]);
    mocks.skus.mockResolvedValue([]);
    mocks.listProcessDefinitions.mockResolvedValue([{ key: "leave" }]);
    const wrapper = await mountPage();
    await wrapper.get("[data-testid='wizard-step-bind'] button").trigger("click");
    expect(wrapper.get("[data-testid='wizard-empty-bind']").text()).toContain("还没有可绑定的 SKU");
    expect(wrapper.get("[data-testid='wizard-empty-bind'] a").attributes("href")).toBe("/p4/skus");
  });

  it("surfaces load errors with retry", async () => {
    mocks.formTemplates.mockRejectedValueOnce(Object.assign(new Error("platform down"), { traceId: "tr-pc3" }));
    mocks.formTemplates.mockResolvedValueOnce([]);
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='wizard-error']").text()).toContain("platform down");
    expect(wrapper.text()).toContain("tr-pc3");
    await wrapper.get("[data-testid='wizard-error'] button").trigger("click");
    await flushPromises();
    expect(wrapper.find("[data-testid='wizard-error']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='wizard-empty-form']").exists()).toBe(true);
  });

  it("binds an SKU from the wizard panel via the existing bind API (PC4)", async () => {
    mocks.formTemplates.mockResolvedValue([{ id: "t1", status: "PUBLISHED" }]);
    mocks.productDefinitions.mockResolvedValue([{ id: "def-1", code: "hr-basic", name: "HR 基础版", status: "DRAFT" }]);
    mocks.skus.mockResolvedValue([{ code: "crm-pro", name: "专业版" }]);
    mocks.listProcessDefinitions.mockResolvedValue([{ key: "leave" }]);
    mocks.bindProductDefinitionSku.mockResolvedValue({});
    const wrapper = await mountPage();
    await wrapper.get("[data-testid='wizard-step-bind'] button").trigger("click");
    expect(wrapper.get("[data-testid='wizard-definition-list']").text()).toContain("HR 基础版");
    await wrapper.get("[data-testid='wizard-bind-open-hr-basic']").trigger("click");
    await wrapper.get("[data-testid='wizard-bind-sku']").setValue("crm-pro");
    await wrapper.get("[data-testid='wizard-bind-form']").trigger("submit");
    await flushPromises();
    expect(mocks.bindProductDefinitionSku).toHaveBeenCalledWith("def-1", "crm-pro");
    expect(wrapper.get("[data-testid='wizard-bind-notice']").text()).toContain("crm-pro");
  });
});

describe("product config wizard real readiness (PC7)", () => {
  const readiness = (statuses, extra = {}) => ({
    product_definition_id: "d1",
    definition_status: "DRAFT",
    complete: Object.values(statuses).every((status) => status === "DONE"),
    steps: Object.entries(statuses).map(([key, status]) => ({ key, label: key, status, detail: `${key} detail ${status}` })),
    ...extra,
  });

  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.formTemplates.mockResolvedValue([{ id: "t1", status: "PUBLISHED" }]);
    mocks.productDefinitions.mockResolvedValue([
      { id: "d1", code: "hr", name: "HR 基础版" },
      { id: "d2", code: "crm", name: "CRM" },
    ]);
    mocks.skus.mockResolvedValue([{ code: "HR_BASIC" }]);
    mocks.listProcessDefinitions.mockResolvedValue([{ key: "leave" }]);
  });

  const statusOf = (wrapper, id) => wrapper.get(`[data-testid='wizard-step-${id}']`).attributes("data-status");

  it("marks only the steps the backend proves done, mapping sku to the bind step", async () => {
    mocks.productConfigReadiness.mockResolvedValue(readiness({ form: "DONE", process: "UNKNOWN", sku: "DONE", publish: "NOT_READY" }));
    const wrapper = await mountPage();
    expect(mocks.productConfigReadiness).toHaveBeenCalledWith("d1");
    expect(statusOf(wrapper, "form")).toBe("DONE");
    expect(statusOf(wrapper, "process")).toBe("UNKNOWN");
    expect(statusOf(wrapper, "bind")).toBe("DONE");
    expect(statusOf(wrapper, "publish")).toBe("NOT_READY");
    expect(wrapper.get("[data-testid='wizard-step-process']").text()).toContain("无法确认");
    expect(wrapper.get("[data-testid='wizard-step-publish']").text()).toContain("未就绪");
    expect(wrapper.get("[data-testid='wizard-step-detail']").text()).toBe("form detail DONE");
    expect(wrapper.find("[data-testid='wizard-complete']").exists()).toBe(false);
  });

  it("never invents done for unknown words or missing steps", async () => {
    mocks.productConfigReadiness.mockResolvedValue({ complete: false, steps: [{ key: "form", status: "COMPLETED" }] });
    const wrapper = await mountPage();
    for (const id of ["form", "process", "bind", "publish"]) expect(statusOf(wrapper, id)).toBe("PENDING");
    expect(wrapper.text()).not.toContain("已完成");
  });

  it("keeps steps pending with a retry when readiness cannot be read", async () => {
    mocks.productConfigReadiness.mockRejectedValueOnce(new Error("down"));
    mocks.productConfigReadiness.mockResolvedValueOnce(readiness({ form: "DONE", process: "DONE", sku: "DONE", publish: "DONE" }));
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='wizard-readiness-error']").text()).toContain("步骤状态暂时无法读取");
    expect(statusOf(wrapper, "form")).toBe("PENDING");
    await wrapper.get("[data-testid='wizard-readiness-error'] button").trigger("click");
    await flushPromises();
    expect(statusOf(wrapper, "publish")).toBe("DONE");
    expect(wrapper.get("[data-testid='wizard-complete']").text()).toContain("四步都已就绪");
  });

  it("follows the ?definition= product and switches readiness with the selector", async () => {
    mocks.productConfigReadiness.mockImplementation(async (id) => readiness(id === "d2"
      ? { form: "DONE", process: "NOT_READY", sku: "NOT_READY", publish: "NOT_READY" }
      : { form: "NOT_READY", process: "NOT_READY", sku: "NOT_READY", publish: "NOT_READY" }));
    const wrapper = await mountPage("/p4/product-config-wizard?definition=d2");
    expect(mocks.productConfigReadiness).toHaveBeenLastCalledWith("d2");
    expect(statusOf(wrapper, "form")).toBe("DONE");
    await wrapper.get("[data-testid='wizard-product-select']").setValue("d1");
    await flushPromises();
    expect(mocks.productConfigReadiness).toHaveBeenLastCalledWith("d1");
    expect(statusOf(wrapper, "form")).toBe("NOT_READY");
    expect(wrapper.router.currentRoute.value.query.definition).toBe("d1");
  });
});