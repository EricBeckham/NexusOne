/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { addDraftNode, createBlankDraft, parseDraftXml, removeExclusiveGateway, serializeDraftXml } from "./processDraft";

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
    expect(() => addDraftNode(draft, "parallelGateway")).toThrow("仅支持");
    // Z6: a gateway needs an anchor node (start or task).
    expect(() => addDraftNode(draft, "exclusiveGateway")).toThrow("请先选择");
    expect(() => addDraftNode(draft, "exclusiveGateway", { after: "end" })).toThrow("请先选择");
  });

  it("inserts an exclusive gateway with two branches that rejoin, and round-trips it (Z6)", () => {
    const draft = createBlankDraft("branchy");
    const branched = addDraftNode(draft, "exclusiveGateway", { after: "approveTask" });
    expect(branched.nodes.map((node) => `${node.id}:${node.type}`)).toEqual([
      "start:startEvent",
      "approveTask:userTask",
      "gateway:exclusiveGateway",
      "branch_task:userTask",
      "end:endEvent",
    ]);
    const edges = branched.flows.map((flow) => `${flow.source_ref}>${flow.target_ref}`).sort();
    expect(edges).toEqual(["approveTask>gateway", "branch_task>end", "gateway>branch_task", "gateway>end", "start>approveTask"].sort());
    expect(branched.flows.every((flow) => flow.condition === null)).toBe(true);
    expect(draft.nodes).toHaveLength(3); // the input draft is not mutated

    const reparsed = parseDraftXml(serializeDraftXml(branched));
    expect(reparsed.nodes.find((node) => node.id === "gateway")).toMatchObject({ type: "exclusiveGateway", name: "条件分支" });
    expect(reparsed.flows).toHaveLength(5);

    // A node that already branches cannot take a second gateway; a second anchor gets a fresh id.
    expect(() => addDraftNode(branched, "exclusiveGateway", { after: "gateway" })).toThrow("请先选择");
    const twice = addDraftNode(branched, "exclusiveGateway", { after: "start" });
    expect(twice.nodes.filter((node) => node.type === "exclusiveGateway").map((node) => node.id)).toEqual(["gateway_2", "gateway"]);
    expect(twice.nodes.filter((node) => node.id.startsWith("branch_task")).map((node) => node.id)).toEqual(["branch_task_2", "branch_task"]);
  });

  it("removes an exclusive gateway, keeping the first branch and dropping the others (Z6)", () => {
    const branched = addDraftNode(createBlankDraft("branchy"), "exclusiveGateway", { after: "approveTask" });
    const restored = removeExclusiveGateway(branched, "gateway");
    expect(restored.nodes.map((node) => node.id)).toEqual(["start", "approveTask", "end"]);
    expect(restored.flows.map((flow) => `${flow.source_ref}>${flow.target_ref}`).sort()).toEqual(["approveTask>end", "start>approveTask"]);
    expect(() => removeExclusiveGateway(branched, "approveTask")).toThrow("只能删除条件分支节点");
  });
});
