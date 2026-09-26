/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FormStudioView from "./FormStudioView.vue";
import { p3 } from "../../api/p3";
import { p2 } from "../../api/client";
import { mapForm } from "../../api/p3-live";
import { clearToasts, useNxToastState } from "../../feedback/nxMessage";

vi.mock("../../api/p3", () => ({
  p3: {
    getForm: vi.fn(),
    submitStart: vi.fn(),
  },
}));
vi.mock("../../api/client", async (importOriginal) => ({ ...await importOriginal(), p2: { persons: vi.fn() } }));

describe("form studio visual states", () => {
  beforeEach(() => {
    clearToasts();
    vi.clearAllMocks();
  });

  it("preserves optional layout metadata in the existing binding mapper", () => {
    const options = { layout: { x: 0, y: 0, w: 6, h: 2 } };
    const form = mapForm({ bindings: [{ field_code: "reason", options }] });
    expect(form.draft_fields[0].options).toEqual(options);
    expect(mapForm({ bindings: [{ field_code: "old" }] }).draft_fields[0].options).toEqual([]);
  });
  it.each(["layout", "legacy", "invalid", "mixed", "empty"])("renders read-only %s preview with safe list fallback", async (mode) => {
    const fields = mode === "empty" ? [] : [
      { code: "reason", name: "申请事由", type: "text", options: { layout: { x: 0, y: 0, w: 6, h: 2 } } },
      { code: "date", name: "申请日期", type: "date", options: { layout: { x: 6, y: 0, w: 6, h: 2 } } },
    ];
    if (mode === "legacy") fields.forEach((field) => { delete field.options; });
    if (mode === "mixed") delete fields[1].options;
    if (mode === "invalid") fields[0].options.layout.x = -1;
    p3.getForm.mockResolvedValue({ name: "审批申请单", draft_status: "DRAFT", draft_fields: fields, running_fields: [] });
    p2.persons.mockResolvedValue([]);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: "/p3/forms/:id", component: FormStudioView }] });
    await router.push("/p3/forms/form1");
    const wrapper = mount(FormStudioView, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.findAll(".nx-tabs button")[1].trigger("click");
    const preview = wrapper.get("[data-testid='studio-field-preview']");
    expect(preview.classes().includes("layout-preview")).toBe(mode === "layout");
    expect(preview.findAll("input, textarea, button")).toHaveLength(0);
    if (mode === "layout") {
      expect(preview.findAll("p")[0].element.style.gridColumn).toBe("1 / span 6");
      expect(preview.findAll("p")[1].element.style.gridColumn).toBe("7 / span 6");
    } else if (mode === "empty") expect(preview.text()).toContain("暂无草稿字段");
    else expect(preview.findAll("[data-testid='studio-preview-field']").map((item) => item.get("strong").text())).toEqual(["申请事由", "申请日期"]);
    wrapper.unmount();
  });
  it("shows a loading skeleton, then a sequential publish guide and validation empty state", async () => {
    let resolveForm;
    p3.getForm.mockReturnValue(new Promise((resolve) => { resolveForm = resolve; }));
    p2.persons.mockResolvedValue([]);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/p3/forms/:id", component: FormStudioView }],
    });
    await router.push("/p3/forms/form1?tab=publish");
    const wrapper = mount(FormStudioView, { global: { plugins: [router] } });
    expect(wrapper.get("[data-testid='nx-skeleton']").attributes("aria-busy")).toBe("true");
    resolveForm({ name: "审批申请单", draft_status: "DRAFT", running_version: 0, draft_fields: [], running_fields: [] });
    await flushPromises();
    expect(wrapper.get(".steps").findAll("li")).toHaveLength(4);
    expect(wrapper.get(".steps").text()).toContain("草稿");
    expect(wrapper.get(".steps").text()).toContain("运行");
    expect(wrapper.get("[data-testid='nx-empty-title']").text()).toBe("尚未校验");
    expect(wrapper.text()).not.toContain("Live M0-07");
    expect(wrapper.find("[data-testid='nx-source-banner']").exists()).toBe(false);
  });

  it("shows NxResult and toast after submit without developer JSON in the user surface", async () => {
    p3.getForm.mockResolvedValue({
      name: "审批申请单",
      draft_status: "PUBLISHED",
      running_version: 1,
      draft_fields: [],
      running_fields: [{ id: "title", code: "title", name: "标题", type: "text", required: true }],
    });
    p2.persons.mockResolvedValue([]);
    p3.submitStart.mockResolvedValue({
      todo: { id: "todo-1" },
      ledger_id: "ledger-1",
      record_id: "record-1",
      trace_id: "trace-ui2",
    });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/p3/forms/:id", component: FormStudioView },
        { path: "/p3/todos/:id", component: { template: "<div />" } },
        { path: "/p3/ledgers/:ledgerId/records/:recordId", component: { template: "<div />" } },
      ],
    });
    await router.push("/p3/forms/form1");
    const wrapper = mount(FormStudioView, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.find('input[type="text"]').setValue("事由");
    await wrapper.get('[data-testid="form-fill-submit"]').trigger("submit");
    await flushPromises();

    expect(wrapper.get("[data-testid='form-submit-result']").text()).toContain("已提交并启动审批");
    expect(wrapper.text()).not.toContain("submit-start");
    expect(wrapper.text()).not.toContain("trace-ui2");
    expect(wrapper.find("[data-testid='form-submit-dev-notes']").exists()).toBe(false);
    expect(wrapper.get('[data-testid="approval-open-todo"]').attributes("href")).toContain("/p3/todos/todo-1");
    expect(useNxToastState().items.at(-1)).toMatchObject({
      tone: "success",
      message: "已提交并启动审批",
      traceId: "trace-ui2",
    });
  });
});
