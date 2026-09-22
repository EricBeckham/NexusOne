#!/usr/bin/env node
/**
 * G17 leftover: Vue → Keycloak otp write-back honesty.
 * Does not redo #96/#118/#133. Does not claim 等保/密评/G17 closed.
 * Recovery codes / email MFA stay orthogonal.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { generate } from "../src/auth/totp.js";
import { confirmEnrollment, startEnrollment } from "../src/auth/totpEnrollment.js";
import {
  WRITEBACK_REASONS,
  WRITEBACK_RESULTS,
  attemptVueOtpWriteback,
  describeOtpWritebackBridge,
} from "../src/auth/totpWriteback.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  writeback: read("src/auth/totpWriteback.js"),
  enroll: read("src/auth/totpEnrollment.js"),
  view: read("src/views/org/TotpEnrollView.vue"),
  doc: read("../docs/g17-vue-otp-writeback.md"),
  ux: read("../docs/g17-totp-enrollment-ux.md"),
  driver: read("../docs/g17-admin-totp-enrollment.md"),
  docsIndex: read("../docs/README.md"),
  claim: read("../docs/planning/团队领取任务清单_V1.1.md"),
  readme: read("../README.md"),
};

const failures = [];

function requireIncludes(name, text, snippets) {
  for (const snippet of snippets) {
    if (!text.includes(snippet)) {
      failures.push(`${name} must include ${JSON.stringify(snippet)}`);
    }
  }
}

function forbidIncludes(name, text, snippets) {
  for (const snippet of snippets) {
    if (text.includes(snippet)) {
      failures.push(`${name} must not include ${JSON.stringify(snippet)}`);
    }
  }
}

requireIncludes("write-back bridge", files.writeback, [
  "vueConfirmWritesKeycloak: false",
  "g17Close: \"NOT_CLAIMED\"",
  "product_vue_writeback_endpoint_absent",
  "#118",
  "#133",
  "#96",
]);
requireIncludes("enrollment leftover still local", files.enroll, [
  "Does not write a Keycloak otp credential",
]);
requireIncludes("enroll view honesty panel", files.view, [
  "totp-writeback-panel",
  "totp-writeback-status",
  "totp-writeback-reason",
  "attemptVueOtpWriteback",
  "totp-start",
  "totp-confirm",
]);
requireIncludes("doc", files.doc, [
  "G18 stays closed",
  "honest SKIP",
  "NOT_CLAIMED",
  "Recovery codes",
  "Email MFA",
  "#96",
  "#118",
  "#133",
]);
requireIncludes("UX leftover pointer", files.ux, ["g17-vue-otp-writeback.md"]);
requireIncludes("driver leftover pointer", files.driver, ["g17-vue-otp-writeback.md"]);
requireIncludes("docs/README leftover rows", files.docsIndex, [
  "g17-admin-mfa",
  "g17-admin-totp-enrollment",
  "g17-totp-enrollment-ux",
  "g17-vue-otp-writeback",
  "g17-gm-tls-nginx-dual-stack",
  "g17-separation-of-duties",
  "g15-native-bridge-stub",
  "g4-p1-staging-audit-events",
  "g17-admin-mfa-recovery-email",
  "s0-secret-rotate-without-restart",
  "g2-ops-pg-dump",
]);
requireIncludes("claim list", files.claim, [
  "g17-vue-otp-writeback.md",
  "g17-totp-enrollment-ux.md",
  "g17-admin-totp-enrollment.md",
  "恢复码",
  "邮箱 MFA",
]);
requireIncludes("root README", files.readme, ["g17-vue-otp-writeback.md"]);
forbidIncludes("doc", files.doc, ["closes G17", "等保关闭", "密评关闭", "reopen G18"]);
forbidIncludes("claim list", files.claim, ["完整 G17 关闭", "等保关闭", "产品 G17 已关闭"]);

const secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
const pending = startEnrollment({ secret, now: 59_000 }).session;
const enrolled = confirmEnrollment(pending, generate(secret, 59_000), { now: 59_000 });
if (!enrolled.ok || enrolled.session.status !== "enrolled") {
  failures.push("local confirmEnrollment must still succeed without Keycloak");
}

const bridge = describeOtpWritebackBridge();
if (bridge.vueConfirmWritesKeycloak || bridge.productWritebackEndpoint || bridge.recoveryCodes || bridge.emailMfa) {
  failures.push("bridge must stay honest: no Vue write, no recovery/email MFA");
}
if (bridge.g17Close !== "NOT_CLAIMED" || bridge.g18 !== "closed_unchanged") {
  failures.push("bridge must not claim G17 or reopen G18");
}

const skipped = await attemptVueOtpWriteback({
  session: enrolled.session,
  issuers: ["http://127.0.0.1:65533/realms/nexus"],
  timeoutMs: 50,
  fetchImpl: async () => {
    throw new Error("ECONNREFUSED");
  },
});
if (skipped.result !== WRITEBACK_RESULTS.SKIP) {
  failures.push("unreachable Keycloak must SKIP");
}
if (skipped.reason !== WRITEBACK_REASONS.KEYCLOAK_ISSUER_UNREACHABLE) {
  failures.push("unreachable Keycloak SKIP reason must be keycloak_issuer_unreachable");
}
if (skipped.vueConfirmWritesKeycloak || skipped.otpCredentialStored) {
  failures.push("SKIP must not claim a stored Keycloak otp credential");
}

if (failures.length) {
  console.error("FAIL G17 Vue Keycloak otp write-back leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log(
  "PASS: Vue otp write-back honesty; #96/#118/#133 leftover rows kept; G18 closed; G17 not claimed",
);
