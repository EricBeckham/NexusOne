import { describe, expect, it } from "vitest";
import { boxEdges, DEFAULT_SNAP, snapLayout, snapToEdges, snapToGrid } from "./fieldSnap";

describe("fieldSnap (ZA2)", () => {
  it("snaps points onto the grid and treats tiny steps as 1", () => {
    expect(snapToGrid({ x: 3, y: 7 }, { gridX: 2, gridY: 5 })).toEqual({ x: 4, y: 5 });
    expect(snapToGrid({ x: 1.8, y: -2 }, { gridX: 0, gridY: 1 })).toEqual({ x: 2, y: 0 });
    expect(snapToGrid(null)).toEqual({ x: 0, y: 0 });
  });

  it("exposes box edges from layout cells", () => {
    expect(boxEdges({ x: 2, y: 1, w: 4, h: 2 })).toEqual({ left: 2, right: 6, top: 1, bottom: 3 });
  });

  it("snaps to a sibling left edge within threshold", () => {
    const siblings = [{ layout: { x: 4, y: 10, w: 4, h: 1 } }];
    expect(snapToEdges({ x: 3, y: 2 }, { w: 4, h: 1 }, siblings, 1)).toEqual({
      x: 4,
      y: 2,
      hit: ["left"],
    });
  });

  it("does not snap when outside the threshold", () => {
    const siblings = [{ layout: { x: 10, y: 10, w: 2, h: 1 } }];
    expect(snapToEdges({ x: 0, y: 0 }, { w: 4, h: 1 }, siblings, 1)).toEqual({
      x: 0,
      y: 0,
      hit: [],
    });
  });

  it("snaps right edge of the mover to a sibling right edge", () => {
    const siblings = [{ layout: { x: 0, y: 10, w: 6, h: 1 } }];
    // mover w=4 at x=3 → right=7, within 1 of sibling right=6 → origin 2
    expect(snapToEdges({ x: 3, y: 3 }, { w: 4, h: 1 }, siblings, 1)).toEqual({
      x: 2,
      y: 3,
      hit: ["right"],
    });
  });

  it("snaps top/bottom independently of x", () => {
    const siblings = [{ layout: { x: 0, y: 8, w: 2, h: 1 } }];
    // sibling top=8 bottom=9; mover far in x
    expect(snapToEdges({ x: 8, y: 8 }, { w: 3, h: 1 }, siblings, 1)).toEqual({
      x: 8,
      y: 8,
      hit: ["top"],
    });
    // mover h=2 so top stays away from sibling edges while bottom meets sibling top
    expect(snapToEdges({ x: 8, y: 5 }, { w: 3, h: 2 }, siblings, 1)).toEqual({
      x: 8,
      y: 6,
      hit: ["bottom"],
    });
  });

  it("applies edge then grid via snapLayout", () => {
    const siblings = [{ layout: { x: 4, y: 10, w: 4, h: 1 } }];
    expect(snapLayout({ x: 3, y: 2 }, { w: 4, h: 1 }, siblings, {
      threshold: 1,
      gridX: 2,
      gridY: 2,
    })).toEqual({ x: 4, y: 2, hit: ["left"] });
    expect(DEFAULT_SNAP.threshold).toBe(1);
  });
});
