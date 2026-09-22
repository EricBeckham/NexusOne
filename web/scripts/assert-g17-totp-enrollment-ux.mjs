#!/usr/bin/env node
/**
 * G17 leftover: in-app TOTP enrollment UI / copy / error paths.
 * Does not redo AdminMfaPolicy, 三权, Tongsuo, or claim 等保/密评/G17 closed.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { generate } from "../src/auth/totp.js";
import {
  ENROLL_CODES,
  MFA_REQUIRED_COPY,
  confirmEnrollment,
  startEnrollment,
} from "../src/auth/totpEnrollment.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  totp: read("src/auth/totp.js"),
  enroll: read("src/auth/totpEnrollment.js"),
  view: read("src/views/org/TotpEnrollView.vue"),
  login: read("src/views/LoginView.vue"),
  router: read("src/router.js"),
  shell: read("src/layout/AppShell.vue"),
  doc: read("../docs/g17-totp-enrollment-ux.md"),
  pkce: read("src/views/login-pkce.browser.test.js"),
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

requireIncludes("totp helper", files.totp, ["RFC 6238", "HmacSHA1", "otpauth://totp/"]);
requireIncludes("enrollment copy", files.enroll, [
  "P2.IAM.MFA_REQUIRED",
  "TOTP_CODE_FORMAT",
  "TOTP_VERIFY_FAILED",
  "TOTP_ALREADY_ENROLLED",
  "不重开 G18",
]);
requireIncludes("enroll view", files.view, [
  "totp-start",
  "totp-secret",
  "totp-otpauth",
  "totp-code",
  "totp-confirm",
  "totp-error",
  "totp-success",
]);
requireIncludes("login", files.login, [
  "stub-login",
  "pkce-login",
  "admin-password-login",
  "mfa-required-panel",
  "goto-totp-enroll",
]);
requireIncludes("router", files.router, ['path: "/p2/mfa/enroll"']);
requireIncludes("shell", files.shell, "/p2/mfa/enroll");
requireIncludes("doc", files.doc, [
  "G18 stays closed",
  "not a second authenticator",
  "P2.IAM.MFA_REQUIRED",
  "recovery codes",
]);
forbidIncludes("doc", files.doc, ["closes G17", "等保关闭", "密评关闭", "reopen G18"]);
forbidIncludes("login", files.login, ["CONFIGURE_TOTP", "Tongsuo"]);
requireIncludes("pkce leftover untouched", files.pkce, ["data-testid=pkce-login"]);

const secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
if (generate(secret, 59_000) !== "287082") {
  failures.push("RFC 6238 6-digit fixture must stay 287082");
}
const pending = startEnrollment({ secret, now: 59_000 }).session;
if (confirmEnrollment(pending, "12").code !== ENROLL_CODES.TOTP_CODE_FORMAT) {
  failures.push("12 must be TOTP_CODE_FORMAT");
}
if (MFA_REQUIRED_COPY.body.includes("重开 G18") && !MFA_REQUIRED_COPY.body.includes("不重开 G18")) {
  failures.push("MFA_REQUIRED copy must not reopen G18");
}

if (failures.length) {
  console.error("FAIL G17 TOTP enrollment UX");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log(
  "PASS: TOTP enrollment UX/copy/error paths; stub-login + pkce-login kept; G18 closed; G17 not claimed",
);
