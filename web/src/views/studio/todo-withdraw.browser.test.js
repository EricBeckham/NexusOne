/**
 * @vitest-environment happy-dom
 *
 * G7 leftover: click the existing Vue data-testid=todo-withdraw control
 * for leave / expense / travel. Stubs the live P3 adapter (no JAR, no Docker).
 * Not a G7 three-type product close. Does not reopen G18.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { clearToasts, useNxToastState } from "../../feedback/nxMessage";
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

const TYPES = [
  {
    type: "leave",
    id: "todo-leave-1",
    instance_id: "inst-leave-1",
    title: "请假审批",
    process: "leave",
    biz_no: "LEAVE-G7-UI-001",
  },
  {
    type: "expense",
    id: "todo-expense-1",
    instance_id: "inst-expense-1",
    title: "报销审批",
    process: "expense",
    biz_no: "EXPENSE-G7-UI-001",
  },
  {
    type: "travel",
    id: "todo-travel-1",
    instance_id: "inst-travel-1",
    title: "出差审批",
    process: "travel",
    biz_no: "TRAVEL-G7-UI-001",
  },
];

function rowOf(spec, status = "RUNNING") {
  return {
    id: spec.id,
    instance_id: spec.instance_id,
    title: spec.title,
    process: spec.process,
    biz_no: spec.biz_no,
    arrived_at: "2026-09-22T17:00:00Z",
    status,
  };
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
    getTodo.mockRejectedValue(new Error("use list row"));
    clearToasts();
  });

  it.each(TYPES)("clicks data-testid=todo-withdraw on pending $type", async (spec) => {
    listTodos.mockResolvedValue({ items: [rowOf(spec)] });
    withdrawTodo.mockImplementation(async () => {
      listTodos.mockResolvedValue({ items: [rowOf(spec, "WITHDRAWN")] });
      return {
        ...rowOf(spec, "WITHDRAWN"),
        approval_status: "WITHDRAWN",
      };
    });

    const wrapper = await mountInbox();
    expect(wrapper.find('[data-testid="todo-withdraw"]').exists()).toBe(false);

    await wrapper.get("[data-testid=todo-table] tbody tr").trigger("click");
    await flushPromises();

    const button = wrapper.get('[data-testid="todo-withdraw"]');
    expect(button.text()).toBe("撤回");
    await button.trigger("click");
    await flushPromises();

    expect(withdrawTodo).toHaveBeenCalledTimes(1);
    expect(withdrawTodo).toHaveBeenCalledWith(spec.id, "", spec.instance_id);
    // S6: the result is a shared toast; its status still comes from the server's approval_status (WITHDRAWN).
    expect(useNxToastState().items.at(-1)).toMatchObject({ tone: "success", message: "已撤回并办结，审批状态：已撤回" });
    expect(wrapper.text()).not.toContain("withdraw");
    expect(wrapper.find('[data-testid="todo-withdraw"]').exists()).toBe(false);
  });

  it("walks leave + expense + travel on the same initiated box", async () => {
    const pending = TYPES.map((spec) => rowOf(spec));
    const withdrawn = new Set();
    listTodos.mockImplementation(async () => ({
      items: pending.map((row) =>
        withdrawn.has(row.process) ? { ...row, status: "WITHDRAWN" } : row,
      ),
    }));
    withdrawTodo.mockImplementation(async (_id, _comment, instanceId) => {
      const spec = TYPES.find((item) => item.instance_id === instanceId);
      withdrawn.add(spec.process);
      return { ...rowOf(spec, "WITHDRAWN"), approval_status: "WITHDRAWN" };
    });

    const wrapper = await mountInbox();
    expect(wrapper.findAll("[data-testid=todo-table] tbody tr")).toHaveLength(3);

    for (const spec of TYPES) {
      const row = wrapper.findAll("[data-testid=todo-table] tbody tr")
        .find((item) => item.text().includes(spec.title));
      expect(row).toBeTruthy();
      await row.trigger("click");
      await flushPromises();
      const button = wrapper.get('[data-testid="todo-withdraw"]');
      await button.trigger("click");
      await flushPromises();
      expect(useNxToastState().items.at(-1)?.message).toBe("已撤回并办结，审批状态：已撤回");
    }

    expect(withdrawTodo).toHaveBeenCalledTimes(3);
    expect(withdrawTodo.mock.calls.map((call) => call[2])).toEqual(
      TYPES.map((spec) => spec.instance_id),
    );
  });
});
