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