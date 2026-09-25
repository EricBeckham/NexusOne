/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  listTodos: vi.fn(),
  getTodo: vi.fn(),
  approveTodo: vi.fn(),
  rejectTodo: vi.fn(),
  withdrawTodo: vi.fn(),
  listProductApplications: vi.fn(),
}));
vi.mock("../../api/p3", () => ({ p3: mocks }));
vi.mock("../../api/session", () => ({ session: { contextId: "ctx-1", lastTraceId: "" } }));

import TodoInboxView from "./TodoInboxView.vue";

const ROWS = [
  { id: "todo-1", title: "请假审批", biz_no: "LEAVE-1", status: "PENDING", arrived_at: "2026-01-02" },
  { id: "todo-2", title: "报销审批", biz_no: "EXP-7", status: "PENDING", arrived_at: "2026-01-03" },
];

async function mountAt(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p3/todos", component: TodoInboxView }, { path: "/p4/installations", component: { template: "<div />" } }],
  });
  await router.push(path);
  const wrapper = mount(TodoInboxView, { global: { plugins: [router] } });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

describe("todo inbox visual standard (Q10)", () => {
  beforeEach(() => {
    for (const fn of Object.values(mocks)) fn.mockReset();
    mocks.listTodos.mockResolvedValue({ items: ROWS });
    // Like the live API: an unknown id (e.g. a product application) is a 404, and the view falls back to the list row.
    mocks.getTodo.mockImplementation(async (id) => {
      const row = ROWS.find((item) => item.id === id);
      if (!row) throw Object.assign(new Error("todo not found"), { status: 404 });
      return row;
    });
    mocks.listProductApplications.mockResolvedValue({ items: [] });
  });

  it("filters on the shared FilterBar, keeps ?q= in the URL and restores it", async () => {
    const wrapper = await mountAt("/p3/todos?box=inbox");
    const bar = wrapper.get("[data-testid='todo-filter-input']").element.closest("form");
    expect(bar.getAttribute("role")).toBe("search");
    expect(wrapper.text()).toContain("共 2 条");
    await wrapper.get("[data-testid='todo-filter-input']").setValue("EXP");
    await flushPromises();
    expect(wrapper.findAll("[data-testid='todo-row']")).toHaveLength(1);
    expect(wrapper.router.currentRoute.value.query).toEqual({ box: "inbox", q: "EXP" });
    await wrapper.get("[data-testid='todo-filter-reset']").trigger("click");
    await flushPromises();
    expect(wrapper.router.currentRoute.value.query.q).toBeUndefined();
    wrapper.unmount();

    const restored = await mountAt("/p3/todos?box=inbox&q=LEAVE");
    expect(restored.get("[data-testid='todo-filter-input']").element.value).toBe("LEAVE");
    expect(restored.findAll("[data-testid='todo-row']").map((row) => row.get(".cell-title").text())).toEqual(["请假审批"]);
  });

  it("uses the dense shared table, opens rows by keyboard and marks the selected one", async () => {
    const wrapper = await mountAt("/p3/todos?box=inbox");
    expect(wrapper.get("[data-testid='todo-table']").classes()).toContain("nx-table");
    const [first] = wrapper.findAll("[data-testid='todo-row']");
    expect(first.attributes("tabindex")).toBe("0");
    await first.trigger("keydown", { key: "Enter" });
    await flushPromises();
    expect(mocks.getTodo).toHaveBeenCalledWith("todo-1");
    expect(wrapper.get("[data-testid='todo-row'].pick").attributes("aria-selected")).toBe("true");
    expect(wrapper.get("[data-testid='todo-approve']").classes()).toContain("nx-btn--primary");
    expect(wrapper.get("[data-testid='todo-reject']").classes()).toContain("nx-btn--danger");
  });

  it("gives 我发起 its own headers, short references and a secondary 撤回 (Q11)", async () => {
    const started = { id: "inst-9", title: "demoSimpleApproval", process: "demoSimpleApproval", biz_no: "product-application:01a0daf7-6532-7339-8c16-be477184001b", status: "RUNNING", instance_id: "inst-9", arrived_at: "2026-01-04" };
    mocks.listTodos.mockResolvedValue({ items: [started] });
    mocks.getTodo.mockResolvedValue(started);
    const wrapper = await mountAt("/p3/todos?box=initiated");
    expect(wrapper.findAll("thead th").map((th) => th.text()).slice(0, 3)).toEqual(["流程", "业务单号", "发起时间"]);
    const no = wrapper.get("[data-testid='todo-row-no']");
    expect(no.text()).toBe("01a0daf7…");
    expect(no.attributes("title")).toBe(started.biz_no);
    await wrapper.get("[data-testid='todo-row']").trigger("click");
    await flushPromises();
    expect(wrapper.get(".detail").text()).toContain(started.biz_no);
    expect(wrapper.get("[data-testid='todo-withdraw']").classes()).toContain("nx-btn--default");
    expect(wrapper.find("[data-testid='todo-approve']").exists()).toBe(false);
  });

  it("shows 我的申请 with application headers and an application-shaped detail (Q11)", async () => {
    mocks.listProductApplications.mockResolvedValue({
      items: [{ id: "app-1", title: "产品申请", biz_no: "01a0daf7-6532-7339-8c16-be477184001b", status: "APPROVED", comment: "可以", arrived_at: "2026-01-02" }],
    });
    const wrapper = await mountAt("/p3/todos?box=requests");
    expect(wrapper.findAll("thead th").map((th) => th.text()).slice(0, 3)).toEqual(["申请", "申请编号", "提交时间"]);
    expect(wrapper.get("[data-testid='todo-row-no']").text()).toBe("01a0daf7…");
    await wrapper.get("[data-testid='todo-row']").trigger("click");
    await flushPromises();
    const meta = wrapper.get("[data-testid='request-detail-meta']");
    expect(meta.text()).toContain("申请编号");
    expect(meta.text()).toContain("已通过");
    expect(wrapper.get(".detail").text()).not.toContain("流程实例");
    expect(wrapper.get("[data-testid='application-comment']").text()).toContain("可以");
    expect(wrapper.find(".detail-actions").exists()).toBe(false);
  });
});