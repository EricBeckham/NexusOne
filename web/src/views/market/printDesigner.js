/**
 * CF-RD1: print designer model built from the PC11 built-in print sample. A slot is one printed position in a
 * section of the page; it shows either a system attribute of the application or a product form field.
 * Pure functions only — no layout engine, no coordinates, no pagination.
 */

/** Header slots print attributes every product application has; they are not product form fields. */
export const SYSTEM_SECTIONS = new Set(["header", "approval"]);

export function slotId(sectionKey, fieldKey) {
  return `${sectionKey}.${fieldKey}`;
}

export function designerFromSample(sample) {
  const sections = (Array.isArray(sample?.sections) ? sample.sections : [])
    .filter((section) => section?.key)
    .map((section) => ({
      key: section.key,
      label: section.label || section.key,
      kind: SYSTEM_SECTIONS.has(section.key) ? "system" : "form",
      slots: (Array.isArray(section.fields) ? section.fields : [])
        .filter((field) => field?.key)
        .map((field) => ({
          id: slotId(section.key, field.key),
          section: section.key,
          label: field.label || field.key,
          type: field.type || "TEXT",
          source: SYSTEM_SECTIONS.has(section.key) ? "system" : "form",
          fieldKey: field.key,
        })),
    }));
  const formFields = sections
    .filter((section) => section.kind === "form")
    .flatMap((section) => section.slots.map((slot) => ({ key: slot.fieldKey, label: slot.label, type: slot.type })));
  return {
    productDefinitionId: sample?.product_definition_id || "",
    productName: sample?.product_name || "",
    templateCode: sample?.template_code || "",
    templateName: sample?.template_name || "内置样例版式",
    paper: sample?.paper || "A4",
    orientation: sample?.orientation === "LANDSCAPE" ? "LANDSCAPE" : "PORTRAIT",
    title: sample?.title || sample?.product_name || "",
    sections,
    formFields,
  };
}

export function allSlots(designer) {
  return (designer?.sections || []).flatMap((section) => section.slots);
}

export const TYPE_LABELS = {
  TEXT: "文本",
  TEXTAREA: "多行文本",
  NUMBER: "数字",
  MONEY: "金额",
  DATE: "日期",
  SELECT: "选项",
  PERSON: "人员",
  ATTACHMENT: "附件",
};

export function typeLabel(type) {
  return TYPE_LABELS[String(type || "").toUpperCase()] || type || "文本";
}
/**
 * CF-RD2: field → slot binding over the CF-R3 contract. Every field of the print sample (system attributes and
 * product form fields) gets one slot; a form field with no explicit mapping sits in the contract's spill slot
 * (form.fields). Wire keys are snake_case (product mapper).
 */
export function contractSlots(contract) {
  const slots = contract?.builtin_template?.slots;
  return (Array.isArray(slots) ? slots : []).filter((slot) => slot?.id);
}

export function spillSlotId(contract) {
  return contract?.mapping_rules?.unmapped_spill_slot_id || "form.fields";
}

export function accepts(slot, type) {
  const list = Array.isArray(slot?.accepts) ? slot.accepts : [];
  return list.includes("*") || list.includes(String(type || "TEXT").toUpperCase());
}

export function slotOptions(contract, type) {
  return contractSlots(contract).filter((slot) => accepts(slot, type));
}

export function bindingRows(designer, contract, mapping) {
  const bound = new Map(
    (Array.isArray(mapping?.mappings) ? mapping.mappings : [])
      .filter((entry) => entry?.field_key)
      .map((entry) => [entry.field_key, entry.slot_id || ""]),
  );
  const seen = new Set();
  const rows = [];
  for (const section of designer?.sections || []) {
    for (const slot of section.slots) {
      if (seen.has(slot.fieldKey)) continue;
      seen.add(slot.fieldKey);
      const fallback = slot.source === "form" ? spillSlotId(contract) : "";
      rows.push({
        fieldKey: slot.fieldKey,
        label: slot.label,
        type: slot.type,
        source: slot.source,
        slotId: bound.has(slot.fieldKey) ? bound.get(slot.fieldKey) : fallback,
      });
    }
  }
  return rows;
}

/** Errors by field key: unknown slot, a type the slot does not accept, or two fields in a single-use slot. */
export function validateBindings(rows, contract) {
  const slots = new Map(contractSlots(contract).map((slot) => [slot.id, slot]));
  const errors = {};
  const usage = new Map();
  for (const row of rows) {
    if (!row.slotId) continue;
    const slot = slots.get(row.slotId);
    if (!slot) {
      errors[row.fieldKey] = "契约里没有这个槽位。";
      continue;
    }
    if (!accepts(slot, row.type)) {
      errors[row.fieldKey] = `「${slot.label}」不接受${typeLabel(row.type)}字段。`;
      continue;
    }
    if (!slot.repeatable) usage.set(slot.id, [...(usage.get(slot.id) || []), row.fieldKey]);
  }
  for (const [slotIdValue, keys] of usage) {
    if (keys.length < 2) continue;
    const label = slots.get(slotIdValue)?.label || slotIdValue;
    for (const key of keys) errors[key] = `「${label}」只能放一个字段。`;
  }
  return errors;
}

export function mappingsFromRows(rows) {
  return rows.filter((row) => row.slotId).map((row) => ({ field_key: row.fieldKey, slot_id: row.slotId }));
}

/** True when the server holds exactly these field → slot pairs (order-insensitive). */
export function sameMappings(a, b) {
  const key = (list) => (Array.isArray(list) ? list : [])
    .map((entry) => `${entry?.field_key}=>${entry?.slot_id}`)
    .sort()
    .join("|");
  return key(a) === key(b);
}