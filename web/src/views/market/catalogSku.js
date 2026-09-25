/**
 * GET /api/v1/p4/catalog returns each SKU as `CatalogSku { sku, product_definition }` (SNAKE_CASE,
 * `product_definition` omitted when the SKU is not bound). Subscribe needs that binding
 * (`subscribeProduct` 404s without it), so an unbound SKU is shown as not subscribable.
 */
export function catalogSku(entry) {
  const sku = entry?.sku || {};
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
