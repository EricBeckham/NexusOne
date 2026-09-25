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
    mocks.getTodo.mockImplementation(async (id) => ROWS.find((row) => row.id === id));
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
});