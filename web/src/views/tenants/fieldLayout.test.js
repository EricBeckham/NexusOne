import { describe, expect, it } from "vitest";
import {
  DEFAULT_LAYOUT,
  hasLayout,
  normalizeLayout,
  omitLayout,
  withNormalizedLayout,
} from "./fieldLayout";

describe("fieldLayout (Z2)", () => {
  it("fills defaults when layout is missing or not an object", () => {
    expect(normalizeLayout(undefined)).toEqual(DEFAULT_LAYOUT);
    expect(normalizeLayout(null)).toEqual(DEFAULT_LAYOUT);
    expect(normalizeLayout("bad")).toEqual(DEFAULT_LAYOUT);
    expect(normalizeLayout([])).toEqual(DEFAULT_LAYOUT);
  });

  it("coerces and clamps illegal numeric values", () => {
    expect(normalizeLayout({ x: -3, y: 1.9, w: 0, h: 99 })).toEqual({ x: 0, y: 1, w: 1, h: 96 });
    expect(normalizeLayout({ x: "2", y: "NaN", w: Infinity, h: -1 })).toEqual({
      x: 2,
      y: DEFAULT_LAYOUT.y,
      w: DEFAULT_LAYOUT.w,
      h: 1,
    });
  });

  it("detects layout presence and attaches normalized layout by index fallback", () => {
    expect(hasLayout({ key: "a" })).toBe(false);
    expect(hasLayout({ layout: { x: 1 } })).toBe(true);
    expect(withNormalizedLayout({ key: "a" }, 3).layout).toEqual({ x: 0, y: 3, w: 4, h: 1 });
    expect(omitLayout({ key: "a", layout: { x: 1, y: 0, w: 2, h: 1 } })).toEqual({ key: "a" });
  });
});
