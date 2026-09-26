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
vi.mock("../../api/session", () => ({ session: { contextId: "ctx-1" } }));

import FormListView from "./FormListView.vue";

async function mountForms() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p3/forms", component: FormListView },
      { path: "/p3/forms/:id", component: { template: "<div />" } },
      { path: "/p3/ledgers", component: { template: "<div />" } },
    ],
  });
  await router.push("/p3/forms");
  const wrapper = mount(FormListView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("form list visual standard (R12)", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.listForms.mockResolvedValue({ items: [] });
    mocks.listLedgers.mockResolvedValue({ items: [{ id: "ledger-1", code: "customers", name: "客户台账" }] });
    mocks.listApplications.mockResolvedValue({ items: [] });
  });

  it("shows a skeleton while loading, then an empty state with the next step", async () => {
    let resolve;
    mocks.listForms.mockReturnValue(new Promise((done) => { resolve = done; }));
    const wrapper = await mountForms();
    expect(wrapper.find("[data-testid='form-loading'] [data-testid='nx-skeleton']").exists()).toBe(true);
    resolve({ items: [] });
    await flushPromises();
    const empty = wrapper.get("[data-testid='form-empty']");
    expect(empty.text()).toContain("还没有表单");
    expect(wrapper.text()).not.toContain("M0-07");
    await empty.get("button").trigger("click");
    expect(wrapper.find("[data-testid='form-create-form']").exists()).toBe(true);
  });

  it("points to 台账 first when the tenant has no ledgers", async () => {
    mocks.listLedgers.mockResolvedValue({ items: [] });
    const wrapper = await mountForms();
    expect(wrapper.get("[data-testid='form-empty'] a").attributes("href")).toBe("/p3/ledgers");
  });

  it("uses readable headers, status tags and text row actions — no Primary inside rows", async () => {
    mocks.listForms.mockResolvedValue({
      items: [
        { id: "form-1", name: "客户表单", ledger_name: "customers", draft_status: "DRAFT", running_version: 0 },
        { id: "form-2", name: "请假单", ledger_name: "leave", draft_status: "VALIDATED", running_version: 2 },
      ],
    });
    const wrapper = await mountForms();
    expect(wrapper.findAll("thead th").map((th) => th.text())).toEqual(["表单", "绑定台账", "草稿状态", "运行版本", "操作"]);
    const [first, second] = wrapper.findAll("tbody tr");
    expect(first.get("[data-testid='nx-status-tag']").text()).toBe("草稿");
    expect(first.text()).toContain("未发布");
    expect(second.get("[data-testid='nx-status-tag']").text()).toBe("已校验");
    expect(second.text()).toContain("运行 v2");
    expect(wrapper.findAll("tbody .primary, tbody .nx-btn--primary")).toHaveLength(0);
    expect(first.get("[data-testid='form-quick-publish']").classes()).toContain("nx-btn--text");
    expect(second.find("[data-testid='form-quick-publish']").exists()).toBe(false);
  });

  it("keeps one Primary while the create panel is open", async () => {
    const wrapper = await mountForms();
    await wrapper.get("[data-testid='form-create-toggle']").trigger("click");
    expect(wrapper.get("[data-testid='form-create-toggle']").classes()).toContain("ghost");
    expect(wrapper.findAll(".primary, .nx-btn--primary").map((node) => node.text())).toEqual(["创建草稿"]);
    expect(wrapper.findAll("[data-testid='form-create-form'] em.nx-req")).toHaveLength(3);
  });
});