/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import {
  addDraftNode, createBlankDraft, parallelRole, parseDraftXml, removeExclusiveGateway, removeParallelBlock, serializeDraftXml,
} from "./processDraft";

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
    expect(() => addDraftNode(draft, "inclusiveGateway")).toThrow("仅支持");
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

  it("inserts a parallel fork/join block and round-trips it (Z9)", () => {
    const draft = createBlankDraft("parallel");
    const block = addDraftNode(draft, "parallelGateway", { after: "approveTask" });
    expect(block.nodes.map((node) => `${node.id}:${node.type}`)).toEqual([
      "start:startEvent",
      "approveTask:userTask",
      "fork:parallelGateway",
      "parallel_task:userTask",
      "parallel_task_2:userTask",
      "join:parallelGateway",
      "end:endEvent",
    ]);
    const edges = block.flows.map((flow) => `${flow.source_ref}>${flow.target_ref}`).sort();
    expect(edges).toEqual([
      "approveTask>fork", "fork>parallel_task", "fork>parallel_task_2", "join>end",
      "parallel_task>join", "parallel_task_2>join", "start>approveTask",
    ].sort());
    expect(parallelRole(block, "fork")).toBe("fork");
    expect(parallelRole(block, "join")).toBe("join");
    expect(parallelRole(block, "approveTask")).toBe("");

    const reparsed = parseDraftXml(serializeDraftXml(block));
    expect(reparsed.nodes.filter((node) => node.type === "parallelGateway").map((node) => node.name)).toEqual(["并行开始", "并行汇合"]);
    expect(serializeDraftXml(block)).toContain('<parallelGateway id="fork" name="并行开始"/>');

    const second = addDraftNode(block, "parallelGateway", { after: "start" });
    expect(second.nodes.filter((node) => node.type === "parallelGateway").map((node) => node.id)).toEqual(["fork_2", "join_2", "fork", "join"]);
    expect(() => addDraftNode(block, "parallelGateway", { after: "fork" })).toThrow("请先选择");
  });

  it("removes a parallel block from either its fork or its join (Z9)", () => {
    const block = addDraftNode(createBlankDraft("parallel"), "parallelGateway", { after: "approveTask" });
    for (const id of ["fork", "join"]) {
      const restored = removeParallelBlock(block, id);
      expect(restored.nodes.map((node) => node.id)).toEqual(["start", "approveTask", "end"]);
      expect(restored.flows.map((flow) => `${flow.source_ref}>${flow.target_ref}`).sort()).toEqual(["approveTask>end", "start>approveTask"]);
    }
    expect(() => removeParallelBlock(block, "approveTask")).toThrow("只能删除并行分支节点");
  });

  it("serializes a parallel block in the exact shape the backend contract test validates (ZA14)", () => {
    // Mirrors ProcessDraftParallelContractTest.webZ9ParallelBlockIsValid (nexus-p3-studio).
    const block = addDraftNode(createBlankDraft("za14-block"), "parallelGateway", { after: "approveTask" });
    block.nodes.forEach((node) => { if (node.type === "userTask") node.assignee = "${assignee}"; });
    const xml = serializeDraftXml(block);
    expect(xml).toContain('xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"');
    expect(xml).toContain('xmlns:flowable="http://flowable.org/bpmn"');
    for (const element of [
      '<parallelGateway id="fork" name="并行开始"/>',
      '<userTask id="parallel_task" name="并行审批 1" flowable:assignee="${assignee}"/>',
      '<userTask id="parallel_task_2" name="并行审批 2" flowable:assignee="${assignee}"/>',
      '<parallelGateway id="join" name="并行汇合"/>',
      '<sequenceFlow id="flow_approveTask_fork" sourceRef="approveTask" targetRef="fork">',
      '<sequenceFlow id="flow_fork_parallel_task" sourceRef="fork" targetRef="parallel_task">',
      '<sequenceFlow id="flow_fork_parallel_task_2" sourceRef="fork" targetRef="parallel_task_2">',
      '<sequenceFlow id="flow_parallel_task_join" sourceRef="parallel_task" targetRef="join">',
      '<sequenceFlow id="flow_parallel_task_2_join" sourceRef="parallel_task_2" targetRef="join">',
      '<sequenceFlow id="flow_join_end" sourceRef="join" targetRef="end">',
    ]) {
      expect(xml).toContain(element);
    }
    expect(xml).not.toContain("conditionExpression");
  });
});
