/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  getForm: vi.fn(),
  submitStart: vi.fn(),
  listTodos: vi.fn(),
  getTodo: vi.fn(),
  approveTodo: vi.fn(),
  rejectTodo: vi.fn(),
  getLedger: vi.fn(),
  listFields: vi.fn(),
  listRecords: vi.fn(),
  persons: vi.fn(),
  check: vi.fn(),
  notifyList: vi.fn(),
  notifyRead: vi.fn(),
}));

vi.mock("../../api/p3", () => ({ p3: mocks }));
vi.mock("../../api/client", () => ({
  p2: { persons: mocks.persons },
  perm: { check: mocks.check },
  notify: { list: mocks.notifyList, read: mocks.notifyRead },
}));
vi.mock("../../api/session", () => ({
  session: { contextId: "ctx-t3gh", personId: "person-1", lastTraceId: "" },
}));

import FormStudioView from "./FormStudioView.vue";
import TodoInboxView from "./TodoInboxView.vue";
import NotifyInboxView from "./NotifyInboxView.vue";
import LedgerDetailView from "./LedgerDetailView.vue";

function routes() {
  return [
    { path: "/p3/forms", component: { template: "<div />" } },
    { path: "/p3/forms/:id", component: FormStudioView },
    { path: "/p3/todos", component: TodoInboxView },
    { path: "/p3/todos/:id", component: TodoInboxView },
    { path: "/p3/notifications", component: NotifyInboxView },
    { path: "/p3/ledgers", component: { template: "<div />" } },
    { path: "/p3/ledgers/:id", component: LedgerDetailView },
    { path: "/p3/ledgers/:id/records/:recordId", component: { template: "<div />" } },
    { path: "/p3/audit", component: { template: "<div />" } },
  ];
}

async function mountAt(path, component) {
  const router = createRouter({ history: createMemoryHistory(), routes: routes() });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(component, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("T3-G sequential approval", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.persons.mockResolvedValue([]);
    mocks.check.mockResolvedValue({ allowed: true });
  });

  it("starts approval and exposes todo and record links", async () => {
    mocks.getForm.mockResolvedValue({
      id: "form-1",
      name: "请假单",
      draft_status: "PUBLISHED",
      running_version: 1,
      running_fields: [{ id: "title", code: "title", name: "标题", type: "text", required: true }],
    });
    mocks.submitStart.mockResolvedValue({
      todo: { id: "todo-1" },
      ledger_id: "ledger-1",
      record_id: "record-1",
      trace_id: "trace-1",
    });

    const { wrapper } = await mountAt("/p3/forms/form-1", FormStudioView);
    await wrapper.find('input[type="text"]').setValue("请假一天");
    await wrapper.get('[data-testid="form-fill-submit"]').trigger("submit");
    await flushPromises();

    expect(mocks.submitStart).toHaveBeenCalledWith("form-1", expect.objectContaining({
      values: { title: "请假一天" },
      process_key: "nexusSequentialApproval",
      assignee_ref: "person-1",
    }));
    expect(wrapper.get('[data-testid="approval-open-todo"]').attributes("href")).toContain("/p3/todos/todo-1");
    expect(wrapper.get('[data-testid="approval-open-record"]').attributes("href"))
      .toBe("/p3/ledgers/ledger-1/records/record-1");
  });

  it.each([
    ["approve", "APPROVED", "同意"],
    ["reject", "REJECTED", "驳回"],
  ])("handles %s and closes the pending todo", async (action, status, label) => {
    const todo = {
      id: "todo-1",
      instance_id: "instance-1",
      title: "人工审批",
      biz_no: "record-1",
      status: "PENDING",
    };
    mocks.listTodos.mockResolvedValue({ items: [todo] });
    mocks.getTodo.mockResolvedValue(todo);
    mocks[`${action}Todo`].mockResolvedValue({
      ...todo,
      status: action.toUpperCase(),
      approval_status: status,
      ledger_id: "ledger-1",
      record_id: "record-1",
    });

    const { wrapper } = await mountAt("/p3/todos", TodoInboxView);
    await wrapper.find("tbody tr").trigger("click");
    await flushPromises();
    await wrapper.get(`[data-testid="todo-${action}"]`).trigger("click");
    await flushPromises();

    expect(mocks[`${action}Todo`]).toHaveBeenCalledWith("todo-1", "");
    expect(wrapper.text()).toContain(`已${label}并办结`);
    expect(wrapper.get('[data-testid="todo-record-link"]').attributes("href"))
      .toBe("/p3/ledgers/ledger-1/records/record-1");
  });
});

describe("T3-H notification and query", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.check.mockResolvedValue({ allowed: true });
  });

  it("marks an approval notification read and opens the matching task box", async () => {
    mocks.notifyList.mockResolvedValue([{
      notification_id: "notification-1",
      template_code: "approval.task.assigned",
      title: "新的审批任务",
      body: "请处理",
      status: "DELIVERED",
      created_at: "2026-09-23T10:00:00Z",
    }]);
    mocks.notifyRead.mockResolvedValue({ status: "READ" });

    const { wrapper, router } = await mountAt("/p3/notifications", NotifyInboxView);
    await wrapper.get('[data-testid="notify-open-notification-1"]').trigger("click");
    await flushPromises();

    expect(mocks.notifyRead).toHaveBeenCalledWith("notification-1");
    expect(router.currentRoute.value.fullPath).toBe("/p3/todos?box=inbox");
  });

  it("queries ledger records by business field through the scoped list API", async () => {
    mocks.getLedger.mockResolvedValue({ id: "ledger-1", name: "客户", code: "customers", running_schema_version: 1 });
    mocks.listFields.mockResolvedValue({
      items: [{ id: "field-1", code: "title", name: "名称", type: "text" }],
      system: [],
    });
    mocks.listRecords
      .mockResolvedValueOnce({ items: [], total: 0 })
      .mockResolvedValueOnce({ items: [{ id: "record-1", title: "客户甲" }], total: 1 });

    const { wrapper } = await mountAt("/p3/ledgers/ledger-1", LedgerDetailView);
    await wrapper.get('[data-testid="record-query-title"]').setValue("客户甲");
    await wrapper.get('[data-testid="record-query"]').trigger("submit");
    await flushPromises();

    expect(mocks.listRecords).toHaveBeenLastCalledWith("ledger-1", { title: "客户甲" });
    expect(wrapper.get('[data-testid="record-query-total"]').text()).toContain("1");
    expect(wrapper.get('[data-testid="record-table"]').text()).toContain("客户甲");
  });
});
