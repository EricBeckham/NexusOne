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

  it("fans a gateway's branches out below the main line and puts the merge after them (Z8)", () => {
    // start → approve → gateway ─┬→ end            (branch 1)
    //                            └→ branch → end    (branch 2)
    const graph = layoutDefinition({
      nodes: [
        { id: "start", type: "startEvent" },
        { id: "approve", type: "userTask" },
        { id: "gateway", type: "exclusiveGateway" },
        { id: "branch", type: "userTask" },
        { id: "end", type: "endEvent" },
      ],
      flows: [
        { id: "a", source_ref: "start", target_ref: "approve" },
        { id: "b", source_ref: "approve", target_ref: "gateway" },
        { id: "c", source_ref: "gateway", target_ref: "end" },
        { id: "d", source_ref: "gateway", target_ref: "branch" },
        { id: "e", source_ref: "branch", target_ref: "end" },
      ],
    });
    const at = Object.fromEntries(graph.nodes.map((node) => [node.id, node]));
    // Columns: every node is right of its predecessors; the merge (end) comes after the branch.
    expect(at.gateway.x).toBeLessThan(at.branch.x);
    expect(at.branch.x).toBeLessThan(at.end.x);
    // Rows: the main line stays on one row; the second branch fans out below.
    expect([at.start.y, at.approve.y, at.gateway.y, at.end.y]).toEqual([at.start.y, at.start.y, at.start.y, at.start.y]);
    expect(at.branch.y).toBeGreaterThan(at.gateway.y);
    expect(graph.height).toBeGreaterThan(graph.nodeHeight + 48);
    // Edges: straight on one row, elbow when changing rows.
    const flow = Object.fromEntries(graph.flows.map((item) => [item.id, item]));
    expect(flow.c.path).toMatch(/^M\d+ \d+ H\d+$/);
    expect(flow.d.path).toMatch(/^M\d+ \d+ H\d+ V\d+ H\d+$/);
    expect(flow.d.x2).toBe(at.branch.x);
  });

  it("spreads three branches on separate rows and still places nodes in a cycle", () => {
    const fan = layoutDefinition({
      nodes: ["g", "a", "b", "c", "m"].map((id) => ({ id, type: id === "g" ? "exclusiveGateway" : "userTask" })),
      flows: [
        { id: "1", source_ref: "g", target_ref: "a" },
        { id: "2", source_ref: "g", target_ref: "b" },
        { id: "3", source_ref: "g", target_ref: "c" },
        { id: "4", source_ref: "a", target_ref: "m" },
        { id: "5", source_ref: "b", target_ref: "m" },
        { id: "6", source_ref: "c", target_ref: "m" },
      ],
    });
    const rows = Object.fromEntries(fan.nodes.map((node) => [node.id, node.y]));
    expect(new Set([rows.a, rows.b, rows.c]).size).toBe(3);
    expect(rows.m).toBe(rows.a);

    const loop = layoutDefinition({
      nodes: [{ id: "x" }, { id: "y" }],
      flows: [{ id: "p", source_ref: "x", target_ref: "y" }, { id: "q", source_ref: "y", target_ref: "x" }],
    });
    expect(loop.nodes.every((node) => Number.isFinite(node.x) && Number.isFinite(node.y))).toBe(true);
    expect(loop.nodes[0].x).not.toBe(loop.nodes[1].x);
  });
});
