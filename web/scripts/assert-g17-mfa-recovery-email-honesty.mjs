#!/usr/bin/env node
/**
 * G17 leftover: recovery-code / email delivery honesty beyond #154.
 * Does not redo #96/#118/#133/#154/#158/#160.
 * Does not claim 等保/密评/G17 closed. G18 stays closed.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { EMAIL_MFA_CODES, requestEmailOtp } from "../src/auth/emailMfa.js";
import {
  consumeRecoveryCode,
  issueRecoveryCodes,
  RECOVERY_CODES,
} from "../src/auth/recoveryCodes.js";
import {
  HONESTY_REASONS,
  HONESTY_RESULTS,
  describeRecoveryEmailHonesty,
  probeRecoveryEmailHonesty,
} from "../src/auth/recoveryEmailHonesty.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  honesty: read("src/auth/recoveryEmailHonesty.js"),
  email: read("src/auth/emailMfa.js"),
  recovery: read("src/auth/recoveryCodes.js"),
  doc: read("../docs/g17-mfa-recovery-email-honesty.md"),
  sibling: read("../docs/g17-admin-mfa-recovery-email.md"),
  writeback: read("../docs/g17-vue-otp-writeback.md"),
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

requireIncludes("honesty helper", files.honesty, [
  "g17-mfa-recovery-email-honesty",
  "mailbox_not_observed",
  "recovery_authn_codes_not_enrolled",
  "NOT_CLAIMED",
  "#154",
  "#96",
  "#118",
  "#133",
  "#158",
  "#160",
]);
requireIncludes("#154 email leftover kept", files.email, [
  "P2.IAM.SMTP_DISABLED",
  "诚实 SKIP",
]);
requireIncludes("#154 recovery leftover kept", files.recovery, [
  "不写回 Keycloak",
  "不重开 G18",
]);
requireIncludes("doc", files.doc, [
  "G18 stays closed",
  "honest SKIP",
  "NOT_CLAIMED",
  "g17-admin-mfa-recovery-email",
  "#96",
  "#118",
  "#133",
  "#154",
  "#158",
  "#160",
]);
requireIncludes("sibling leftover pointer", files.sibling, [
  "g17-mfa-recovery-email-honesty.md",
]);
requireIncludes("write-back leftover still present", files.writeback, [
  "g17-admin-mfa-recovery-email.md",
]);
requireIncludes("docs/README leftover rows", files.docsIndex, [
  "g17-admin-mfa-recovery-email",
  "g17-mfa-recovery-email-honesty",
  "g17-vue-otp-writeback",
  "g4-export-wizard-deny",
  "g10-file-reconcile-honesty",
  "g15-wx-dt-prod-gap-honesty",
  "g17-gm-tls-nginx-handshake",
]);
requireIncludes("claim list", files.claim, [
  "g17-mfa-recovery-email-honesty.md",
  "g17-admin-mfa-recovery-email.md",
  "g17-vue-otp-writeback.md",
]);
requireIncludes("root README", files.readme, ["g17-mfa-recovery-email-honesty.md"]);
forbidIncludes("doc", files.doc, ["closes G17", "等保关闭", "密评关闭", "reopen G18"]);
forbidIncludes("claim list", files.claim, ["等保关闭", "产品 G17 已关闭", "宣称完整 G17 关闭"]);

const bridge = describeRecoveryEmailHonesty();
if (bridge.vueWritesKeycloakOtp || bridge.smtpSendsMail || bridge.mailboxObserved) {
  failures.push("honesty bridge must not claim Vue write / SMTP send / mailbox");
}
if (bridge.g17Close !== "NOT_CLAIMED" || bridge.g18 !== "closed_unchanged") {
  failures.push("honesty bridge must not claim G17 or reopen G18");
}

const issued = issueRecoveryCodes({ random: () => 5 });
if (!issued.ok || issued.session.plaintext.length !== 8) {
  failures.push("#154 issueRecoveryCodes must still mint 8 codes");
}
const consumed = await consumeRecoveryCode(issued.session, issued.session.plaintext[0]);
if (!consumed.ok) {
  failures.push("#154 first recovery code must still consume");
}
const reuse = await consumeRecoveryCode(consumed.session, issued.session.plaintext[0]);
if (reuse.code !== RECOVERY_CODES.RECOVERY_ALREADY_USED) {
  failures.push("#154 reused recovery code must still be RECOVERY_ALREADY_USED");
}
const emailSkip = requestEmailOtp("admin-a@example.com");
if (!emailSkip.skip || emailSkip.code !== EMAIL_MFA_CODES.SMTP_DISABLED) {
  failures.push("#154 default email MFA request must still SKIP with SMTP_DISABLED");
}

const probe = await probeRecoveryEmailHonesty({
  smtpEnabled: false,
  issuers: ["http://127.0.0.1:65533/realms/nexus"],
  timeoutMs: 50,
  fetchImpl: async () => {
    throw new Error("ECONNREFUSED");
  },
});
if (probe.emailDelivery !== HONESTY_RESULTS.SKIP || probe.recoveryLive !== HONESTY_RESULTS.SKIP) {
  failures.push("default live delivery must SKIP");
}
if (probe.emailDeliverySkipReason !== HONESTY_REASONS.SMTP_DISABLED) {
  failures.push("default email SKIP reason must be SMTP_DISABLED");
}
if (probe.g17Close !== "NOT_CLAIMED" || probe.vueWritesKeycloakOtp) {
  failures.push("probe must not claim G17 or Vue Keycloak otp write");
}

if (failures.length) {
  console.error("FAIL G17 MFA recovery / email delivery honesty leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log(
  "PASS: recovery/email delivery honesty; #154 leftover kept; G18 closed; G17 not claimed",
);
