/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { addDraftNode, createBlankDraft, parseDraftXml, serializeDraftXml } from "./processDraft";

describe("process draft editing", () => {
  it("round-trips basic BPMN nodes, user-task assignee references, and conditional flows", () => {
    const definition = {
      key: "approval",
      name: "审批流程",
      nodes: [
        { id: "start", name: "开始", type: "startEvent" },
        { id: "approve", name: "审批", type: "userTask", assignee: "${manager}" },
        { id: "finish", name: "完成", type: "endEvent" },
      ],
      flows: [{ id: "toFinish", source_ref: "approve", target_ref: "finish", condition: "${ok}" }],
    };
    const reparsed = parseDraftXml(serializeDraftXml(definition));
    expect(reparsed).toMatchObject({ key: "approval", name: "审批流程" });
    expect(reparsed.nodes[1]).toMatchObject({ id: "approve", name: "审批", type: "userTask", assignee: "${manager}" });
    expect(reparsed.flows[0]).toMatchObject({ id: "toFinish", condition: "${ok}" });
  });

  it("creates a simple editable draft and inserts only supported node kinds", () => {
    const draft = createBlankDraft("new-flow");
    const expanded = addDraftNode(draft, "userTask");
    expect(expanded.nodes.map((node) => node.type)).toEqual(["startEvent", "userTask", "userTask", "endEvent"]);
    expect(expanded.flows.some((flow) => flow.source_ref === "approveTask" && flow.target_ref === "userTask")).toBe(true);
    expect(() => addDraftNode(draft, "exclusiveGateway")).toThrow("仅支持");
  });
});
