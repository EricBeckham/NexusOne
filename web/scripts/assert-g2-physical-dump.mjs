#!/usr/bin/env node
/**
 * G2 leftover: physical dump / raw export on the same PermissionEngine.
 * Does not claim product-wide G2, report Task redo, or AuditPort changes.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { DENY_CODES, DUMP_SURFACES } from "../src/permission/dumpCatalog.js";
import { denyCode, requireAllowed } from "../src/permission/dumpGuard.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  catalog: read("src/permission/dumpCatalog.js"),
  guard: read("src/permission/dumpGuard.js"),
  view: read("src/views/studio/DumpView.vue"),
  live: read("src/api/p3-live.js"),
  facade: read("src/api/p3.js"),
  router: read("src/router.js"),
  javaCatalog: read("../nexus-permission-core/src/main/java/one/nexus/permission/surface/DumpSurfaceCatalog.java"),
  javaGuard: read("../nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/DumpSurfaceGuard.java"),
  javaApi: read("../nexus-p3-studio/src/main/java/one/nexus/p3/dump/DumpController.java"),
  ledger: read("src/views/studio/LedgerListView.vue"),
  ledgerDetail: read("src/views/studio/LedgerDetailView.vue"),
  formList: read("src/views/studio/FormListView.vue"),
  formStudio: read("src/views/studio/FormStudioView.vue"),
  desktopTodos: read("src/views/studio/TodoInboxView.vue"),
  report: read("src/views/studio/ReportView.vue"),
  doc: read("../docs/g2-physical-dump-surface.md"),
};

const failures = [];

function requireIncludes(name, text, snippets) {
  for (const snippet of snippets) {
    if (!text.includes(snippet)) {
      failures.push(`${name} must include ${snippet}`);
    }
  }
}

function forbidIncludes(name, text, snippets) {
  for (const snippet of snippets) {
    if (text.includes(snippet)) {
      failures.push(`${name} must not include ${snippet}`);
    }
  }
}

requireIncludes("JS catalog", files.catalog, [
  'resource: "ledger"',
  'action: "read"',
  'resource: "ledger.export.csv"',
  'action: "submit"',
  "PermissionEngine",
]);
requireIncludes("JS guard", files.guard, ["requireAllowed", "ENGINE", "no_grant"]);
requireIncludes("dump view", files.view, [
  "DUMP_SURFACES",
  "exportDump",
  "perm.check",
  "g2-dump-export",
]);
requireIncludes("p3 live", files.live, [
  "/api/v1/p3/dumps/decision",
  "/api/v1/p3/dumps/export",
]);
requireIncludes("router", files.router, ['path: "/p3/dumps"']);
requireIncludes("Java catalog", files.javaCatalog, [
  'READ_RESOURCE = "ledger"',
  "DUMP_RESOURCE = SurfaceCatalog.EXPORT_RESOURCE",
]);
requireIncludes("Java DumpSurfaceGuard", files.javaGuard, [
  "SurfaceGuard.require(permission",
  "requireRead",
  "requireDump",
]);
requireIncludes("Java DumpController", files.javaApi, [
  "AuthorizedExport.writeProjected",
  "requireDump",
  "requireRead",
  "physical_dump",
]);
requireIncludes("doc", files.doc, [
  "DumpSurfaceGuard",
  "no_grant",
  "explicit_deny",
  "tenant_boundary",
  "P2.PERM.0003",
  "G18 stays closed",
  "not a product-wide G2",
  "writeProjected",
]);

forbidIncludes("doc", files.doc, ["closes product G2", "AuditPort rewrite", "reopen G18"]);
forbidIncludes("dump view", files.view, ["useH5Surface", "H5_SURFACES", "NxMobileShell"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  desktopTodos: files.desktopTodos,
  report: files.report,
})) {
  forbidIncludes(`desktop ${name}`, text, [
    "DumpSurfaceGuard",
    "DUMP_SURFACES",
    "exportDump",
    "dumpDecision",
  ]);
}

if (DUMP_SURFACES.read.resource !== "ledger" || DUMP_SURFACES.dump.action !== "submit") {
  failures.push("dump catalog must reuse desktop ledger/export cells");
}
if (denyCode("no_grant") !== "P2.PERM.0003" || DENY_CODES.explicit_deny !== "P2.PERM.0002") {
  failures.push("deny codes must match desktop PermissionException");
}
try {
  requireAllowed({ allowed: false, reason: "tenant_boundary" });
  failures.push("tenant_boundary must deny");
} catch (err) {
  if (err.code !== "P2.PERM.0001") {
    failures.push(`tenant_boundary must be P2.PERM.0001, got ${err.code}`);
  }
}

if (failures.length) {
  console.error("FAIL G2 physical dump surface");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: physical dump / raw export on SurfaceGuard; desktop P3 ledger/form/todo/report untouched; product G2 leftover");
