import { describe, expect, it } from "vitest";
import { DEFAULT_MOBILE_HOME } from "../auth/safeReturn.js";
import { resolveNotifyJump, safeNotifyJump, TEMPLATE_JUMPS } from "./notifyJump.js";

describe("resolveNotifyJump", () => {
  it("maps approval templates to todo boxes", () => {
    expect(resolveNotifyJump({ template_code: "approval.task.assigned" })).toBe("/m/p3/todos?box=inbox");
    expect(resolveNotifyJump({ template_code: "approval.task.approved" })).toBe("/m/p3/todos?box=initiated");
    expect(resolveNotifyJump({ template_code: "approval.task.rejected" })).toBe(
      TEMPLATE_JUMPS["approval.task.rejected"],
    );
  });

  it("prefers explicit safe /m path", () => {
    expect(resolveNotifyJump({ jump: "/m/p3/query?ledger=abc" })).toBe("/m/p3/query?ledger=abc");
  });

  it("drops desktop, off-site, token, and tenant_id jumps", () => {
    expect(resolveNotifyJump({ jump: "/p3/todos" })).toBe(DEFAULT_MOBILE_HOME);
    expect(resolveNotifyJump({ jump: "https://evil.example/m/p3/home" })).toBe(DEFAULT_MOBILE_HOME);
    expect(resolveNotifyJump({ jump: "/m/p3/todos?access_token=x" })).toBe(DEFAULT_MOBILE_HOME);
    expect(resolveNotifyJump({ jump: "/m/p3/home?tenant_id=1" })).toBe(DEFAULT_MOBILE_HOME);
  });

  it("uses todo / form / list / file ids from variables", () => {
    expect(resolveNotifyJump({ variables: { todo_id: "task-1" } })).toBe("/m/p3/todos/task-1");
    expect(resolveNotifyJump({ form_id: "form-1" })).toBe("/m/p3/forms/form-1");
    expect(resolveNotifyJump({ ledger_id: "led-1", record_id: "rec-1" })).toBe("/m/p3/lists/led-1/records/rec-1");
    expect(resolveNotifyJump({ fileId: "file-1" })).toBe("/m/p3/files/file-1");
  });

  it("rejects unsafe ids", () => {
    expect(resolveNotifyJump({ todo_id: "../p3" })).toBe(DEFAULT_MOBILE_HOME);
  });
});

describe("safeNotifyJump", () => {
  it("keeps deep-link query on mobile paths", () => {
    expect(safeNotifyJump("/m/p3/todos?box=inbox")).toBe("/m/p3/todos?box=inbox");
  });
});
