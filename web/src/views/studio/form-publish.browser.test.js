/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  listForms: vi.fn(),
  listLedgers: vi.fn(),
  listApplications: vi.fn(),
  getLedger: vi.fn(),
  createForm: vi.fn(),
  validateForm: vi.fn(),
  publishForm: vi.fn(),
}));

vi.mock("../../api/p3", () => ({ p3: mocks }));
vi.mock("../../api/session", () => ({ session: { contextId: "t3f-context" } }));

import FormListView from "./FormListView.vue";

async function mountForms() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p3/forms", component: FormListView },
      { path: "/p3/forms/:id", component: { template: "<div>form</div>" } },
    ],
  });
  await router.push("/p3/forms");
  await router.isReady();
  const wrapper = mount(FormListView, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("T3-F basic form publish", () => {
  let forms;

  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    forms = [{
      id: "form-1",
      name: "客户表单",
      code: "customer_form",
      ledger_name: "customers",
      draft_status: "DRAFT",
      running_version: 0,
    }];
    mocks.listForms.mockImplementation(async () => ({ items: forms }));
    mocks.listLedgers.mockResolvedValue({ items: [{ id: "ledger-1", code: "customers", name: "客户台账", application_id: "app-1" }] });
    mocks.listApplications.mockResolvedValue({ items: [{ id: "app-1", app_code: "crm" }] });
    mocks.getLedger.mockResolvedValue({
      id: "ledger-1",
      code: "customers",
      application_id: "app-1",
      fields: [{ code: "title", name: "标题", type: "text", required: true }],
    });
    mocks.createForm.mockResolvedValue({ id: "form-2" });
    mocks.validateForm.mockResolvedValue({ ok: true, issues: [] });
    mocks.publishForm.mockImplementation(async () => {
      forms = [{ ...forms[0], draft_status: "DRAFT", running_version: 1 }];
      return { running_version: 1 };
    });
  });

  it("creates a DRAFT bound to the T3-E ledger object contract", async () => {
    const { wrapper, router } = await mountForms();
    await wrapper.get('[data-testid="form-create-toggle"]').trigger("click");
    await wrapper.get('[data-testid="form-ledger-select"]').setValue("ledger-1");
    await wrapper.get('[data-testid="form-code"]').setValue("customer_create");
    await wrapper.get('[data-testid="form-name"]').setValue("客户创建表单");
    await wrapper.get('[data-testid="form-create-form"]').trigger("submit");
    await flushPromises();

    expect(mocks.createForm).toHaveBeenCalledWith({
      app_code: "crm",
      form_code: "customer_create",
      name: "客户创建表单",
      bindings: [{
        widget_code: "title",
        ledger_code: "customers",
        field_code: "title",
        required: true,
        widget_type: "TEXT",
        ordinal: 0,
      }],
    });
    expect(router.currentRoute.value.fullPath).toBe("/p3/forms/form-2?tab=publish");
  });

  it("validates, publishes, and refreshes the list with Running v1", async () => {
    const { wrapper } = await mountForms();
    await wrapper.get('[data-testid="form-quick-publish"]').trigger("click");
    await flushPromises();

    expect(mocks.validateForm).toHaveBeenCalledWith("form-1");
    expect(mocks.publishForm).toHaveBeenCalledWith("form-1");
    expect(wrapper.text()).toContain("运行 v1");
    expect(wrapper.text()).toContain("列表已刷新");
  });
});
