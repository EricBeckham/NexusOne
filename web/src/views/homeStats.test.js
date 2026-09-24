import { describe, expect, it } from "vitest";
import { countTree, realCount, recordName } from "./homeStats";

describe("home stats", () => {
  it("counts only real lists", () => {
    expect(realCount([{ id: "a" }, { id: "b" }])).toBe(2);
    expect(realCount([])).toBe(0);
    expect(realCount({ items: [] })).toBeNull();
    expect(realCount(null)).toBeNull();
  });

  it("counts a department tree without inventing missing nodes", () => {
    expect(countTree([{ id: "a", children: [{ id: "b" }] }])).toBe(2);
    expect(countTree([])).toBe(0);
    expect(countTree({ tree: [] })).toBeNull();
  });

  it("reads a name only when the payload has one", () => {
    expect(recordName({ name: "平台" })).toBe("平台");
    expect(recordName({ display_name: "星枢" })).toBe("星枢");
    expect(recordName({ id: "x" })).toBeNull();
  });
});
