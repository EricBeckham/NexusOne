/**
 * Dump / raw export cells. Same pairs as desktop SurfaceGuard /
 * DumpSurfaceCatalog. Not a second engine. Not report Task submit.
 */
export const ENGINE = "PermissionEngine";

export const DUMP_SURFACES = {
  read: { resource: "ledger", action: "read" },
  dump: { resource: "ledger.export.csv", action: "submit" },
};

export { DENY_CODES, denyCode } from "./reportCatalog.js";
