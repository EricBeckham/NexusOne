/**
 * ZA7 thin branch-condition DSL: one comparison `${variable op value}` ↔ the flow's condition string.
 * Not a rule engine — no AND/OR, functions or scripts. Anything that does not parse is an "advanced"
 * expression and is kept verbatim (never rewritten or dropped).
 *
 * Catalog variables are the ones the runtime really sets (SequentialApprovalService):
 * `outcome` (APPROVE/REJECT after a task completes) and `assigneeType` (PERSON/POSITION/ROLE at start).
 * Application form values are not process variables today (see docs/planning/ZA6-条件DSL差距盘点.md).
 */
export const CONDITION_VARIABLES = [
  {
    name: "outcome",
    label: "审批结果",
    type: "enum",
    options: [
      { value: "APPROVE", label: "同意" },
      { value: "REJECT", label: "驳回" },
    ],
  },
  {
    name: "assigneeType",
    label: "审批人类型",
    type: "enum",
    options: [
      { value: "PERSON", label: "指定人员" },
      { value: "POSITION", label: "岗位" },
      { value: "ROLE", label: "角色" },
    ],
  },
];

export const CONDITION_OPERATORS = [
  { value: "==", label: "等于", numeric: false },
  { value: "!=", label: "不等于", numeric: false },
  { value: ">", label: "大于", numeric: true },
  { value: ">=", label: "大于等于", numeric: true },
  { value: "<", label: "小于", numeric: true },
  { value: "<=", label: "小于等于", numeric: true },
];

const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_]*$/;
const NUMBER = /^-?\d+(\.\d+)?$/;
const SIMPLE = /^\$\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*(==|!=|>=|<=|>|<)\s*('(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?|true|false)\s*\}$/;

export function catalogVariable(name) {
  return CONDITION_VARIABLES.find((item) => item.name === name) || null;
}

export function operatorsFor(variable) {
  const entry = catalogVariable(variable);
  return entry?.type === "enum" ? CONDITION_OPERATORS.filter((op) => !op.numeric) : CONDITION_OPERATORS;
}

/**
 * @returns {{kind: "empty"} | {kind: "simple", variable: string, operator: string, value: string, valueType: "string"|"number"|"boolean"} | {kind: "advanced", text: string}}
 */
export function parseCondition(text) {
  const source = String(text ?? "").trim();
  if (!source) return { kind: "empty" };
  const match = SIMPLE.exec(source);
  if (!match) return { kind: "advanced", text: source };
  const [, variable, operator, literal] = match;
  if (literal === "true" || literal === "false") return { kind: "simple", variable, operator, value: literal, valueType: "boolean" };
  if (NUMBER.test(literal)) return { kind: "simple", variable, operator, value: literal, valueType: "number" };
  const quote = literal[0];
  const inner = literal.slice(1, -1).replace(new RegExp(`\\\\([\\\\${quote}])`, "g"), "$1");
  return { kind: "simple", variable, operator, value: inner, valueType: "string" };
}

function literalOf(value, valueType) {
  if (valueType === "number" || valueType === "boolean") return String(value).trim();
  return `'${String(value).replace(/\\/g, "\\\\").replace(/'/g, "\\'")}'`;
}

/** Canonical string for a complete simple condition; "" for an empty one. Throws on an incomplete one. */
export function formatCondition({ variable, operator, value, valueType = "string" } = {}) {
  const name = String(variable ?? "").trim();
  const hasValue = value !== undefined && value !== null && String(value).trim() !== "";
  if (!name && !operator && !hasValue) return "";
  if (!IDENTIFIER.test(name) || !CONDITION_OPERATORS.some((op) => op.value === operator) || !hasValue) {
    throw new Error("条件还不完整");
  }
  const literal = String(value).trim();
  if ((valueType === "number" && !NUMBER.test(literal)) || (valueType === "boolean" && !["true", "false"].includes(literal))) {
    throw new Error("条件的值和类型不匹配");
  }
  return `\${${name} ${operator} ${literalOf(value, valueType)}}`;
}

/** Human-readable summary, e.g. 审批结果 等于 同意; advanced expressions come back verbatim. */
export function describeCondition(text) {
  const parsed = parseCondition(text);
  if (parsed.kind === "empty") return "无条件";
  if (parsed.kind === "advanced") return parsed.text;
  const entry = catalogVariable(parsed.variable);
  const operator = CONDITION_OPERATORS.find((op) => op.value === parsed.operator)?.label || parsed.operator;
  const value = entry?.options?.find((option) => option.value === parsed.value)?.label
    ?? (parsed.valueType === "string" ? `「${parsed.value}」` : parsed.value);
  return `${entry?.label || parsed.variable} ${operator} ${value}`;
}

/**
 * ZA8: per-field problems of a builder draft. An all-empty draft is "no condition" and has none.
 * @returns {{variable?: string, operator?: string, value?: string}}
 */
export function checkConditionDraft({ variable, operator, value } = {}) {
  const name = String(variable ?? "").trim();
  const text = String(value ?? "").trim();
  const problems = {};
  if (!name && !text) return problems;
  if (!name) problems.variable = "请选择变量";
  else if (!IDENTIFIER.test(name)) problems.variable = "变量名只能包含字母、数字和下划线，且不能以数字开头";
  const op = CONDITION_OPERATORS.find((item) => item.value === operator);
  if (!op) problems.operator = "请选择运算符";
  if (!text) problems.value = catalogVariable(name)?.options ? "请选择一个值" : "请填写要比较的值";
  else if (op?.numeric && !NUMBER.test(text)) problems.value = `「${op.label}」需要填写数字`;
  else {
    const options = catalogVariable(name)?.options;
    if (options && !options.some((option) => option.value === text)) problems.value = "请从列表中选择一个值";
  }
  return problems;
}

/** ZA8: a light format check for any stored condition (builder output always passes). "" when fine. */
export function checkConditionText(text) {
  const source = String(text ?? "").trim();
  if (!source) return "";
  if (parseCondition(source).kind === "simple") return "";
  if (!/^\$\{[\s\S]*\}$/.test(source)) return "条件需要写成 ${…} 的形式";
  if (!source.slice(2, -1).trim()) return "条件内容不能为空；无条件请直接清空";
  const unescaped = source.replace(/\\./g, "");
  if ((unescaped.match(/'/g) || []).length % 2 || (unescaped.match(/"/g) || []).length % 2) return "引号没有成对";
  let depth = 0;
  for (const char of unescaped) {
    if (char === "(") depth += 1;
    if (char === ")") depth -= 1;
    if (depth < 0) break;
  }
  if (depth !== 0) return "括号没有成对";
  return "";
}
