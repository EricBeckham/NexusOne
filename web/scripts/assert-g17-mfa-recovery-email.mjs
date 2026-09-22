#!/usr/bin/env node
/**
 * G17 leftover: recovery codes + email MFA request contract.
 * Extends #96/#118/#133. Does not write Keycloak otp from Vue.
 * Does not claim 等保/密评/G17 closed.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  EMAIL_MFA_CODES,
  requestEmailOtp,
} from "../src/auth/emailMfa.js";
import {
  RECOVERY_CODES,
  consumeRecoveryCode,
  issueRecoveryCodes,
} from "../src/auth/recoveryCodes.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  recovery: read("src/auth/recoveryCodes.js"),
  email: read("src/auth/emailMfa.js"),
  enroll: read("src/auth/totpEnrollment.js"),
  recoveryView: read("src/views/org/RecoveryCodesView.vue"),
  emailView: read("src/views/org/EmailMfaView.vue"),
  totpView: read("src/views/org/TotpEnrollView.vue"),
  login: read("src/views/LoginView.vue"),
  router: read("src/router.js"),
  shell: read("src/layout/AppShell.vue"),
  doc: read("../docs/g17-admin-mfa-recovery-email.md"),
  catalog: read("../docs/README.md"),
  totpDoc: read("../docs/g17-totp-enrollment-ux.md"),
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

requireIncludes("recovery helper", files.recovery, ["SHA-256", "不写回 Keycloak", "不重开 G18"]);
requireIncludes("email helper", files.email, [
  "P2.IAM.SMTP_DISABLED",
  "诚实 SKIP",
  "不把动态码写回 Keycloak",
]);
requireIncludes("totp leftover kept", files.enroll, [
  "P2.IAM.MFA_REQUIRED",
  "TOTP_VERIFY_FAILED",
  "不重开 G18",
]);
requireIncludes("recovery view", files.recoveryView, [
  "recovery-issue",
  "recovery-code",
  "recovery-consume",
  "recovery-error",
]);
requireIncludes("email view", files.emailView, [
  "email-mfa-request",
  "email-mfa-skip",
  "email-mfa-error",
]);
requireIncludes("enroll sibling links", files.totpView, [
  "goto-recovery-codes",
  "goto-email-mfa",
  "没有把 otp 凭据写入 Keycloak",
]);
requireIncludes("login leftovers kept", files.login, [
  "stub-login",
  "pkce-login",
  "admin-password-login",
  "goto-totp-enroll",
  "goto-recovery-codes",
]);
requireIncludes("router", files.router, [
  'path: "/p2/mfa/enroll"',
  'path: "/p2/mfa/recovery"',
  'path: "/p2/mfa/email"',
]);
requireIncludes("shell", files.shell, "/p2/mfa/recovery");
requireIncludes("doc", files.doc, [
  "G18 stays closed",
  "honest SKIP",
  "P2.IAM.SMTP_DISABLED",
  "does not write",
]);
requireIncludes("catalog keeps both leftover rows", files.catalog, [
  "g1-queryast-boundsql-guard",
  "g17-admin-mfa-recovery-email",
  "g5-mid-publish-failure",
  "g2-tenant-only-catalog",
  "g2-todo-http-surface",
  "g4-export-wizard",
  "g4-p1-staging-audit-events",
  "g9-sentinel-cluster-honesty",
  "g15-native-bridge-stub",
  "g4-pkce-spa-verifier",
  "g10-identity-drift-dry-run",
]);
requireIncludes("totp leftover still documented", files.totpDoc, [
  "G18 stays closed",
  "second authenticator",
]);
forbidIncludes("doc", files.doc, ["closes G17", "等保关闭", "密评关闭", "reopen G18"]);
forbidIncludes("recovery view", files.recoveryView, ["CONFIGURE_TOTP", "write otp"]);

const issued = issueRecoveryCodes({ random: () => 4 });
if (!issued.ok || issued.session.plaintext.length !== 8) {
  failures.push("issueRecoveryCodes must mint 8 codes");
}
const consumed = await consumeRecoveryCode(issued.session, issued.session.plaintext[0]);
if (!consumed.ok) {
  failures.push("first recovery code must consume");
}
const reuse = await consumeRecoveryCode(consumed.session, issued.session.plaintext[0]);
if (reuse.code !== RECOVERY_CODES.RECOVERY_ALREADY_USED) {
  failures.push("reused recovery code must be RECOVERY_ALREADY_USED");
}
const emailSkip = requestEmailOtp("admin-a@example.com");
if (!emailSkip.skip || emailSkip.code !== EMAIL_MFA_CODES.SMTP_DISABLED) {
  failures.push("default email MFA request must SKIP with SMTP_DISABLED");
}

if (failures.length) {
  console.error("FAIL G17 MFA recovery / email leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log(
  "PASS: recovery codes + email MFA SKIP contract; TOTP leftover kept; G18 closed; G17 not claimed",
);
