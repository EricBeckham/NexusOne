import { describe, expect, it } from "vitest";
import { filterTree, nodeCount, relationNode, treeRoots, visibleRows } from "./relationDisplay";

const tree = {
  nodes: [
    {
      id: "root",
      code: "group",
      name: "集团",
      status: "ACTIVE",
      children: [
        {
          id: "child",
          code: "branch",
          name: "分部",
          status: "DISABLED",
          parent_tenant_id: "root",
          children: [],
        },
      ],
    },
  ],
};

describe("tenant relation display", () => {
  it("reads snake_case parent ids and drops rows without an id", () => {
    const roots = treeRoots(tree);
    expect(roots[0].children[0].parentTenantId).toBe("root");
    expect(relationNode({ code: "orphan" })).toBeNull();
    expect(treeRoots({})).toBeNull();
  });

  it("keeps a matching descendant under its parent and hides unrelated branches", () => {
    const roots = treeRoots(tree);
    expect(filterTree(roots, "分部").map((node) => node.code)).toEqual(["group"]);
    expect(filterTree(roots, "分部")[0].children.map((node) => node.code)).toEqual(["branch"]);
    expect(filterTree(roots, "没有")).toEqual([]);
    expect(filterTree(roots, "集团")[0].children).toHaveLength(1);
  });

  it("collapses a large tree until a node is expanded", () => {
    const child = { id: "c", code: "c", name: "c", status: "", parentTenantId: "p", children: [] };
    const parent = { id: "p", code: "p", name: "p", status: "", parentTenantId: "", children: [child] };
    expect(nodeCount([parent])).toBe(2);
    expect(visibleRows([parent], { lazy: true }).map((row) => row.id)).toEqual(["p"]);
    expect(visibleRows([parent], { lazy: true, expanded: new Set(["p"]) }).map((row) => row.id)).toEqual(["p", "c"]);
  });
});
