import { describe, expect, it } from "vitest";
import { todoStatus } from "./todoStatus";

describe("todo status vocabulary (N2)", () => {
  it("gives every box the same label and tone for the same outcome", () => {
    expect(todoStatus("APPROVED")).toEqual({ label: "已通过", tone: "success" });
    expect(todoStatus("approve")).toEqual({ label: "已同意", tone: "success" });
    expect(todoStatus("REJECTED")).toEqual({ label: "已驳回", tone: "danger" });
    expect(todoStatus("REJECT")).toEqual(todoStatus("REJECTED"));
    expect(todoStatus("PENDING")).toEqual({ label: "待处理", tone: "warning" });
    expect(todoStatus("RUNNING")).toEqual({ label: "进行中", tone: "info" });
    expect(todoStatus("IN_APPROVAL")).toEqual({ label: "审批中", tone: "info" });
  });

  it("does not paint withdrawn or done items as failures", () => {
    expect(todoStatus("WITHDRAWN")).toEqual({ label: "已撤回", tone: "neutral" });
    expect(todoStatus("DONE")).toEqual({ label: "已办结", tone: "neutral" });
  });

  it("falls back honestly for missing or unknown codes", () => {
    expect(todoStatus(null)).toEqual({ label: "暂无", tone: "neutral" });
    expect(todoStatus("", "进行中")).toEqual({ label: "进行中", tone: "info" });
    expect(todoStatus("SUSPENDED")).toEqual({ label: "SUSPENDED", tone: "neutral" });
  });
});
