import { describe, expect, it } from "vitest";
import {
  DEFAULT_LAYOUT,
  hasLayout,
  normalizeLayout,
  omitLayout,
  readWireOptions,
  wireOptions,
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

describe("fieldLayout wire options (Z4)", () => {
  it("reads layout from options.layout and SELECT choices envelope", () => {
    expect(readWireOptions(null)).toEqual({ layout: null, choices: [] });
    expect(readWireOptions([{ label: "A", value: "a" }])).toEqual({
      layout: null,
      choices: [{ label: "A", value: "a" }],
    });
    expect(readWireOptions({ layout: { x: 1, y: 2, w: 6, h: 2 } })).toEqual({
      layout: { x: 1, y: 2, w: 6, h: 2 },
      choices: [],
    });
    expect(readWireOptions({
      layout: { x: 0, y: 1, w: 4, h: 1 },
      choices: [{ label: "年假", value: "annual" }],
    })).toEqual({
      layout: { x: 0, y: 1, w: 4, h: 1 },
      choices: [{ label: "年假", value: "annual" }],
    });
  });

  it("writes options.layout for free layout without a new column", () => {
    expect(wireOptions("TEXT", [], null)).toBeNull();
    expect(wireOptions("TEXT", [], { x: 2, y: 0, w: 4, h: 1 })).toEqual({
      layout: { x: 2, y: 0, w: 4, h: 1 },
    });
    expect(wireOptions("SELECT", [{ label: "A", value: "a" }], null)).toEqual([
      { label: "A", value: "a" },
    ]);
    expect(wireOptions("SELECT", [{ label: "A", value: "a" }], { x: 1, y: 0, w: 6, h: 1 })).toEqual({
      layout: { x: 1, y: 0, w: 6, h: 1 },
      choices: [{ label: "A", value: "a" }],
    });
  });
});
