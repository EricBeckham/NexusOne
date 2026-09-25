/** Field types the platform form template API accepts, grouped for the designer palette. */
export const FIELD_GROUPS = [
  { id: "basic", label: "基础" },
  { id: "data", label: "数据" },
  { id: "business", label: "业务" },
];

export const FIELD_TYPES = [
  { value: "TEXT", label: "文本", icon: "type", group: "basic", keyPrefix: "text", hint: "单行文字" },
  { value: "TEXTAREA", label: "多行文本", icon: "alignLeft", group: "basic", keyPrefix: "textarea", hint: "长段说明" },
  { value: "NUMBER", label: "数字", icon: "hash", group: "basic", keyPrefix: "number", hint: "整数或小数" },
  { value: "DATE", label: "日期", icon: "calendar", group: "basic", keyPrefix: "date", hint: "年月日" },
  { value: "SELECT", label: "单选下拉", icon: "list", group: "data", keyPrefix: "choice", hint: "从选项中选一个" },
  { value: "MONEY", label: "金额", icon: "currency", group: "data", keyPrefix: "amount", hint: "两位小数" },
  { value: "PERSON", label: "成员选择", icon: "user", group: "business", keyPrefix: "person", hint: "本租户成员" },
  { value: "ATTACHMENT", label: "附件", icon: "paperclip", group: "business", keyPrefix: "attachment", hint: "上传文件" },
];

const BY_VALUE = Object.fromEntries(FIELD_TYPES.map((type) => [type.value, type]));

export function fieldType(value) {
  return BY_VALUE[value] || BY_VALUE.TEXT;
}

let uidSeed = 0;
export function nextUid() {
  uidSeed += 1;
  return `f${uidSeed}`;
}

/** First unused `<prefix>_<n>` key; satisfies the server rule ^[a-z][a-z0-9_]*$. */
export function nextKey(type, fields) {
  const prefix = fieldType(type).keyPrefix;
  const used = new Set(fields.map((field) => field.key));
  let n = 1;
  while (used.has(`${prefix}_${n}`)) n += 1;
  return `${prefix}_${n}`;
}

/** A field created from the palette: valid key, readable label, and a starter option for SELECT. */
export function paletteField(type, fields) {
  return {
    uid: nextUid(),
    key: nextKey(type, fields),
    label: fieldType(type).label,
    type,
    required: false,
    options: type === "SELECT" ? [{ label: "选项 1", value: "option_1" }] : [],
  };
}

export function blankField() {
  return { uid: nextUid(), key: "", label: "", type: "TEXT", required: false, options: [] };
}

export { insertAt, moveToGap } from "../../../utils/listMove";

export function normalizeOptions(options) {
  if (!Array.isArray(options)) return [];
  return options.map((item) => {
    if (item && typeof item === "object") {
      return { label: String(item.label ?? ""), value: String(item.value ?? "") };
    }
    const text = item == null ? "" : String(item);
    return { label: text, value: text };
  });
}

export function filledOptions(field) {
  return (field.options || []).filter((option) => option.label || option.value).map((option) => ({
    label: option.label || option.value,
    value: option.value || option.label,
  }));
}
