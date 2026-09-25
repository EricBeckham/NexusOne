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
      { path: "/p4/installations", component: { template: "<div />" } },
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
    expect(wrapper.text()).not.toContain("path API");
    expect(wrapper.get("tbody tr").text()).toContain("待处理");
    expect(wrapper.get("tbody tr").text()).not.toContain("PENDING");
    expect(wrapper.text()).toContain("流程实例");
    expect(wrapper.text()).not.toContain("instance inst-1");
    await wrapper.get('[data-testid="todo-comment"]').setValue("同意这条");
    await wrapper.get('[data-testid="todo-approve"]').trigger("click");
    await flushPromises();
    expect(mocks.approveTodo).toHaveBeenCalledWith("todo-1", "同意这条", "product-application:1");
    expect(wrapper.text()).toContain("已同意并办结。审批状态：已通过（approval_status=APPROVED）");
    expect(wrapper.text()).not.toContain("approval_status=—");
  });

  it("gives every empty box a reason and a next step (M4)", async () => {
    const wrapper = await mountInbox();
    const empty = () => wrapper.get("[data-testid='todo-empty']");
    expect(empty().text()).toContain("选你为审批人后");
    expect(empty().get("[data-testid='todo-empty-action']").attributes("href")).toBe("/p4/installations");
    const tab = (label) => wrapper.findAll("button").find((button) => button.text() === label);

    await tab("我发起").trigger("click");
    await flushPromises();
    expect(empty().text()).toContain("从「我的已开通」发起申请后");
    expect(empty().get("[data-testid='todo-empty-action']").text()).toBe("去发起申请");

    await tab("已办").trigger("click");
    await flushPromises();
    expect(empty().text()).toContain("暂无已办");
    await empty().get("[data-testid='todo-empty-action']").trigger("click");
    await flushPromises();
    expect(wrapper.get(".nx-tabs button.active").text()).toBe("我的待办");
    expect(wrapper.findAll("td").some((cell) => cell.text() === "暂无")).toBe(false);
  });

  it("offers to clear a filter that matches nothing", async () => {
    mocks.listTodos.mockResolvedValue({ items: [{ id: "todo-1", title: "审批", biz_no: "product-application:1", status: "PENDING" }] });
    const wrapper = await mountInbox();
    await wrapper.get("input").setValue("不存在");
    expect(wrapper.get("[data-testid='todo-empty']").text()).toContain("没有匹配项");
    await wrapper.get("[data-testid='todo-empty-action']").trigger("click");
    expect(wrapper.find("[data-testid='todo-empty']").exists()).toBe(false);
    expect(wrapper.findAll("tbody tr")).toHaveLength(1);
  });

  it("shows my request status and comment without a withdraw control", async () => {
    const wrapper = await mountInbox();
    expect(wrapper.get("[data-testid='todo-empty']").text()).toContain("暂无待办");
    expect(wrapper.text()).not.toContain("submit-start");
    expect(wrapper.find("[data-testid='todo-table']").exists()).toBe(false);
    const requests = wrapper.findAll("button").find((button) => button.text() === "我的申请");
    await requests.trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='todo-empty']").text()).toContain("暂无申请");
    expect(wrapper.text()).toContain("从已开通产品发起后");
    expect(wrapper.get("[data-testid='todo-empty'] a").attributes("href")).toBe("/p4/installations");

    mocks.listProductApplications.mockResolvedValue({
      items: [{ id: "app-1", title: "产品申请", biz_no: "app-1", status: "APPROVED", comment: "可以", arrived_at: "2026-01-02" }],
    });
    await requests.trigger("click");
    await flushPromises();
    expect(wrapper.get("tbody [data-tone]").text()).toBe("已通过");
    expect(wrapper.get("tbody [data-tone]").attributes("data-tone")).toBe("success");

    expect(wrapper.get("thead").text()).toContain("提交时间");
    expect(wrapper.get("thead").text()).toContain("申请编号");
    await wrapper.get("tbody tr").trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="application-comment"]').text()).toContain("审批意见");
    expect(wrapper.get('[data-testid="application-comment"]').text()).toContain("可以");
    expect(wrapper.find('[data-testid="todo-withdraw"]').exists()).toBe(false);
  });
});
