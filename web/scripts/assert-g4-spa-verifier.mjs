#!/usr/bin/env node
/**
 * G4 leftover: SPA-held PKCE verifier wiring.
 * Does not claim product G4. Does not redo H5 Playwright (#126) or desktop stub click (#125).
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { authorizeUrlHasSecret, challengeS256, createS256 } from "../src/mobile/auth/pkce.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const repo = join(root, "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");
const readRepo = (rel) => readFileSync(join(repo, rel), "utf8");

const files = {
  pkce: read("src/mobile/auth/pkce.js"),
  login: read("src/mobile/products/p3-studio/pages/LoginPage.vue"),
  controller: readRepo("nexus-p2-iam/src/main/java/one/nexus/p2/api/AuthController.java"),
  pkceJava: readRepo("nexus-p2-iam/src/main/java/one/nexus/p2/idp/Pkce.java"),
  leftover: readRepo("docs/g4-pkce-spa-verifier.md"),
  evidence: readRepo("docs/engineering/g4-pkce-spa-verifier-EVIDENCE.md"),
  readme: readRepo("docs/README.md"),
  desktop: read("src/api/pkce-desktop.js"),
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

requireIncludes("H5 pkce helper", files.pkce, ["createS256", "randomVerifier", "challengeS256"]);
requireIncludes("H5 login", files.login, ["createS256", "code_challenge", "mobile-pkce-login"]);
requireIncludes("AuthController", files.controller, ["code_challenge", "clientHeld", "code_verifier"]);
requireIncludes("Pkce.java", files.pkceJava, ["clientHeld", "must not mint"]);
requireIncludes("leftover doc", files.leftover, [
  "SPA",
  "code_verifier",
  "honest SKIP",
  "does not claim",
  "AuditPort",
  "human",
]);
requireIncludes("evidence", files.evidence, ["NOT_CLAIMED", "SKIP", "AuditPort"]);
requireIncludes("docs/README leftover table", files.readme, [
  "g4-pkce-spa-verifier",
  "g10-identity-drift-dry-run",
  "g2-tenant-only-catalog",
  "g3-outbox-consume-idempotent",
]);
requireIncludes("desktop BFF leftover stays", files.desktop, ["authorize-url?pkce=s256 still mints code_verifier"]);
forbidIncludes("leftover doc", files.leftover, [
  "product G4 is closed",
  "closes product G4",
  "G18 reopened",
]);

const rfcVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
const rfcChallenge = await challengeS256(rfcVerifier);
if (rfcChallenge !== "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM") {
  failures.push(`RFC 7636 vector mismatch: ${rfcChallenge}`);
}
const pair = await createS256();
if (pair.verifier.length < 43 || pair.challenge !== (await challengeS256(pair.verifier))) {
  failures.push("SPA createS256 pair is not S256");
}
if (!authorizeUrlHasSecret(`/callback?code=x&code_verifier=${pair.verifier}`)) {
  failures.push("authorize URL with verifier must be rejected");
}

if (failures.length) {
  console.error("FAIL G4 SPA verifier leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: SPA-held S256 mint wired; BFF leftover untouched; product G4 not claimed");
