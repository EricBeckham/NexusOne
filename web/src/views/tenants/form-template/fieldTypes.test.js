import { describe, expect, it } from "vitest";
import { FIELD_TYPES, insertAt, moveToGap, nextKey, paletteField } from "./fieldTypes";

describe("form template field helpers", () => {
  it("generates the first unused key that matches the server key rule", () => {
    const fields = [{ key: "text_1" }, { key: "text_3" }];
    expect(nextKey("TEXT", fields)).toBe("text_2");
    expect(nextKey("MONEY", fields)).toBe("amount_1");
    for (const type of FIELD_TYPES) expect(nextKey(type.value, [])).toMatch(/^[a-z][a-z0-9_]*$/);
  });

  it("creates palette fields with a label and a starter option only for SELECT", () => {
    expect(paletteField("SELECT", []).options).toEqual([{ label: "选项 1", value: "option_1" }]);
    const date = paletteField("DATE", []);
    expect(date).toMatchObject({ key: "date_1", label: "日期", type: "DATE", required: false, options: [] });
    expect(date.uid).not.toBe(paletteField("DATE", []).uid);
  });

  it("inserts at a clamped index", () => {
    expect(insertAt(["a", "b"], 1, "x")).toEqual(["a", "x", "b"]);
    expect(insertAt(["a"], 9, "x")).toEqual(["a", "x"]);
  });

  it("moves an item into a drop gap and ignores gaps adjacent to itself", () => {
    const list = ["a", "b", "c", "d"];
    expect(moveToGap(list, 0, 2)).toEqual(["b", "a", "c", "d"]);
    expect(moveToGap(list, 0, 4)).toEqual(["b", "c", "d", "a"]);
    expect(moveToGap(list, 3, 0)).toEqual(["d", "a", "b", "c"]);
    expect(moveToGap(list, 1, 1)).toBe(list);
    expect(moveToGap(list, 1, 2)).toBe(list);
    expect(moveToGap(list, -1, 2)).toBe(list);
  });
});
