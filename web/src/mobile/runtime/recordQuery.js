/**
 * F010 query: field-equals filters for GET /api/v1/p3/ledgers/{id}/records.
 * Field codes follow P3 LedgerIdentifiers. Never send tenant identity.
 */
export const RESERVED_QUERY_KEYS = Object.freeze([
  "id",
  "tenant_id",
  "owner_id",
  "department_id",
  "created_at",
  "created_by",
  "updated_at",
  "updated_by",
  "deleted_at",
  "version",
  "schema_version",
  "ext_data",
  "page",
  "size",
]);

const FIELD_CODE = /^[a-z][a-z0-9_]{0,23}$/;

export function isLedgerFieldCode(key) {
  return typeof key === "string" && FIELD_CODE.test(key) && !RESERVED_QUERY_KEYS.includes(key);
}

export function buildRecordQuery(filters = {}, paging = {}) {
  const out = {};
  for (const [key, value] of Object.entries(filters || {})) {
    if (!isLedgerFieldCode(key)) {
      continue;
    }
    if (value == null || String(value).trim() === "") {
      continue;
    }
    out[key] = String(value).trim();
  }
  if (paging.page != null && String(paging.page).trim() !== "") {
    out.page = String(paging.page).trim();
  }
  if (paging.size != null && String(paging.size).trim() !== "") {
    out.size = String(paging.size).trim();
  }
  return out;
}

export function recordQueryString(filters, paging) {
  const built = buildRecordQuery(filters, paging);
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(built)) {
    params.set(key, value);
  }
  return params.toString();
}
