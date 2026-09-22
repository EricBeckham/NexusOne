#!/usr/bin/env node
/**
 * G17 leftover harness: Vue → Keycloak otp write-back honesty probe.
 * Always-green without Keycloak. Live write-back is PASS only when a
 * reachable issuer, a product Vue write-back endpoint, and a stored otp
 * credential are all proven. Default on this VM is honest SKIP.
 */
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { confirmEnrollment, startEnrollment } from "../../web/src/auth/totpEnrollment.js";
import { generate } from "../../web/src/auth/totp.js";
import {
  WRITEBACK_RESULTS,
  attemptVueOtpWriteback,
  describeOtpWritebackBridge,
  probeKeycloakOtpWriteback,
} from "../../web/src/auth/totpWriteback.js";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "../..");

function usage() {
  console.log(`Usage:
  node scripts/g17-vue-otp-writeback/probe.mjs [--out DIR] [--json-out FILE]
  node scripts/g17-vue-otp-writeback/probe.mjs --verify-report FILE
`);
}

function reportGet(file, key) {
  const lines = readFileSync(file, "utf8").split(/\r?\n/);
  for (const line of lines) {
    if (line.startsWith(`${key}=`)) {
      return line.slice(key.length + 1);
    }
  }
  return "";
}

function fail(message) {
  console.error(`FAIL: ${message}`);
  process.exit(1);
}

function verifyReport(file) {
  const required = [
    "schema_version",
    "timestamp_utc",
    "java_feature",
    "boot_parent_version",
    "docker_used",
    "vue_confirm_writes_keycloak",
    "product_writeback_endpoint",
    "keycloak_issuer_reachable",
    "oidc_discovery",
    "vue_otp_writeback",
    "otp_credential_stored",
    "recovery_codes_claimed",
    "email_mfa_claimed",
    "dengbao_claimed",
    "mipin_claimed",
    "hsm_claimed",
    "g17_close",
    "g18_status",
    "audit_port_changed",
    "redo_96",
    "redo_118",
    "redo_133",
  ];
  for (const key of required) {
    if (!reportGet(file, key)) {
      fail(`missing key ${key} in ${file}`);
    }
  }
  if (reportGet(file, "java_feature") !== "21") {
    fail("java_feature must be 21");
  }
  if (reportGet(file, "boot_parent_version") !== "4.1.1") {
    fail("boot_parent_version must be 4.1.1");
  }
  if (reportGet(file, "docker_used") !== "false") {
    fail("docker_used must be false");
  }
  if (reportGet(file, "g18_status") !== "closed_unchanged") {
    fail("g18_status must stay closed_unchanged");
  }
  if (reportGet(file, "audit_port_changed") !== "false") {
    fail("audit_port_changed must be false");
  }
  if (reportGet(file, "dengbao_claimed") !== "false") {
    fail("dengbao_claimed must stay false");
  }
  if (reportGet(file, "mipin_claimed") !== "false") {
    fail("mipin_claimed must stay false");
  }
  if (reportGet(file, "hsm_claimed") !== "false") {
    fail("hsm_claimed must stay false");
  }
  if (reportGet(file, "g17_close") !== "NOT_CLAIMED") {
    fail("g17_close must stay NOT_CLAIMED");
  }
  if (reportGet(file, "recovery_codes_claimed") !== "false") {
    fail("recovery_codes_claimed must stay false (orthogonal leftover)");
  }
  if (reportGet(file, "email_mfa_claimed") !== "false") {
    fail("email_mfa_claimed must stay false (orthogonal leftover)");
  }
  if (reportGet(file, "redo_96") !== "false") {
    fail("redo_96 must stay false");
  }
  if (reportGet(file, "redo_118") !== "false") {
    fail("redo_118 must stay false");
  }
  if (reportGet(file, "redo_133") !== "false") {
    fail("redo_133 must stay false");
  }

  const writeback = reportGet(file, "vue_otp_writeback");
  const reachable = reportGet(file, "keycloak_issuer_reachable");
  const endpoint = reportGet(file, "product_writeback_endpoint");
  const stored = reportGet(file, "otp_credential_stored");
  const vueWrites = reportGet(file, "vue_confirm_writes_keycloak");

  if (!["PASS", "SKIP", "FAIL"].includes(writeback)) {
    fail("vue_otp_writeback must be PASS/SKIP/FAIL");
  }
  if (writeback === "SKIP" && !reportGet(file, "writeback_skip_reason")) {
    fail("vue_otp_writeback=SKIP needs writeback_skip_reason");
  }
  if (writeback === "PASS") {
    if (reachable !== "true") {
      fail("vue_otp_writeback=PASS requires keycloak_issuer_reachable=true");
    }
    if (reportGet(file, "oidc_discovery") !== "PASS") {
      fail("vue_otp_writeback=PASS requires oidc_discovery=PASS");
    }
    if (endpoint !== "true") {
      fail("vue_otp_writeback=PASS requires product_writeback_endpoint=true");
    }
    if (stored !== "true") {
      fail("vue_otp_writeback=PASS requires otp_credential_stored=true");
    }
  } else {
    if (stored === "true") {
      fail("otp_credential_stored cannot be true unless vue_otp_writeback=PASS");
    }
    if (vueWrites === "true") {
      fail("vue_confirm_writes_keycloak cannot be true unless write-back PASS");
    }
  }
  const body = readFileSync(file, "utf8");
  if (/(PASSWORD|passwd|client-secret|totpSecret)=/i.test(body)) {
    fail("report must not contain password/secret material");
  }
  console.log(`PASS: report honesty gate (${file})`);
}

function kv(lines, key, value) {
  lines.push(`${key}=${value}`);
}

async function collect({ outDir, jsonOut }) {
  mkdirSync(outDir, { recursive: true, mode: 0o700 });
  const stamp = new Date().toISOString().replace(/\.\d+Z$/, "Z");
  const bridge = describeOtpWritebackBridge();
  const probe = await probeKeycloakOtpWriteback({ timeoutMs: 1500 });
  const secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
  const pending = startEnrollment({ secret, now: 59_000 }).session;
  const enrolled = confirmEnrollment(pending, generate(secret, 59_000), { now: 59_000 }).session;
  const writeback = await attemptVueOtpWriteback({
    session: enrolled,
    timeoutMs: 1500,
  });

  const lines = [];
  kv(lines, "schema_version", "1");
  kv(lines, "timestamp_utc", stamp);
  kv(lines, "java_feature", "21");
  kv(lines, "boot_parent_version", "4.1.1");
  kv(lines, "docker_used", "false");
  kv(lines, "vue_confirm_writes_keycloak", String(writeback.vueConfirmWritesKeycloak));
  kv(lines, "product_writeback_endpoint", String(writeback.productWritebackEndpoint));
  kv(lines, "keycloak_issuer_reachable", String(writeback.keycloakIssuerReachable));
  kv(lines, "oidc_discovery", writeback.oidcDiscovery);
  kv(lines, "vue_otp_writeback", writeback.result);
  kv(lines, "otp_credential_stored", String(writeback.otpCredentialStored));
  kv(lines, "writeback_skip_reason", writeback.result === WRITEBACK_RESULTS.SKIP ? writeback.reason : "");
  kv(lines, "probed_issuer", writeback.issuer || probe.issuer || "");
  kv(lines, "recovery_codes_claimed", String(bridge.recoveryCodes));
  kv(lines, "email_mfa_claimed", String(bridge.emailMfa));
  kv(lines, "dengbao_claimed", String(bridge.dengbaoClaimed));
  kv(lines, "mipin_claimed", String(bridge.mipinClaimed));
  kv(lines, "hsm_claimed", String(bridge.hsmClaimed));
  kv(lines, "g17_close", bridge.g17Close);
  kv(lines, "g18_status", bridge.g18);
  kv(lines, "audit_port_changed", String(bridge.auditPortChanged));
  kv(lines, "redo_96", "false");
  kv(lines, "redo_118", "false");
  kv(lines, "redo_133", "false");
  kv(lines, "http_driver_walk", bridge.httpDriverWalk);
  kv(lines, "enrollment_ux", bridge.enrollmentUx);

  const reportPath = join(outDir, "report.env");
  writeFileSync(reportPath, `${lines.join("\n")}\n`, { mode: 0o600 });

  const walk = {
    leftover: bridge.leftover,
    result: writeback.result,
    reason: writeback.reason,
    vueConfirmWritesKeycloak: writeback.vueConfirmWritesKeycloak,
    productWritebackEndpoint: writeback.productWritebackEndpoint,
    keycloakIssuerReachable: writeback.keycloakIssuerReachable,
    oidcDiscovery: writeback.oidcDiscovery,
    otpCredentialStored: writeback.otpCredentialStored,
    issuer: writeback.issuer || probe.issuer || "",
    g17Close: bridge.g17Close,
    g18: bridge.g18,
    recoveryCodes: bridge.recoveryCodes,
    emailMfa: bridge.emailMfa,
    timestampUtc: stamp,
    evidence: "scripts/g17-vue-otp-writeback.sh",
  };
  const walkPath = jsonOut || join(outDir, "walk.json");
  mkdirSync(dirname(walkPath), { recursive: true });
  writeFileSync(walkPath, `${JSON.stringify(walk, null, 2)}\n`);

  console.log(`wrote ${reportPath}`);
  console.log(`wrote ${walkPath}`);
  console.log(`vue_otp_writeback=${writeback.result}`);
  if (writeback.result === WRITEBACK_RESULTS.SKIP) {
    console.log(`writeback_skip_reason=${writeback.reason}`);
  }
  verifyReport(reportPath);
  return { reportPath, walkPath, writeback };
}

const args = process.argv.slice(2);
let outDir = "";
let jsonOut = "";
let verify = "";
for (let i = 0; i < args.length; i += 1) {
  if (args[i] === "--out") {
    outDir = args[++i];
  } else if (args[i] === "--json-out") {
    jsonOut = args[++i];
  } else if (args[i] === "--verify-report") {
    verify = args[++i];
  } else if (args[i] === "-h" || args[i] === "--help") {
    usage();
    process.exit(0);
  } else {
    fail(`unknown argument: ${args[i]}`);
  }
}

if (verify) {
  verifyReport(verify);
  process.exit(0);
}

const stamp = new Date().toISOString().replace(/[-:]/g, "").replace(/\.\d+Z$/, "Z");
if (!outDir) {
  outDir = join(root, ".local/g17-vue-otp-writeback", stamp);
}
await collect({ outDir, jsonOut });
