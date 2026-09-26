/**
 * Z2: form-field free-layout metadata. Units are grid cells (integers ≥ 0).
 * Does not own drag/drop UI — consumers (Z3+) place fields with these numbers.
 */

export const DEFAULT_LAYOUT = Object.freeze({ x: 0, y: 0, w: 4, h: 1 });

const MAX = 96;

function toInt(value, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.trunc(n);
}

function clamp(n, min, max) {
  return Math.min(max, Math.max(min, n));
}

/**
 * Normalize any input into `{ x, y, w, h }`.
 * Missing / illegal values fall back to defaults; w/h at least 1.
 */
export function normalizeLayout(input, fallback = DEFAULT_LAYOUT) {
  const base = fallback && typeof fallback === "object" ? fallback : DEFAULT_LAYOUT;
  const src = input && typeof input === "object" && !Array.isArray(input) ? input : {};
  return {
    x: clamp(toInt(src.x, toInt(base.x, 0)), 0, MAX),
    y: clamp(toInt(src.y, toInt(base.y, 0)), 0, MAX),
    w: clamp(toInt(src.w, toInt(base.w, 4)), 1, MAX),
    h: clamp(toInt(src.h, toInt(base.h, 1)), 1, MAX),
  };
}

/** True when the field already carries a usable layout object (even if sparse). */
export function hasLayout(field) {
  const layout = field?.layout;
  return Boolean(layout && typeof layout === "object" && !Array.isArray(layout));
}

/** Return field with normalized `layout` (always present). */
export function withNormalizedLayout(field, index = 0) {
  const fallback = { ...DEFAULT_LAYOUT, y: clamp(index, 0, MAX) };
  return {
    ...field,
    layout: normalizeLayout(field?.layout, fallback),
  };
}

/** Strip layout for list-only payloads when free layout is unused. */
export function omitLayout(field) {
  if (!field || typeof field !== "object") return field;
  const { layout, ...rest } = field;
  return rest;
}
