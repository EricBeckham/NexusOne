/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { clearToasts, useNxToastState } from "../../feedback/nxMessage";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  productApplicationForm: vi.fn(),
  createProductApplication: vi.fn(),
  persons: vi.fn(),
  upload: vi.fn(),
  installation: vi.fn(),
  app: vi.fn(),
  version: vi.fn(),
}));
vi.mock("../../api/p3", () => ({
  p3: {
    productApplicationForm: mocks.productApplicationForm,
    createProductApplication: mocks.createProductApplication,
  },
}));
vi.mock("../../api/client", () => ({
  p2: { persons: mocks.persons },
  p4: { installation: mocks.installation, app: mocks.app, version: mocks.version },
}));
vi.mock("../../api/files", () => ({
  files: { upload: mocks.upload },
  readAsBase64: vi.fn(async () => "YQ=="),
}));

import ProductStartView from "./ProductStartView.vue";

const fields = [
  { key: "title", label: "事由", type: "TEXT", required: true, options: [] },
  { key: "kind", label: "类型", type: "SELECT", required: true, options: ["annual"] },
  { key: "amount", label: "金额", type: "MONEY", required: true, options: [] },
  { key: "approver", label: "审批人", type: "PERSON", required: true, options: [] },
  { key: "evidence", label: "附件", type: "ATTACHMENT", required: false, options: [] },
];

async function mountStart(runtime = false) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/installations/:id/start", component: ProductStartView },
      { path: "/p3/applications/:id/start", component: ProductStartView, meta: { product: 'p3' } },
      { path: "/p3/applications", component: { template: '<div>applications</div>' } },
      { path: "/p4/installations", component: { template: "<div>installations</div>" } },
      { path: "/p3/todos", component: { template: "<div>requests</div>" } },
    ],
  });
  await router.push(runtime ? '/p3/applications/install-1/start' : "/p4/installations/install-1/start");
  const wrapper = mount(ProductStartView, { global: { plugins: [router] } });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

describe("provisioned product start form", () => {
  it('keeps the member runtime cancel and breadcrumbs within P3', async () => {
    mocks.productApplicationForm.mockResolvedValue({ fields });
    const wrapper = await mountStart(true);
    expect(wrapper.html()).not.toContain('/p4/');
    await wrapper.get('[data-testid="start-cancel"]').trigger('click'); await flushPromises();
    expect(wrapper.router.currentRoute.value.path).toBe('/p3/applications');
  });
  beforeEach(() => {
    mocks.productApplicationForm.mockReset();
    mocks.createProductApplication.mockReset();
    mocks.persons.mockReset();
    mocks.upload.mockReset();
    mocks.persons.mockResolvedValue([{ id: "person-1", displayName: "审批人 B", status: "ACTIVE" }]);
    clearToasts();
  });

  it("shows an honest empty state when the pinned template has no fields", async () => {
    mocks.productApplicationForm.mockResolvedValue({ fields: [] });
    const wrapper = await mountStart();
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).toContain("这个已开通产品没有可填写的模板版本。");
    expect(wrapper.find("form").exists()).toBe(false);
    await wrapper.get("[data-testid='start-back']").trigger("click");
    await flushPromises();
    expect(wrapper.router.currentRoute.value.path).toBe("/p4/installations");
  });

  it("does not pretend the template is empty when loading fails", async () => {
    mocks.productApplicationForm.mockRejectedValue(new Error("模板暂时不可用"));
    const wrapper = await mountStart();
    expect(wrapper.get("[data-testid='start-error']").text()).toBe("模板暂时不可用");
    expect(wrapper.text()).not.toContain("没有可填写的模板版本");
    expect(wrapper.find("form").exists()).toBe(false);
  });

  it("renders the pinned fields and opens 我的申请 after submit", async () => {
    mocks.productApplicationForm.mockResolvedValue({ fields });
    mocks.upload.mockResolvedValue({ file_id: "file-1" });
    mocks.createProductApplication.mockResolvedValue({ id: "app-1", status: "RUNNING" });
    const wrapper = await mountStart();
    expect(wrapper.text()).toContain("P4 · 星市");
    expect(wrapper.get("[data-testid='start-form']").classes()).toContain("start-form");
    expect(wrapper.get("label[for='field-title']").text()).toContain("*");
    expect(wrapper.text()).toContain("事由");
    expect(wrapper.text()).toContain("annual");
    expect(wrapper.get('[data-testid="approver-select"]').text()).toContain("审批人 B");
    await wrapper.get("#field-title").setValue("请假");
    await wrapper.get("#field-kind").setValue("annual");
    await wrapper.get("#field-amount").setValue("12.5");
    await wrapper.get('[data-testid="approver-select"]').setValue("person-1");
    const file = new File(["a"], "evidence.txt", { type: "text/plain" });
    Object.defineProperty(wrapper.get("#field-evidence").element, "files", { value: [file] });
    await wrapper.get("#field-evidence").trigger("change");
    await flushPromises();
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(mocks.createProductApplication).toHaveBeenCalledWith("install-1", {
      title: "请假",
      kind: "annual",
      amount: 12.5,
      approver: "person-1",
      evidence: "file-1",
    });
    // S7: the form gives way to the S3 result shell; 查看我的申请 opens 我的申请.
    expect(wrapper.find("form").exists()).toBe(false);
    const result = wrapper.get("[data-testid='start-result']");
    expect(result.get("[data-testid='nx-result-title']").text()).toBe("申请已提交");
    expect(result.text()).toContain("申请编号 app-1…");
    expect(result.findAll(".nx-btn--primary").map((node) => node.text())).toEqual(["查看我的申请"]);
    expect(useNxToastState().items).toHaveLength(0);
    await result.get("[data-testid='start-result-requests']").trigger("click");
    await flushPromises();
    expect(wrapper.router.currentRoute.value.fullPath).toBe("/p3/todos?box=requests");
  });

  it("offers 返回我的已开通 and 再发起一条 with an empty form after a submit (S7)", async () => {
    mocks.productApplicationForm.mockResolvedValue({ fields: [{ key: "title", label: "事由", type: "TEXT", required: true, options: [] }] });
    mocks.createProductApplication.mockResolvedValue({ id: "01a0daf7-6532-7339" });
    const wrapper = await mountStart();
    await wrapper.get("#field-title").setValue("请假");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get("[data-testid='start-result-back']").attributes("href")).toBe("/p4/installations");
    await wrapper.get("[data-testid='start-result-again']").trigger("click");
    await flushPromises();
    expect(wrapper.find("[data-testid='start-result']").exists()).toBe(false);
    expect(wrapper.get("#field-title").element.value).toBe("");
    expect(mocks.createProductApplication).toHaveBeenCalledTimes(1);
  });

  it("shows the server failure text and places snake_case field errors under their field", async () => {
    mocks.productApplicationForm.mockResolvedValue({ fields: [{ key: "title", label: "事由", type: "TEXT", required: true, options: [] }] });
    // Real wire shape: ProductApplicationController returns FieldError records through the SNAKE_CASE mapper.
    const failure = Object.assign(new Error("application values do not match the pinned form template"), { status: 422 });
    failure.body = { message: failure.message, errors: [{ field_key: "title", code: "field.required", message: "A value is required" }] };
    mocks.createProductApplication.mockRejectedValue(failure);
    const wrapper = await mountStart();
    await wrapper.get("#field-title").setValue("请假");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    const alert = wrapper.get('[data-testid="start-submit-failure"]');
    expect(alert.get('[data-testid="nx-request-alert-hint"]').text()).toContain("按字段下方的提示修改");
    expect(alert.get('[data-testid="nx-request-alert-server"]').text()).toContain("application values do not match the pinned form template");
    expect(alert.find('[data-testid="nx-request-alert-retry"]').exists()).toBe(false);
    expect(wrapper.get("[data-testid='field-error-title']").text()).toBe("A value is required");
    expect(wrapper.find("[data-testid='start-field-errors']").exists()).toBe(false);
    expect(wrapper.router.currentRoute.value.path).toBe("/p4/installations/install-1/start");
    await wrapper.get("[data-testid='start-cancel']").trigger("click");
    await flushPromises();
    expect(wrapper.router.currentRoute.value.path).toBe("/p4/installations");
  });

  it("names the installed product and version being applied for (N1)", async () => {
    mocks.productApplicationForm.mockResolvedValue({ fields: [{ key: "title", label: "事由", type: "TEXT", required: true, options: [] }] });
    // Real SNAKE_CASE wire shapes for /p4/installations/{id}, /p4/apps/{id}, /p4/versions/{id}.
    mocks.installation.mockResolvedValue({ id: "install-1", app_id: "app-1", version_id: "ver-1", status: "INSTALLED" });
    mocks.app.mockResolvedValue({ id: "app-1", code: "demo_simple_approval", name: "演示数据 · 简单审批" });
    mocks.version.mockResolvedValue({ id: "ver-1", version: "1.0.0" });
    const wrapper = await mountStart();
    const context = wrapper.get("[data-testid='start-product']");
    expect(context.text()).toContain("演示数据 · 简单审批");
    expect(context.text()).toContain("版本 1.0.0");
    expect(mocks.installation).toHaveBeenCalledWith("install-1");
    wrapper.unmount();

    mocks.installation.mockRejectedValue(new Error("installation not found"));
    const fallback = await mountStart();
    expect(fallback.find("[data-testid='start-product']").exists()).toBe(false);
    expect(fallback.find("[data-testid='start-form']").exists()).toBe(true);
  });

  it("loads with a form skeleton and ends in one footer: 取消 then a single Primary (Q9)", async () => {
    let resolveForm;
    mocks.productApplicationForm.mockReturnValue(new Promise((resolve) => { resolveForm = resolve; }));
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: "/p4/installations/:id/start", component: ProductStartView }] });
    await router.push("/p4/installations/install-1/start");
    const wrapper = mount(ProductStartView, { global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.get("[data-testid='start-loading'] [data-testid='nx-skeleton']").attributes("data-variant")).toBe("form-block");
    expect(wrapper.find("form").exists()).toBe(false);

    resolveForm({ fields: [{ key: "title", label: "事由", type: "TEXT", required: true, options: [] }] });
    await flushPromises();
    expect(wrapper.find("[data-testid='start-loading']").exists()).toBe(false);
    expect(wrapper.get("label[for='field-title'] em.nx-req").text()).toBe("*");
    const footer = wrapper.get("[data-testid='start-actions']");
    expect(footer.findAll("button").map((button) => button.text())).toEqual(["取消", "提交申请"]);
    expect(footer.findAll(".nx-btn--primary, .primary")).toHaveLength(1);
    expect(footer.get("[data-testid='start-submit']").attributes("type")).toBe("submit");
    expect(wrapper.findAll(".nx-btn--primary, .primary")).toHaveLength(1);
  });
  it("explains an opaque 500, shows the trace id, and retries with the same values", async () => {
    mocks.productApplicationForm.mockResolvedValue({ fields: [{ key: "title", label: "事由", type: "TEXT", required: true, options: [] }] });
    mocks.createProductApplication.mockRejectedValueOnce(Object.assign(new Error("Internal error"), {
      status: 500, code: "CORE.API.0099", traceId: "trace-h10",
    }));
    const wrapper = await mountStart();
    await wrapper.get("#field-title").setValue("请假");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    const alert = wrapper.get('[data-testid="start-submit-failure"]');
    expect(alert.text()).toContain("提交失败，已填写的内容已保留");
    expect(alert.get('[data-testid="nx-request-alert-hint"]').text()).toContain("可以稍后重试");
    expect(alert.get('[data-testid="nx-request-alert-trace"]').text()).toContain("trace-h10");
    expect(alert.text()).toContain("CORE.API.0099");
    expect(wrapper.text()).not.toContain("Internal error");
    expect(wrapper.get("#field-title").element.value).toBe("请假");

    mocks.createProductApplication.mockResolvedValueOnce({ id: "app-1" });
    await alert.get('[data-testid="nx-request-alert-retry"]').trigger("click");
    await flushPromises();
    expect(mocks.createProductApplication).toHaveBeenCalledTimes(2);
    expect(mocks.createProductApplication).toHaveBeenLastCalledWith("install-1", { title: "请假" });
    expect(wrapper.get("[data-testid='start-result'] [data-testid='nx-result-title']").text()).toBe("申请已提交");
  });
});
