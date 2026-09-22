/**
 * @vitest-environment happy-dom
 *
 * G7 leftover: click the existing Vue data-testid=todo-withdraw control.
 * Stubs the live P3 adapter (no JAR, no Docker). Not a G7 three-type close.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const { listTodos, getTodo, withdrawTodo, approveTodo, rejectTodo } = vi.hoisted(() => ({
  listTodos: vi.fn(),
  getTodo: vi.fn(),
  withdrawTodo: vi.fn(),
  approveTodo: vi.fn(),
  rejectTodo: vi.fn(),
}));

vi.mock("../../api/p3", () => ({
  p3: {
    listTodos,
    getTodo,
    withdrawTodo,
    approveTodo,
    rejectTodo,
  },
}));

vi.mock("../../api/session", () => ({
  session: {
    contextId: "01a0c98a-1f3c-7f72-b724-6ac34746e981",
    lastTraceId: "",
  },
}));

import TodoInboxView from "./TodoInboxView.vue";

const leaveRow = {
  id: "todo-leave-1",
  instance_id: "inst-leave-1",
  title: "请假审批",
  process: "leave",
  biz_no: "LEAVE-G7-UI-001",
  arrived_at: "2026-09-22T17:00:00Z",
  status: "RUNNING",
};

function pendingItems() {
  return { items: [{ ...leaveRow }] };
}

function withdrawnItems() {
  return { items: [{ ...leaveRow, status: "WITHDRAWN" }] };
}

async function mountInbox() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p3/todos", component: TodoInboxView }],
  });
  await router.push("/p3/todos?box=initiated");
  await router.isReady();
  const wrapper = mount(TodoInboxView, {
    global: {
      plugins: [router],
    },
  });
  await flushPromises();
  return wrapper;
}

describe("todo-withdraw control", () => {
  beforeEach(() => {
    listTodos.mockReset();
    getTodo.mockReset();
    withdrawTodo.mockReset();
    listTodos.mockResolvedValue(pendingItems());
    getTodo.mockRejectedValue(new Error("use list row"));
    withdrawTodo.mockImplementation(async () => {
      listTodos.mockResolvedValue(withdrawnItems());
      return {
        ...leaveRow,
        status: "WITHDRAWN",
        approval_status: "WITHDRAWN",
      };
    });
  });

  it("clicks data-testid=todo-withdraw on a pending initiated leave row", async () => {
    const wrapper = await mountInbox();
    expect(wrapper.find('[data-testid="todo-withdraw"]').exists()).toBe(false);

    await wrapper.get("[data-testid=todo-table] tbody tr").trigger("click");
    await flushPromises();

    const button = wrapper.get('[data-testid="todo-withdraw"]');
    expect(button.text()).toBe("撤回");
    await button.trigger("click");
    await flushPromises();

    expect(withdrawTodo).toHaveBeenCalledTimes(1);
    expect(withdrawTodo).toHaveBeenCalledWith("todo-leave-1", "", "inst-leave-1");
    expect(wrapper.text()).toContain("已 withdraw");
    expect(wrapper.text()).toContain("approval_status=WITHDRAWN");
    expect(wrapper.find('[data-testid="todo-withdraw"]').exists()).toBe(false);
  });
});
