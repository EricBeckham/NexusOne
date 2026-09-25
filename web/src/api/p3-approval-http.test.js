/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { persist, session } from "./session";
import { approveTodo, rejectTodo, submitStart } from "./p3-live";

function response(path) {
  if (path.startsWith("/api/v1/process/tasks/")) {
    // Real ProcessController TodoView (SNAKE_CASE): no approval_status; instance_status + outcome instead.
    const approved = path.endsWith("/approve");
    return {
      id: "todo-9",
      instance_id: "instance-9",
      task_name: "审批",
      status: "DONE",
      outcome: approved ? "APPROVE" : "REJECT",
      business_key: "product-application:app-9",
      instance_status: approved ? "APPROVED" : "REJECTED",
      comment: "I8 演示审批通过",
    };
  }
  if (path === "/api/v1/p3/path/submit-start") {
    return {
      ledger_id: "ledger-1",
      record_id: "record-1",
      approval_status: "IN_APPROVAL",
      process: { id: "instance-1", status: "RUNNING" },
      todo: { id: "todo-1", status: "PENDING" },
    };
  }
  const approved = path.endsWith("/approve");
  return {
    ledger_id: "ledger-1",
    record_id: "record-1",
    approval_status: approved ? "APPROVED" : "REJECTED",
    process: { id: "instance-1", status: approved ? "APPROVED" : "REJECTED" },
    todo: { id: "todo-1", status: "DONE", outcome: approved ? "APPROVE" : "REJECT" },
  };
}

describe("T3-G approval HTTP contract", () => {
  beforeEach(() => {
    session.accessToken = "stub.t3g";
    session.contextId = "t3g-context";
    persist();
    vi.stubGlobal("fetch", vi.fn(async (path) => ({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(response(String(path))),
    })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("starts one sequential approval and closes it through approve or reject", async () => {
    const started = await submitStart("form-1", {
      values: { title: "请假" },
      assignee_type: "PERSON",
      assignee_ref: "person-1",
    });
    const approved = await approveTodo("todo-1");
    const rejected = await rejectTodo("todo-2");

    expect(fetch.mock.calls.map(([path, init]) => [path, init.method])).toEqual([
      ["/api/v1/p3/path/submit-start", "POST"],
      ["/api/v1/p3/path/tasks/todo-1/approve", "POST"],
      ["/api/v1/p3/path/tasks/todo-2/reject", "POST"],
    ]);
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(expect.objectContaining({
      form_id: "form-1",
      process_key: "nexusSequentialApproval",
      assignee_type: "PERSON",
      assignee_ref: "person-1",
    }));
    expect(fetch.mock.calls[0][1].headers["Idempotency-Key"]).toBeTruthy();
    for (const [, init] of fetch.mock.calls) {
      expect(init.headers["X-Nexus-Context-Id"]).toBe("t3g-context");
      expect(init.headers["X-Tenant-Id"]).toBeUndefined();
      expect(init.headers["X-Trace-Id"]).toBeTruthy();
    }
    expect(started.approval_status).toBe("IN_APPROVAL");
    expect(approved.approval_status).toBe("APPROVED");
    expect(rejected.approval_status).toBe("REJECTED");
  });

  it("derives the status of a product-application decision from the TodoView instance status (M1)", async () => {
    const approved = await approveTodo("todo-9", "I8 演示审批通过", "product-application:app-9");
    const rejected = await rejectTodo("todo-9", "", "product-application:app-9");
    expect(fetch.mock.calls.map(([path]) => path)).toEqual([
      "/api/v1/process/tasks/todo-9/approve",
      "/api/v1/process/tasks/todo-9/reject",
    ]);
    expect(approved.approval_status).toBe("APPROVED");
    expect(approved.status).toBe("APPROVED");
    expect(rejected.approval_status).toBe("REJECTED");
    expect(rejected.status).toBe("REJECTED");
  });

  it("maps done-list TodoView status from instance_status (N5 alignment)", async () => {
    const { mapTodo, normalizeDecisionOutcome } = await import("./p3-live");
    expect(normalizeDecisionOutcome("APPROVE")).toBe("APPROVED");
    expect(normalizeDecisionOutcome("REJECT")).toBe("REJECTED");
    const done = mapTodo({
      id: "todo-9",
      status: "DONE",
      outcome: "APPROVE",
      instance_status: "APPROVED",
      task_name: "审批",
      business_key: "product-application:app-9",
    }, "done");
    expect(done.status).toBe("APPROVED");
    expect(done.approval_status).toBe("APPROVED");
  });

  it("sends the comment and uses the process API for a product application", async () => {
    await approveTodo("todo-1", "  同意这条  ", "product-application:app-1");
    await rejectTodo("todo-2", "驳回", "ledger-1");
    const [productPath, productInit] = fetch.mock.calls[0];
    const [ledgerPath, ledgerInit] = fetch.mock.calls[1];
    expect(productPath).toBe("/api/v1/process/tasks/todo-1/approve");
    expect(JSON.parse(productInit.body)).toEqual({ comment: "同意这条" });
    expect(ledgerPath).toBe("/api/v1/p3/path/tasks/todo-2/reject");
    expect(JSON.parse(ledgerInit.body)).toEqual({ comment: "驳回" });
    expect(productInit.headers["X-Tenant-Id"]).toBeUndefined();
  });
});
