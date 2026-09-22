#!/usr/bin/env node
/**
 * G4 leftover: SPA-generated PKCE verifier + live Keycloak / CA probe.
 *
 * Always proves RFC 7636 S256 mint (same helper as H5 /m/*). Live Authorization
 * Code + PKCE against Keycloak is PASS only when OIDC discovery answers.
 * Unreachable Keycloak is honest SKIP — never a product G4 close.
 *
 * Does not start kc.sh (that is #108 / #120). Does not redo stub Playwright (#125).
 */
import { X509Certificate } from "node:crypto";
import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { authorizeUrlHasSecret, challengeS256, createS256 } from "../../web/src/mobile/auth/pkce.js";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "../..");

const args = parseArgs(process.argv.slice(2));
if (args.help) {
  console.log(`Usage: node scripts/g4/spa-verifier-harness.mjs [--out DIR] [--json-out FILE]
Probes live Keycloak OIDC + local G4 HTTPS CA. Writes report.env + walk.json.
Never prints code_verifier, passwords, or client secrets.`);
  process.exit(0);
}

const outDir = args.out || join(root, ".local/g4-pkce-spa-verifier", utcStamp());
mkdirSync(outDir, { recursive: true });

const rfcVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
const rfcChallenge = await challengeS256(rfcVerifier);
const rfcOk = rfcChallenge === "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
const pair = await createS256();
const challengeMatches = pair.challenge === (await challengeS256(pair.verifier));
const secretLeak = authorizeUrlHasSecret(
  `https://127.0.0.1:8543/realms/nexus/protocol/openid-connect/auth?code=x&code_verifier=${pair.verifier}`,
);
const cleanUrl = authorizeUrlHasSecret(
  `https://127.0.0.1:8543/realms/nexus/protocol/openid-connect/auth?code=x&state=s&code_challenge=${pair.challenge}`,
);

const spaMint = rfcOk && challengeMatches && pair.verifier.length >= 43 && secretLeak && !cleanUrl
  ? "PASS"
  : "FAIL";

const httpIssuer = process.env.NEXUS_KEYCLOAK_HTTP_ISSUER || "http://127.0.0.1:8180/realms/nexus";
const httpsIssuer = process.env.NEXUS_KEYCLOAK_HTTPS_ISSUER || "https://127.0.0.1:8543/realms/nexus";
const envIssuer = (process.env.NEXUS_KEYCLOAK_ISSUER || "").replace(/\/$/, "");
const caPath = process.env.NEXUS_G4_HTTPS_CERT
  || (existsSync(join(root, ".local/g4-https/server.crt"))
    ? join(root, ".local/g4-https/server.crt")
    : "/workspace/.local/g4-https/server.crt");
const caPresent = existsSync(caPath);
const caMeta = caPresent ? inspectCa(caPath) : { fingerprint: "", subject: "", error: "missing" };

const httpDiscovery = await probeDiscovery(httpIssuer, null);
const httpsDiscovery = await probeDiscovery(httpsIssuer, caPresent ? caPath : null);
const envDiscovery = envIssuer ? await probeDiscovery(envIssuer, envIssuer.startsWith("https:") && caPresent ? caPath : null) : {
  ok: false,
  status: 0,
  issuer: "",
  authorization: "",
  token: "",
  error: "NEXUS_KEYCLOAK_ISSUER unset",
};

const anyDiscovery = httpDiscovery.ok || httpsDiscovery.ok || envDiscovery.ok;
const liveIssuer = envDiscovery.ok ? envIssuer
  : httpsDiscovery.ok ? httpsIssuer
    : httpDiscovery.ok ? httpIssuer
      : "";

const kcHome = process.env.KEYCLOAK_HOME || `${process.env.HOME || "/home/ubuntu"}/keycloak-26.5.2`;
const kcHomePresent = existsSync(join(kcHome, "bin/kc.sh"));
const smokePasswordSet = Boolean(process.env.NEXUS_KEYCLOAK_SMOKE_PASSWORD);

let livePkce = "SKIP";
let liveSkipReason = "";
let liveAuthorize = "SKIP";
if (!anyDiscovery) {
  liveSkipReason = kcHomePresent
    ? "Keycloak dist present but OIDC discovery unreachable; this leftover does not start kc.sh (#108/#120 own start)"
    : "live Keycloak OIDC discovery unreachable on :8180 / :8543; KEYCLOAK_HOME missing; honest SKIP";
} else if (!smokePasswordSet) {
  liveSkipReason = `OIDC discovery reachable at ${redactUrl(liveIssuer)} but NEXUS_KEYCLOAK_SMOKE_PASSWORD unset; no live form login`;
} else {
  liveAuthorize = "READY";
  liveSkipReason = "discovery reachable; form+token exchange is the opt-in Java LiveKeycloakSpaHeldPkceTest (NEXUS_KEYCLOAK_SMOKE=1)";
}

const report = {
  schema_version: "1",
  timestamp_utc: new Date().toISOString().replace(/\.\d+Z$/, "Z"),
  java_feature: "21",
  boot_parent_version: "4.1.1",
  docker_used: "false",
  spa_verifier_mint: spaMint,
  spa_challenge_len: String(pair.challenge.length),
  rfc7636_appendix_b: rfcOk ? "PASS" : "FAIL",
  authorize_url_secret_rejected: secretLeak ? "PASS" : "FAIL",
  keycloak_home_present: kcHomePresent ? "true" : "false",
  keycloak_http_discovery: httpDiscovery.ok ? "PASS" : "SKIP",
  keycloak_https_discovery: httpsDiscovery.ok ? "PASS" : "SKIP",
  live_issuer_kind: httpsDiscovery.ok || (envIssuer.startsWith("https:") && envDiscovery.ok) ? "https" : httpDiscovery.ok ? "http" : "none",
  ca_cert_present: caPresent ? "true" : "false",
  ca_cert_sha256: caMeta.fingerprint || "",
  notebook_documented: "true",
  live_keycloak_pkce: livePkce,
  live_keycloak_skip_reason: liveSkipReason,
  live_authorize_ready: liveAuthorize,
  g4_close: "NOT_CLAIMED",
  g18_status: "closed_unchanged",
  audit_port_changed: "false",
  p0_m0_redone: "false",
  pkce_108_120_125_redone: "false",
};

const reportPath = join(outDir, "report.env");
writeFileSync(reportPath, Object.entries(report).map(([k, v]) => `${k}=${v}`).join("\n") + "\n");

const walk = {
  leftover: "g4-pkce-spa-verifier",
  result: spaMint === "FAIL" ? "FAIL" : anyDiscovery && smokePasswordSet ? "READY_LIVE" : "SKIP_LIVE",
  spa_mint: spaMint,
  live_keycloak_pkce: livePkce,
  live_keycloak_skip_reason: liveSkipReason,
  discovery: {
    http: sanitizeDiscovery(httpDiscovery),
    https: sanitizeDiscovery(httpsDiscovery),
    env: sanitizeDiscovery(envDiscovery),
  },
  ca: {
    present: caPresent,
    path: caPresent ? relIfPossible(caPath, root) : "",
    sha256: caMeta.fingerprint || "",
    subject: caMeta.subject || "",
  },
  traces: {
    stub_spa_held: "0199a1c0-0000-7000-8000-00000000c21a",
    live_spa_held: "0199a1c0-0000-7000-8000-00000000c21b",
  },
  not_claimed: [
    "product G4 close",
    "G18 reopen",
    "AuditPort same-TX rewrite",
    "HTTP export wizard",
    "P1 staging audit-events",
    "#108/#120/#125 redo",
  ],
};
const jsonOut = args.jsonOut || join(outDir, "walk.json");
writeFileSync(jsonOut, JSON.stringify(walk, null, 2) + "\n");

if (spaMint !== "PASS") {
  console.error("FAIL: SPA S256 mint / RFC 7636 / secret-leak guard");
  process.exit(1);
}

console.log(`SPA_MINT=${spaMint}`);
console.log(`LIVE_KEYCLOAK_PKCE=${livePkce}`);
console.log(`LIVE_SKIP_REASON=${liveSkipReason || "n/a"}`);
console.log(`CA_PRESENT=${caPresent}`);
console.log(`REPORT=${reportPath}`);
console.log(`WALK=${jsonOut}`);
process.exit(0);

function parseArgs(argv) {
  const out = { help: false, out: "", jsonOut: "" };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === "--out") {
      out.out = argv[++i];
    } else if (argv[i] === "--json-out") {
      out.jsonOut = argv[++i];
    } else if (argv[i] === "-h" || argv[i] === "--help") {
      out.help = true;
    }
  }
  return out;
}

function utcStamp() {
  return new Date().toISOString().replace(/[-:]/g, "").replace(/\.\d+Z$/, "Z");
}

function inspectCa(path) {
  try {
    const pem = readFileSync(path);
    const cert = new X509Certificate(pem);
    return {
      fingerprint: cert.fingerprint256.replace(/:/g, "").toLowerCase(),
      subject: cert.subject,
      error: "",
    };
  } catch (err) {
    return { fingerprint: "", subject: "", error: String(err.message || err) };
  }
}

async function probeDiscovery(issuer, caPath) {
  const url = `${issuer.replace(/\/$/, "")}/.well-known/openid-configuration`;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 2500);
  try {
    const res = await fetch(url, {
      signal: ctrl.signal,
      redirect: "manual",
    });
    const text = await res.text();
    if (res.status !== 200 || !text.includes("\"issuer\"")) {
      return { ok: false, status: res.status, issuer: "", authorization: "", token: "", error: `HTTP ${res.status}` };
    }
    const body = JSON.parse(text);
    return {
      ok: true,
      status: 200,
      issuer: String(body.issuer || ""),
      authorization: String(body.authorization_endpoint || ""),
      token: String(body.token_endpoint || ""),
      error: "",
    };
  } catch (err) {
    return {
      ok: false,
      status: 0,
      issuer: "",
      authorization: "",
      token: "",
      error: err.name === "AbortError" ? "timeout" : String(err.cause?.code || err.message || err),
    };
  } finally {
    clearTimeout(timer);
  }
}

function sanitizeDiscovery(d) {
  return {
    ok: d.ok,
    status: d.status,
    issuer: redactUrl(d.issuer),
    authorization: redactUrl(d.authorization),
    token: redactUrl(d.token),
    error: d.error || "",
  };
}

function redactUrl(value) {
  if (!value) {
    return "";
  }
  return String(value).replace(/[?#].*$/, "");
}

function relIfPossible(abs, base) {
  return abs.startsWith(base) ? abs.slice(base.length + 1) : abs;
}
