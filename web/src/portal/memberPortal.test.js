import { describe, expect, it } from "vitest";
import { currentInstances } from "../api/portal";
import { visiblePortal } from "./memberPortal";

const menu = [
  { id: "group", label: "办公", entry_key: null, sort: 10, hidden: false },
  { id: "apply", parent_id: "group", label: "请假申请", entry_key: "fn:leave:leave_apply", sort: 20, hidden: false },
  { id: "view", parent_id: "group", label: "请假查看", entry_key: "fn:leave:leave_view", sort: 30, hidden: false },
  { id: "secret", label: "隐藏项", entry_key: "fn:leave:leave_apply", sort: 40, hidden: true },
];

describe("PLC5 member portal filter", () => {
  it("keeps a readable menu entry and drops the rest, including an empty instance query", () => {
    const view = visiblePortal({
      menu,
      modules: [
        { code: "todos", title: "待办", sort: 2, hidden: false },
        { code: "draft", title: "草稿", sort: 1, hidden: true },
      ],
      readable: ["fn:leave:leave_apply"],
      instances: currentInstances(),
    });
    expect(currentInstances()).toEqual([]);
    expect(view.entries.map((item) => item.label)).toEqual(["办公", "请假申请"]);
    expect(view.modules.map((item) => item.code)).toEqual(["todos"]);
  });

  it("adds nothing from instances until the query returns rows", () => {
    const empty = visiblePortal({ menu: [], modules: [], readable: ["fn:leave:leave_apply"], instances: [] });
    expect(empty.entries).toEqual([]);
    const installed = visiblePortal({
      menu: [],
      modules: [],
      readable: ["fn:leave:leave_apply"],
      instances: [{ status: "INSTALLED", entries: ["fn:leave:leave_apply"] }],
    });
    expect(installed.entries.map((item) => item.entry_key)).toEqual(["fn:leave:leave_apply"]);
  });
});
