/**
 * H5 /m/* list / detail / form / todo cells. Same resource/action pairs as
 * desktop SurfaceGuard / H5SurfaceCatalog. Not a second engine.
 */
export const ENGINE = "PermissionEngine";

export const H5_SURFACES = {
  listRead: { resource: "ledger", action: "read" },
  listWrite: { resource: "ledger", action: "write" },
  formRead: { resource: "form", action: "read" },
  formWrite: { resource: "form", action: "write" },
  todoRead: { resource: "process.task", action: "read" },
  todoApprove: { resource: "process.task", action: "approve" },
  todoReject: { resource: "process.task", action: "reject" },
  todoWithdraw: { resource: "process.instance", action: "withdraw" },
  instanceStart: { resource: "process.instance", action: "start" },
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
