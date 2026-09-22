/**
 * Report query / export cells. Same pairs as desktop SurfaceGuard /
 * ReportSurfaceCatalog. Not a second engine. Not a physical dump.
 */
export const ENGINE = "PermissionEngine";

export const REPORT_SURFACES = {
  query: { resource: "ledger", action: "read" },
  export: { resource: "ledger.export.csv", action: "submit" },
};

export const DENY_CODES = {
  no_grant: "P2.PERM.0003",
  explicit_deny: "P2.PERM.0002",
  tenant_boundary: "P2.PERM.0001",
  empty_scope: "P2.PERM.0004",
  duty_conflict: "P2.PERM.0014",
};

export function denyCode(reason) {
  return DENY_CODES[reason] || DENY_CODES.no_grant;
}

export function cellKey(cell) {
  return `${cell.resource}/${cell.action}`;
}
