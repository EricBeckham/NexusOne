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
    ],
  });
  await router.push("/p1/form-templates");
  const wrapper = mount(FormTemplateListView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("form template list", () => {
  beforeEach(() => p1.formTemplates.mockReset());

  it("shows 暂无 when the platform has no templates", async () => {
    p1.formTemplates.mockResolvedValue([]);
    const wrapper = await mountList();
    expect(wrapper.get("[data-testid='nx-empty-title']").text()).toBe("还没有表单模板");
    expect(wrapper.get("[data-testid='nx-empty-next'] a").attributes("href")).toBe("/p1/form-templates/new");
    expect(wrapper.find("[data-testid='template-list']").exists()).toBe(false);
  });

  it("links each template to its editor", async () => {
    p1.formTemplates.mockResolvedValue([{ id: "tpl-1", code: "leave", name: "请假", status: "DRAFT", fields: [] }]);
    const wrapper = await mountList();
    expect(wrapper.get("[data-testid='template-list']").text()).toContain("请假");
    expect(wrapper.get("a[href='/p1/form-templates/tpl-1']").text()).toBe("编辑");
    expect(wrapper.get("[data-testid='template-list']").classes()).toContain("nx-table--compact");
    expect(wrapper.get("[data-testid='nx-status-tag']").text()).toBe("草稿");
  });

  it("keeps the table header and shows row skeletons while loading", async () => {
    let finishRequest;
    p1.formTemplates.mockReturnValue(new Promise((resolve) => { finishRequest = resolve; }));
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: "/p1/form-templates", component: FormTemplateListView },
      { path: "/p1/form-templates/new", component: { template: "<div>new</div>" } },
    ] });
    await router.push("/p1/form-templates");
    const wrapper = mount(FormTemplateListView, { global: { plugins: [router] } });
    expect(wrapper.get("[data-testid='template-list'] thead").text()).toContain("名称");
    expect(wrapper.findAll("[data-testid='nx-skeleton']")).toHaveLength(3);
    finishRequest([]);
    await flushPromises();
    wrapper.unmount();
  });
});
