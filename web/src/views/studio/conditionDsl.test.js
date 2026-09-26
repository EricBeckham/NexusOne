import { describe, expect, it } from "vitest";
import { checkConditionDraft, checkConditionText, describeCondition, formatCondition, operatorsFor, parseCondition } from "./conditionDsl";

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
    // Never emit a bare word as a number or boolean (ZA8).
    expect(() => formatCondition({ variable: "amount", operator: ">", value: "很多", valueType: "number" })).toThrow("不匹配");
    expect(() => formatCondition({ variable: "urgent", operator: "==", value: "yes", valueType: "boolean" })).toThrow("不匹配");
  });

  it("describes conditions in words and narrows operators for catalog enums", () => {
    expect(describeCondition("${outcome == 'APPROVE'}")).toBe("审批结果 等于 同意");
    expect(describeCondition("${amount > 1000}")).toBe("amount 大于 1000");
    expect(describeCondition("${note != 'x'}")).toBe("note 不等于 「x」");
    expect(describeCondition("")).toBe("无条件");
    expect(operatorsFor("outcome").map((op) => op.value)).toEqual(["==", "!="]);
    expect(operatorsFor("amount")).toHaveLength(6);
  });

  it("names what is missing or wrong in a builder draft (ZA8)", () => {
    expect(checkConditionDraft({ variable: "", operator: "==", value: "" })).toEqual({});
    expect(checkConditionDraft({ variable: "outcome", operator: "==", value: "" })).toEqual({ value: "请选择一个值" });
    expect(checkConditionDraft({ variable: "amount", operator: "==", value: "" })).toEqual({ value: "请填写要比较的值" });
    expect(checkConditionDraft({ variable: "", operator: "==", value: "1" })).toEqual({ variable: "请选择变量" });
    expect(checkConditionDraft({ variable: "9x", operator: "==", value: "1" }).variable).toContain("不能以数字开头");
    expect(checkConditionDraft({ variable: "amount", operator: ">", value: "多" })).toEqual({ value: "「大于」需要填写数字" });
    expect(checkConditionDraft({ variable: "outcome", operator: "==", value: "MAYBE" })).toEqual({ value: "请从列表中选择一个值" });
    expect(checkConditionDraft({ variable: "amount", operator: "~", value: "1" })).toEqual({ operator: "请选择运算符" });
    expect(checkConditionDraft({ variable: "amount", operator: ">=", value: "10.5" })).toEqual({});
  });

  it("lightly checks stored condition text without judging advanced logic (ZA8)", () => {
    expect(checkConditionText("")).toBe("");
    expect(checkConditionText("${outcome == 'APPROVE'}")).toBe("");
    expect(checkConditionText("${a > 1 && fn(b)}")).toBe("");
    expect(checkConditionText("amount > 1")).toBe("条件需要写成 ${…} 的形式");
    expect(checkConditionText("${ }")).toContain("条件内容不能为空");
    expect(checkConditionText("${note == 'x}")).toBe("引号没有成对");
    expect(checkConditionText("${fn(a}")).toBe("括号没有成对");
    expect(checkConditionText("${note == 'it\\'s'}")).toBe("");
  });
});
