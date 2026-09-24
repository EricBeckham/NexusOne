function text(value) {
  return typeof value === "string" && value.trim() ? value : null;
}

function numberOrNull(value) {
  return Number.isFinite(value) ? value : null;
}

export function entitlementSummary(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }
  return {
    source: text(value.source),
    packCode: text(value.pack_code ?? value.packCode),
    packVersion: text(value.pack_version ?? value.packVersion),
    skuCode: text(value.sku_code ?? value.skuCode),
    schema: text(value.payload?.schema),
    projectedAt: text(value.projected_at ?? value.projectedAt),
  };
}

export function entitlementItems(value) {
  const map = value?.payload?.entitlements;
  if (!map || typeof map !== "object" || Array.isArray(map)) {
    return [];
  }
  return Object.entries(map).map(([key, item]) => ({
    key,
    enabled: typeof item?.enabled === "boolean" ? item.enabled : null,
    quota: numberOrNull(item?.quota),
  }));
}

export function quotaRows(value) {
  if (!Array.isArray(value)) {
    return null;
  }
  return value.map((row) => ({
    metric: text(row?.metric),
    used: numberOrNull(row?.used),
    limit: numberOrNull(row?.limit),
    warnPercent: numberOrNull(row?.warn_percent ?? row?.warnPercent),
    policy: text(row?.over_limit_policy ?? row?.overLimitPolicy),
    warning: typeof row?.warning === "boolean" ? row.warning : null,
  }));
}

export function show(value) {
  return value === null || value === undefined || value === "" ? "暂无" : String(value);
}
