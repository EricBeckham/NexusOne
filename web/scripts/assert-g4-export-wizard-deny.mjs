#!/usr/bin/env node
/**
 * G4 leftover after #152: HTTP export wizard complete-deny on the hash chain.
 * Does not claim product G4 close. Keeps sibling leftover rows.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  view: read("src/views/studio/ExportWizardView.vue"),
  test: read("src/views/studio/export-wizard.browser.test.js"),
  javaApi: read("../nexus-p3-studio/src/main/java/one/nexus/p3/report/ExportWizardController.java"),
  authorized: read("../nexus-permission-core/src/main/java/one/nexus/permission/surface/AuthorizedExport.java"),
  denyIt: read("../nexus-bootstrap/src/test/java/one/nexus/bootstrap/G4ExportWizardDenyIT.java"),
  denyUnit: read("../nexus-permission-core/src/test/java/one/nexus/permission/surface/G4ExportWizardDenyAuditTest.java"),
  docsIndex: read("../docs/README.md"),
  doc: read("../docs/g4-export-wizard-deny.md"),
  claim: read("../docs/planning/团队领取任务清单_V1.1.md"),
  readme: read("../README.md"),
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
  "g4-export-wizard-error",
]);
requireIncludes("vue deny tests", files.test, [
  "P2.PERM.0003",
  "P2.PERM.0002",
  "g4-export-wizard-error",
]);
requireIncludes("Java wizard", files.javaApi, [
  "requireExport",
  "AuthorizedExport.completeWizard",
]);
requireIncludes("AuthorizedExport", files.authorized, [
  "completeWizard",
  "appendNewTransaction",
]);
requireIncludes("deny IT", files.denyIt, [
  "P2.PERM.0002",
  "EXPORT_COMPLETED",
  "verifyChain",
]);
requireIncludes("deny unit", files.denyUnit, [
  "completeAfterBeginDenyWritesPairAndSkipsIsolatedExport",
  "EXPORT_SUBMITTED",
]);
requireIncludes("docs/README leftover rows", files.docsIndex, [
  "g4-export-wizard",
  "g4-export-wizard-deny",
  "g4-p1-staging-audit-events",
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
  "complete-deny",
  "p2.permission.denied",
  "isolation.guard.reject",
  "Not a product export UI",
  "G18 remains closed",
]);
requireIncludes("claim list", files.claim, [
  "g4-export-wizard-deny.md",
  "g4-export-wizard.md",
]);
requireIncludes("root README", files.readme, ["g4-export-wizard-deny.md"]);
forbidIncludes("doc", files.doc, ["closes product G4", "AuditPort rewrite", "reopen G18"]);
forbidIncludes("claim list", files.claim, ["产品 G4 已关闭", "完整 G4 关闭"]);

if (failures.length) {
  console.error("FAIL G4 export wizard complete-deny leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: HTTP export wizard complete-deny leftover; sibling leftover rows kept; product G4 leftover");
