/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FormTemplateListView from "./FormTemplateListView.vue";
import { p1 } from "../../api/client";

vi.mock("../../api/client", () => ({
  p1: { formTemplates: vi.fn() },
}));

async function mountList() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p1/form-templates", component: FormTemplateListView },
      { path: "/p1/form-templates/new", component: { template: "<div>new</div>" } },
      { path: "/p1/form-templates/:id", component: { template: "<div>editor</div>" } },
      { path: "/p4/product-config-wizard", component: { template: "<div>wizard</div>" } },
      { path: "/p4/product-definitions", component: { template: "<div>definitions</div>" } },
      { path: "/p3/workflow", component: { template: "<div>workflow</div>" } },
    ],
  });
  await router.push("/p1/form-templates");
  const wrapper = mount(FormTemplateListView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("form template list", () => {
  beforeEach(() => p1.formTemplates.mockReset());

  it("shows empty next steps toward the config wizard via NxDataTable S shell (DT1)", async () => {
    p1.formTemplates.mockResolvedValue([]);
    const wrapper = await mountList();
    expect(wrapper.get("[data-testid='template-list'] [data-testid='nx-data-table-empty']").exists()).toBe(true);
    expect(wrapper.get("[data-testid='nx-empty-title']").text()).toBe("还没有表单模板");
    expect(wrapper.get("[data-testid='nx-data-table-empty']").text()).toContain("产品配置向导");
    expect(wrapper.get("[data-testid='nx-empty-next'] a.primary").attributes("href")).toBe("/p1/form-templates/new");
    expect(wrapper.get("[data-testid='template-empty-wizard']").attributes("href")).toBe("/p4/product-config-wizard");
    expect(wrapper.get("[data-testid='template-empty-definitions']").attributes("href")).toBe("/p4/product-definitions");
    expect(wrapper.get("[data-testid='template-open-wizard']").attributes("href")).toBe("/p4/product-config-wizard");
    expect(wrapper.get("[data-testid='nx-page-crumb'] a").attributes("href")).toBe("/p4/product-config-wizard");
    expect(wrapper.get("[data-testid='nx-page-crumb'] a").text()).toBe("产品配置向导");
    expect(wrapper.find("[data-testid='nx-data-table']").exists()).toBe(false);
  });

  it("links each template to its editor through NxDataTable", async () => {
    p1.formTemplates.mockResolvedValue([{ id: "tpl-1", code: "leave", name: "请假", status: "DRAFT", fields: [] }]);
    const wrapper = await mountList();
    const table = wrapper.get("[data-testid='nx-data-table']");
    expect(table.text()).toContain("请假");
    expect(wrapper.get("a[href='/p1/form-templates/tpl-1']").text()).toBe("编辑");
    expect(table.classes()).toContain("nx-table--compact");
    expect(wrapper.get("[data-testid='template-status-DRAFT']").text()).toBe("草稿");
    expect(wrapper.get("[data-testid='template-status-DRAFT']").attributes("data-tone")).toBe("neutral");
  });

  it("shows published badge tone and filter chips (CF-T1)", async () => {
    p1.formTemplates.mockResolvedValue([
      { id: "tpl-1", code: "leave", name: "请假", status: "DRAFT", fields: [{ key: "a" }], version: 1 },
      { id: "tpl-2", code: "expense", name: "报销", status: "PUBLISHED", fields: [], publishedVersion: 3 },
    ]);
    const wrapper = await mountList();
    expect(wrapper.get("[data-testid='template-list-summary']").text()).toContain("已发布 1");
    expect(wrapper.get("[data-testid='template-status-PUBLISHED']").text()).toBe("已发布");
    expect(wrapper.get("[data-testid='template-status-PUBLISHED']").attributes("data-tone")).toBe("success");
    expect(wrapper.get("a[href='/p1/form-templates/tpl-2']").text()).toBe("查看");
    await wrapper.get("[data-testid='template-filter-PUBLISHED']").trigger("click");
    expect(wrapper.find("[data-testid='template-status-DRAFT']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='template-status-PUBLISHED']").exists()).toBe(true);
    expect(wrapper.get("[data-testid='template-version']").text()).toBe("v3");
  });

  it("shows NxDataTable loading skeleton without inventing rows", async () => {
    let finishRequest;
    p1.formTemplates.mockReturnValue(new Promise((resolve) => { finishRequest = resolve; }));
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: "/p1/form-templates", component: FormTemplateListView },
      { path: "/p1/form-templates/new", component: { template: "<div>new</div>" } },
      { path: "/p4/product-config-wizard", component: { template: "<div>wizard</div>" } },
    ] });
    await router.push("/p1/form-templates");
    const wrapper = mount(FormTemplateListView, { global: { plugins: [router] } });
    expect(wrapper.get("[data-testid='nx-data-table-loading']").exists()).toBe(true);
    expect(wrapper.findAll("[data-testid='nx-skeleton']")).toHaveLength(3);
    expect(wrapper.find("[data-testid='nx-data-table']").exists()).toBe(false);
    finishRequest([]);
    await flushPromises();
    wrapper.unmount();
  });

  it("surfaces load failures with the table error S shell", async () => {
    p1.formTemplates.mockRejectedValueOnce(Object.assign(new Error("catalog down"), { traceId: "tr-dt1" }));
    const wrapper = await mountList();
    const err = wrapper.get("[data-testid='nx-data-table-error']");
    expect(err.text()).toContain("表单模板暂时无法加载");
    expect(err.text()).toContain("catalog down");
    p1.formTemplates.mockResolvedValueOnce([{ id: "tpl-1", code: "leave", name: "请假", status: "DRAFT", fields: [] }]);
    await err.get("button").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='nx-data-table']").text()).toContain("请假");
  });
});
