import { describe, expect, it } from "vitest";
import { describeCondition, formatCondition, operatorsFor, parseCondition } from "./conditionDsl";

describe("thin branch-condition DSL (ZA7)", () => {
  it("round-trips catalog, free, numeric, text and boolean comparisons", () => {
    const cases = [
      { variable: "outcome", operator: "==", value: "APPROVE", valueType: "string", text: "${outcome == 'APPROVE'}" },
      { variable: "assigneeType", operator: "!=", value: "ROLE", valueType: "string", text: "${assigneeType != 'ROLE'}" },
      { variable: "amount", operator: ">", value: "1000", valueType: "number", text: "${amount > 1000}" },
      { variable: "ratio", operator: "<=", value: "-0.5", valueType: "number", text: "${ratio <= -0.5}" },
      { variable: "urgent", operator: "==", value: "true", valueType: "boolean", text: "${urgent == true}" },
    ];
    for (const { text, ...structure } of cases) {
      expect(formatCondition(structure)).toBe(text);
      expect(parseCondition(text)).toEqual({ kind: "simple", ...structure });
    }
  });

  it("tolerates spacing and double quotes and escapes quotes in text values", () => {
    expect(parseCondition('  ${ outcome==\"REJECT\" }  ')).toEqual({ kind: "simple", variable: "outcome", operator: "==", value: "REJECT", valueType: "string" });
    const text = formatCondition({ variable: "note", operator: "==", value: "it's", valueType: "string" });
    expect(text).toBe("${note == 'it\\'s'}");
    expect(parseCondition(text)).toMatchObject({ value: "it's" });
  });

  it("keeps empty as no condition and anything else verbatim as advanced", () => {
    expect(parseCondition("")).toEqual({ kind: "empty" });
    expect(parseCondition(null)).toEqual({ kind: "empty" });
    expect(formatCondition({ variable: "", operator: "", value: "" })).toBe("");
    for (const text of ["${a > 1 && b < 2}", "${fn(x)}", "amount > 1", "${outcome == APPROVE}", "${x.y == 1}"]) {
      expect(parseCondition(text)).toEqual({ kind: "advanced", text });
      expect(describeCondition(text)).toBe(text);
    }
  });

  it("refuses to format an incomplete comparison", () => {
    expect(() => formatCondition({ variable: "outcome", operator: "==", value: "" })).toThrow("条件还不完整");
    expect(() => formatCondition({ variable: "1bad", operator: "==", value: "x" })).toThrow("条件还不完整");
    expect(() => formatCondition({ variable: "outcome", operator: "~=", value: "x" })).toThrow("条件还不完整");
  });

  it("describes conditions in words and narrows operators for catalog enums", () => {
    expect(describeCondition("${outcome == 'APPROVE'}")).toBe("审批结果 等于 同意");
    expect(describeCondition("${amount > 1000}")).toBe("amount 大于 1000");
    expect(describeCondition("${note != 'x'}")).toBe("note 不等于 「x」");
    expect(describeCondition("")).toBe("无条件");
    expect(operatorsFor("outcome").map((op) => op.value)).toEqual(["==", "!="]);
    expect(operatorsFor("amount")).toHaveLength(6);
  });
});