import { describe, expect, it } from "vitest";
import { layoutDefinition, showText } from "./processLayout";

describe("process definition layout", () => {
  it("places nodes by real edges and leaves missing conditions empty", () => {
    const graph = layoutDefinition({
      nodes: [
        { id: "start", name: "Start", type: "startEvent" },
        { id: "approve", name: "Approve", type: "userTask" },
        { id: "end", name: null, type: "endEvent" },
      ],
      flows: [
        { id: "toApprove", source_ref: "start", target_ref: "approve", condition: null },
        { id: "toEnd", source_ref: "approve", target_ref: "end", condition: "${outcome == 'APPROVE'}" },
      ],
    });
    const byId = Object.fromEntries(graph.nodes.map((node) => [node.id, node]));
    expect(byId.start.x).toBeLessThan(byId.approve.x);
    expect(byId.approve.x).toBeLessThan(byId.end.x);
    expect(graph.flows.find((flow) => flow.id === "toApprove").x2).toBe(byId.approve.x);
    expect(showText(null)).toBe("暂无");
    expect(showText(byId.end.name)).toBe("暂无");
  });
});
