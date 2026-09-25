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

async function mountInbox(query = "") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p3/todos", component: TodoInboxView },
      { path: "/p3/audit", component: { template: "<div />" } },
    ],
  });
  await router.push(`/p3/todos${query}`);
  const wrapper = mount(TodoInboxView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("todo comment and my requests", () => {
  beforeEach(() => {
    mocks.listTodos.mockReset();
    mocks.getTodo.mockReset();
    mocks.listProductApplications.mockReset();
    mocks.approveTodo.mockReset();
    mocks.getTodo.mockRejectedValue(new Error("missing"));
    mocks.listTodos.mockResolvedValue({ items: [] });
    mocks.listProductApplications.mockResolvedValue({ items: [] });
  });

  it("submits the inbox comment through the API", async () => {
    const row = { id: "todo-1", title: "审批", biz_no: "product-application:1", status: "PENDING", instance_id: "inst-1" };
    mocks.listTodos.mockResolvedValue({ items: [row] });
    mocks.getTodo.mockResolvedValue(row);
    mocks.approveTodo.mockResolvedValue({ id: "todo-1", approval_status: "APPROVED" });
    const wrapper = await mountInbox();
    await wrapper.get("tbody tr").trigger("click");
    await flushPromises();
    expect(wrapper.text()).not.toContain("仅本页");
    expect(wrapper.text()).not.toContain("意见字段 API 未提供");
    expect(wrapper.text()).toContain("意见一起提交 path API");
    await wrapper.get('[data-testid="todo-comment"]').setValue("同意这条");
    await wrapper.get('[data-testid="todo-approve"]').trigger("click");
    await flushPromises();
    expect(mocks.approveTodo).toHaveBeenCalledWith("todo-1", "同意这条", "product-application:1");
  });

  it("shows my request status and comment without a withdraw control", async () => {
    const wrapper = await mountInbox();
    const requests = wrapper.findAll("button").find((button) => button.text() === "我的申请");
    await requests.trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("暂无");

    mocks.listProductApplications.mockResolvedValue({
      items: [{ id: "app-1", title: "产品申请", biz_no: "app-1", status: "APPROVED", comment: "可以", arrived_at: "2026-01-02" }],
    });
    await requests.trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("通过");
    await wrapper.get("tbody tr").trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="application-comment"]').text()).toContain("可以");
    expect(wrapper.find('[data-testid="todo-withdraw"]').exists()).toBe(false);
  });
});
