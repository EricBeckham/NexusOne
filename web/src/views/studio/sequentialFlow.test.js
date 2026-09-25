/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { fromSequence, newStep, toSequence } from "./sequentialFlow";
import { createBlankDraft, parseDraftXml, serializeDraftXml } from "./processDraft";

const chain = {
  key: "k",
  name: "n",
  nodes: [
    { id: "end", name: "结束", type: "endEvent" },
    { id: "b", name: "B", type: "userTask" },
    { id: "start", name: "开始", type: "startEvent" },
    { id: "a", name: "A", type: "userTask" },
  ],
  flows: [
    { id: "1", source_ref: "start", target_ref: "a", condition: null },
    { id: "2", source_ref: "a", target_ref: "b", condition: null },
    { id: "3", source_ref: "b", target_ref: "end", condition: null },
  ],
};

describe("sequential flow model", () => {
  it("orders steps by walking the chain from the start, not by node order", () => {
    const sequence = toSequence(chain);
    expect(sequence.sequential).toBe(true);
    expect(sequence.steps.map((step) => step.id)).toEqual(["a", "b"]);
    expect(sequence.start.id).toBe("start");
    expect(sequence.end.id).toBe("end");
  });

  it("refuses gateways, conditions, and branches", () => {
    expect(toSequence({ nodes: [{ id: "g", type: "exclusiveGateway" }], flows: [] }).sequential).toBe(false);
    expect(toSequence({ ...chain, flows: [{ ...chain.flows[0], condition: "${x}" }] }).reason).toContain("条件");
    const branched = { ...chain, flows: [...chain.flows, { id: "4", source_ref: "start", target_ref: "b", condition: null }] };
    expect(toSequence(branched).reason).toContain("分支");
  });

  it("rebuilds a straight chain and adds a missing end node", () => {
    const partial = { key: "k", nodes: [{ id: "start", type: "startEvent" }, { id: "a", type: "userTask" }], flows: [] };
    const rebuilt = fromSequence(partial, toSequence(partial).steps);
    expect(rebuilt.nodes.map((node) => node.id)).toEqual(["start", "a", "end"]);
    expect(rebuilt.flows.map((flow) => `${flow.source_ref}>${flow.target_ref}`)).toEqual(["start>a", "a>end"]);
  });

  it("round-trips a reordered chain through BPMN XML", () => {
    const reordered = fromSequence(chain, [...toSequence(chain).steps].reverse());
    const parsed = parseDraftXml(serializeDraftXml(reordered));
    expect(toSequence(parsed).steps.map((step) => step.id)).toEqual(["b", "a"]);
  });

  it("creates new steps with unique ids and numbered names", () => {
    const draft = createBlankDraft("k");
    const step = newStep(draft);
    expect(step).toMatchObject({ id: "approve_2", name: "审批节点 2", type: "userTask", assignee: "" });
    const next = fromSequence(draft, [...toSequence(draft).steps, step]);
    expect(newStep(next).id).toBe("approve_3");
  });
});
