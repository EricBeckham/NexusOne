import { describe, expect, it } from "vitest";
import {
  menuPayload,
  menuProblems,
  modulePayload,
  moduleProblems,
  moveByIndex,
  moveSibling,
  orderedMenu,
} from "./portalConfig";

const group = { id: "g", parent_id: null, label: "办公", entry_key: "", section: "办事", sort: 0, hidden: false };
const leave = { id: "a", parent_id: "g", label: "请假申请", entry_key: "fn:leave:leave_apply", section: "", sort: 0, hidden: false };
const expense = { id: "b", parent_id: "g", label: "报销", entry_key: "fn:expense:expense_apply", section: "", sort: 1, hidden: true };

describe("PLC4 portal config", () => {
  it("rejects a blank label and a malformed entry key", () => {
    expect(menuProblems([group, { ...leave, label: "  ", entry_key: "leave" }])).toEqual(["label_required", "invalid_entry_key"]);
  });

  it("rejects an unknown parent and a duplicate module code", () => {
    expect(menuProblems([{ ...leave, parent_id: "missing" }])).toEqual(["unknown_parent"]);
    expect(moduleProblems([
      { code: "Todos", title: "待办", sort: 0, hidden: false },
      { code: "todos", title: "", sort: 1, hidden: false },
      { code: "todos", title: "通知", sort: 2, hidden: false },
    ])).toEqual(["invalid_code", "title_required", "duplicate_code"]);
  });

  it("moves an entry among its siblings and keeps the group in front", () => {
    const moved = moveSibling([group, leave, expense], "b", -1);
    expect(orderedMenu(moved).map((row) => row.item.id)).toEqual(["g", "b", "a"]);
    expect(menuPayload(moved).items.find((item) => item.id === "b")).toMatchObject({
      parent_id: "g",
      entry_key: "fn:expense:expense_apply",
      hidden: true,
      sort: 0,
    });
  });

  it("reorders home modules by the displayed index", () => {
    const modules = [
      { code: "todos", title: "待办", sort: 0, hidden: false },
      { code: "notices", title: "通知", sort: 1, hidden: false },
    ];
    expect(modulePayload(moveByIndex(modules, 1, -1)).modules.map((item) => item.code)).toEqual(["notices", "todos"]);
  });
});
