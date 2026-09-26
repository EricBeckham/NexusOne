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