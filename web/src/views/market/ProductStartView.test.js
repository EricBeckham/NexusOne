/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  productApplicationForm: vi.fn(),
  createProductApplication: vi.fn(),
  persons: vi.fn(),
  upload: vi.fn(),
}));
vi.mock("../../api/p3", () => ({
  p3: {
    productApplicationForm: mocks.productApplicationForm,
    createProductApplication: mocks.createProductApplication,
  },
}));
vi.mock("../../api/client", () => ({ p2: { persons: mocks.persons } }));
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

async function mountStart() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/installations/:id/start", component: ProductStartView },
      { path: "/p4/installations", component: { template: "<div>installations</div>" } },
      { path: "/p3/todos", component: { template: "<div>requests</div>" } },
    ],
  });
  await router.push("/p4/installations/install-1/start");
  const wrapper = mount(ProductStartView, { global: { plugins: [router] } });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

describe("provisioned product start form", () => {
  beforeEach(() => {
    mocks.productApplicationForm.mockReset();
    mocks.createProductApplication.mockReset();
    mocks.persons.mockReset();
    mocks.upload.mockReset();
    mocks.persons.mockResolvedValue([{ id: "person-1", displayName: "审批人 B", status: "ACTIVE" }]);
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
    expect(wrapper.router.currentRoute.value.fullPath).toBe("/p3/todos?box=requests");
  });

  it("shows the server failure text", async () => {
    mocks.productApplicationForm.mockResolvedValue({ fields: [{ key: "title", label: "事由", type: "TEXT", required: true, options: [] }] });
    const failure = new Error("application values do not match the pinned form template");
    failure.body = { errors: [{ fieldKey: "title", code: "field.required", message: "A value is required" }] };
    mocks.createProductApplication.mockRejectedValue(failure);
    const wrapper = await mountStart();
    await wrapper.get("#field-title").setValue("请假");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get('[data-testid="start-error"]').text()).toBe("application values do not match the pinned form template");
    expect(wrapper.get("[data-testid='field-error-title']").text()).toBe("A value is required");
    expect(wrapper.router.currentRoute.value.path).toBe("/p4/installations/install-1/start");
    await wrapper.get("[data-testid='start-cancel']").trigger("click");
    await flushPromises();
    expect(wrapper.router.currentRoute.value.path).toBe("/p4/installations");
  });
});
