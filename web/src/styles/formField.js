/**
 * O4 form-field style contract (§10.3). CSS lives in `./form-field.css` (loaded from main.js).
 * Reuse these class names in new forms; do not invent per-page red-star colors.
 */
export const FORM_FIELD_CLASSES = Object.freeze({
  /** Red required asterisk next to the label. */
  required: "nx-req",
  /** Single field-adjacent error line. */
  error: "nx-field-error",
  /** Form-level unmatched error list. */
  errorList: "nx-field-errors",
});

/** Markup snippet for docs / copy-paste (not rendered). */
export const FORM_FIELD_EXAMPLE = `
<label class="nx-field">
  <span>名称<em class="nx-req" aria-hidden="true">*</em></span>
  <input required aria-invalid="true" />
  <small class="nx-field-error">请填写名称</small>
</label>
`.trim();
