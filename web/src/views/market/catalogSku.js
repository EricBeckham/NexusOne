/**
 * GET /api/v1/p4/catalog exposes code/name/entitlements directly on each SKU.
 * The nested fallback keeps the UI tolerant while older demo hosts roll forward.
 */
export function catalogSku(entry) {
  const sku = entry?.sku || entry || {};
  return {
    code: sku.code || "",
    name: sku.name || "",
    entitlements: entitlementChips(sku.entitlements),
    definition: entry?.product_definition || null,
  };
}

export function catalogSkus(item) {
  return (Array.isArray(item?.skus) ? item.skus : []).map(catalogSku);
}

function entitlementChips(entitlements) {
  if (!entitlements || typeof entitlements !== "object") return [];
  return Object.entries(entitlements)
    .filter(([, spec]) => spec?.enabled !== false)
    .map(([key, spec]) => (spec?.quota != null ? `${key} · 配额 ${spec.quota}` : key));
}
