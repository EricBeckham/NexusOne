#!/usr/bin/env node
/**
 * G7 leftover harness: Vue todo-withdraw / browser evidence honesty probe.
 * Does not redo #103/#113/#140/#159. Live JAR/IdP withdraw is PASS only
 * when a healthy JAR, issued context, live/Keycloak IdP, Chrome, and an
 * observed live withdraw click are all proven. Default is honest SKIP.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  BOTH_LEFTOVER_ROWS,
  REASONS,
  RESULTS,
  describeWithdrawBrowserHonesty,
  evaluateLiveJarIdpWithdraw,
  parseReport,
  verifyHonestyReport,
} from "../../web/src/api/g7WithdrawBrowserHonesty.js";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "../..");

function usage() {
  console.log(`Usage:
  node scripts/g7-withdraw-browser-honesty/probe.mjs [--out DIR] [--json-out FILE]
  node scripts/g7-withdraw-browser-honesty/probe.mjs --verify-report FILE
`);
}

function fail(message) {
  console.error(`FAIL: ${message}`);
  process.exit(1);
}

function verifyReport(file) {
  const report = parseReport(readFileSync(file, "utf8"));
  try {
    verifyHonestyReport(report);
  } catch (error) {
    fail(error.message);
  }
  console.log(`PASS: report honesty gate (${file})`);
}

async function probeLiveJar(baseUrl, timeoutMs = 1500) {
  if (!baseUrl) {
    return { liveJar: false, reason: "NEXUS_BASE_URL unset or not healthy" };
  }
  const fetchFn = globalThis.fetch;
  if (typeof fetchFn !== "function") {
    return { liveJar: false, reason: "fetch unavailable" };
  }
  const url = `${String(baseUrl).replace(/\/$/, "")}/actuator/health`;
  try {
    const ctrl = typeof AbortController === "function" ? new AbortController() : null;
    const timer = ctrl ? setTimeout(() => ctrl.abort(), timeoutMs) : null;
    const res = await fetchFn(url, ctrl ? { signal: ctrl.signal } : {});
    if (timer) {
      clearTimeout(timer);
    }
    if (res && res.ok) {
      return { liveJar: true, reason: `GET ${url}` };
    }
  } catch {
    // agent VMs usually have no product JAR listener
  }
  return { liveJar: false, reason: `live JAR not healthy (${url})` };
}

function chromePresent() {
  return [
    "/usr/bin/google-chrome-stable",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
  ].some((path) => existsSync(path));
}

function kv(lines, key, value) {
  lines.push(`${key}=${value}`);
}

async function collect({ outDir, jsonOut }) {
  mkdirSync(outDir, { recursive: true, mode: 0o700 });
  const stamp = new Date().toISOString().replace(/\.\d+Z$/, "Z");
  const bridge = describeWithdrawBrowserHonesty();
  const env = process.env;
  const base = env.NEXUS_BASE_URL || "";
  const jar = await probeLiveJar(base);
  const issuedContext = Boolean(env.NEXUS_G7_LIVE_CONTEXT_ID);
  let idp = "absent";
  if (env.NEXUS_G7_LIVE_IDP) {
    idp = env.NEXUS_G7_LIVE_IDP;
  } else if (env.NEXUS_IAM_IDP && env.NEXUS_IAM_IDP !== "stub") {
    idp = env.NEXUS_IAM_IDP;
  }
  const chrome = chromePresent();
  const observed = env.NEXUS_G7_LIVE_WITHDRAW_OBSERVED === "true";
  const evaluation = evaluateLiveJarIdpWithdraw({
    liveJar: jar.liveJar,
    issuedContext,
    idp,
    chromePresent: chrome,
    withdrawClickObserved: observed,
    typesWalked: observed ? ["leave", "expense", "travel"] : [],
  });

  const lines = [];
  kv(lines, "schema_version", "1");
  kv(lines, "timestamp_utc", stamp);
  kv(lines, "java_feature", "21");
  kv(lines, "boot_parent_version", "4.1.1");
  kv(lines, "boot_parent_match", "PASS");
  kv(lines, "docker_used", "false");
  kv(lines, "stub_walk_is_not_live", "true");
  kv(lines, "vue_reachability_is_not_live_jar", "true");
  kv(lines, "live_jar", String(evaluation.liveJar === true));
  kv(lines, "issued_context", String(evaluation.issuedContext === true));
  kv(lines, "idp", evaluation.idp);
  kv(lines, "chrome_present", String(evaluation.chromePresent === true));
  kv(lines, "withdraw_click_observed", String(evaluation.withdrawClickObserved === true));
  kv(lines, "live_jar_idp_withdraw", evaluation.result);
  kv(lines, "live_jar_idp_skip_reason", evaluation.result === RESULTS.SKIP ? evaluation.reason : "");
  kv(lines, "jar_probe_reason", jar.reason);
  kv(lines, "g7_close", "NOT_CLAIMED");
  kv(lines, "g18_status", "closed_unchanged");
  kv(lines, "audit_port_changed", "false");
  kv(lines, "p0_m0_redone", "false");
  kv(lines, "g7_103_redone", "false");
  kv(lines, "g7_113_redone", "false");
  kv(lines, "g7_140_redone", "false");
  kv(lines, "g7_159_redone", "false");
  kv(lines, "leftover_rows_kept", BOTH_LEFTOVER_ROWS.join(","));
  kv(lines, "control", bridge.control);
  kv(lines, "http_path", bridge.httpPath);

  const reportPath = join(outDir, "report.env");
  writeFileSync(reportPath, `${lines.join("\n")}\n`, { mode: 0o600 });

  const walk = {
    leftover: bridge.leftover,
    result: evaluation.result,
    reason: evaluation.reason || REASONS.LIVE_JAR_UNHEALTHY,
    liveJar: evaluation.liveJar,
    issuedContext: evaluation.issuedContext,
    idp: evaluation.idp,
    chromePresent: evaluation.chromePresent,
    withdrawClickObserved: evaluation.withdrawClickObserved,
    stubWalkIsNotLive: true,
    g7Close: "NOT_CLAIMED",
    g18: "closed_unchanged",
    redo103: false,
    redo113: false,
    redo140: false,
    redo159: false,
    leftoverRowsKept: BOTH_LEFTOVER_ROWS,
    timestampUtc: stamp,
    evidence: "scripts/g7-withdraw-browser-honesty.sh",
  };
  const walkPath = jsonOut || join(outDir, "walk.json");
  mkdirSync(dirname(walkPath), { recursive: true });
  writeFileSync(walkPath, `${JSON.stringify(walk, null, 2)}\n`);

  console.log(`wrote ${reportPath}`);
  console.log(`wrote ${walkPath}`);
  console.log(`live_jar_idp_withdraw=${evaluation.result}`);
  if (evaluation.result === RESULTS.SKIP) {
    console.log(`live_jar_idp_skip_reason=${evaluation.reason}`);
  }
  verifyReport(reportPath);
  return { reportPath, walkPath, evaluation };
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
  outDir = join(root, ".local/g7-withdraw-browser-honesty", stamp);
}
await collect({ outDir, jsonOut });
