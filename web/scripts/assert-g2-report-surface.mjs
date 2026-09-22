#!/usr/bin/env node
/**
 * G2 leftover: report query/export on the same PermissionEngine.
 * Does not claim product-wide G2, physical dump, or AuditPort changes.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { DENY_CODES, REPORT_SURFACES } from "../src/permission/reportCatalog.js";
import { denyCode, requireAllowed } from "../src/permission/reportGuard.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  catalog: read("src/permission/reportCatalog.js"),
  guard: read("src/permission/reportGuard.js"),
  view: read("src/views/studio/ReportView.vue"),
  query: read("src/mobile/products/p3-studio/pages/QueryPage.vue"),
  live: read("src/api/p3-live.js"),
  facade: read("src/api/p3.js"),
  router: read("src/router.js"),
  javaCatalog: read("../nexus-permission-core/src/main/java/one/nexus/permission/surface/ReportSurfaceCatalog.java"),
  javaGuard: read("../nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/ReportSurfaceGuard.java"),
  javaApi: read("../nexus-p3-studio/src/main/java/one/nexus/p3/report/ReportController.java"),
  ledger: read("src/views/studio/LedgerListView.vue"),
  ledgerDetail: read("src/views/studio/LedgerDetailView.vue"),
  formList: read("src/views/studio/FormListView.vue"),
  formStudio: read("src/views/studio/FormStudioView.vue"),
  desktopTodos: read("src/views/studio/TodoInboxView.vue"),
  doc: read("../docs/g2-report-surface-guard.md"),
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
  "P2.PERM.0003",
  "P2.PERM.0002",
  "P2.PERM.0001",
  "PermissionEngine",
]);
requireIncludes("JS guard", files.guard, ["requireAllowed", "ENGINE", "no_grant"]);
requireIncludes("report view", files.view, [
  "REPORT_SURFACES",
  "queryReport",
  "exportReport",
  "perm.check",
  "g2-report-query",
]);
requireIncludes("H5 query", files.query, [
  "H5_SURFACES.listRead",
  "listRecords",
  "REPORT_SURFACES.export",
  "exportReport",
]);
requireIncludes("p3 live", files.live, [
  "/api/v1/p3/reports/decision",
  "/api/v1/p3/reports/query",
  "/api/v1/p3/reports/export",
]);
requireIncludes("router", files.router, ['path: "/p3/reports"']);
requireIncludes("Java catalog", files.javaCatalog, [
  'QUERY_RESOURCE = "ledger"',
  "EXPORT_RESOURCE = SurfaceCatalog.EXPORT_RESOURCE",
]);
requireIncludes("Java ReportSurfaceGuard", files.javaGuard, [
  "SurfaceGuard.require(permission",
  "requireQuery",
  "requireExport",
]);
requireIncludes("Java ReportController", files.javaApi, [
  "AuthorizedExport.submit",
  "requireQuery",
  "requireExport",
  "physical_dump",
]);
requireIncludes("doc", files.doc, [
  "ReportSurfaceGuard",
  "no_grant",
  "explicit_deny",
  "tenant_boundary",
  "P2.PERM.0003",
  "G18 stays closed",
  "not a product-wide G2",
  "physical",
]);

forbidIncludes("doc", files.doc, ["closes product G2", "AuditPort rewrite", "reopen G18"]);
forbidIncludes("report view", files.view, ["useH5Surface", "H5_SURFACES", "NxMobileShell"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  desktopTodos: files.desktopTodos,
})) {
  forbidIncludes(`desktop ${name}`, text, [
    "ReportSurfaceGuard",
    "REPORT_SURFACES",
    "queryReport",
    "exportReport",
    "NxMobileShell",
  ]);
}

if (REPORT_SURFACES.query.resource !== "ledger" || REPORT_SURFACES.export.action !== "submit") {
  failures.push("report catalog must reuse desktop ledger/export cells");
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
  console.error("FAIL G2 report surface");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: report query/export on SurfaceGuard; desktop P3 ledger/form/todo untouched; product G2 leftover");
