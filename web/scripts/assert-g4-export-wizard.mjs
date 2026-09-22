#!/usr/bin/env node
/**
 * G4 leftover: HTTP export wizard begin/complete on the hash chain.
 * Does not claim product G4 close. Does not rewrite AuditPort same-TX.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  view: read("src/views/studio/ExportWizardView.vue"),
  live: read("src/api/p3-live.js"),
  facade: read("src/api/p3.js"),
  router: read("src/router.js"),
  javaApi: read("../nexus-p3-studio/src/main/java/one/nexus/p3/report/ExportWizardController.java"),
  authorized: read("../nexus-permission-core/src/main/java/one/nexus/permission/surface/AuthorizedExport.java"),
  captures: read("../nexus-audit-core/src/main/java/one/nexus/audit/AuditCaptures.java"),
  docsIndex: read("../docs/README.md"),
  doc: read("../docs/g4-export-wizard.md"),
  claim: read("../docs/planning/团队领取任务清单_V1.1.md"),
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

requireIncludes("wizard view", files.view, [
  "g4-export-wizard-begin",
  "g4-export-wizard-complete",
  "beginExportWizard",
  "completeExportWizard",
]);
requireIncludes("p3 live", files.live, [
  "/api/v1/p3/exports/wizard/begin",
  "/api/v1/p3/exports/wizard/complete",
]);
requireIncludes("router", files.router, ['path: "/p3/exports/wizard"']);
requireIncludes("Java wizard", files.javaApi, [
  "AuthorizedExport.beginWizard",
  "AuthorizedExport.completeWizard",
  "requireExport",
]);
requireIncludes("AuthorizedExport", files.authorized, [
  "beginWizard",
  "completeWizard",
  "EXPORT_BEGUN",
  "EXPORT_COMPLETED",
]);
requireIncludes("AuditCaptures", files.captures, [
  "p3.export.begun",
  "p3.export.completed",
]);
requireIncludes("docs/README leftover rows", files.docsIndex, [
  "g4-pkce-browser-click",
  "g4-guard-deny-audit",
  "g4-export-wizard",
  "g4-export-wizard-deny",
  "g17-vue-otp-writeback",
  "g17-gm-tls-nginx-handshake",
  "g9-live-multi-jvm-replica",
  "g6-recovery-port-honesty",
  "g1-fieldpolicy-mask-second-auth",
  "g2-h5-every-page",
  "linux-arm-profile-honesty",
  "g10-file-reconcile-honesty",
]);
requireIncludes("doc", files.doc, [
  "p3.export.begun",
  "p3.export.completed",
  "Not a product export UI",
  "G18 remains closed",
]);
requireIncludes("claim list", files.claim, ["g4-export-wizard.md", "P1 staging audit-events"]);
forbidIncludes("doc", files.doc, ["closes product G4", "AuditPort rewrite", "reopen G18"]);
forbidIncludes("claim list", files.claim, ["产品 G4 已关闭", "完整 G4 关闭"]);

if (failures.length) {
  console.error("FAIL G4 export wizard leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: HTTP export wizard leftover; PKCE + guard-deny leftover rows kept; product G4 leftover");
