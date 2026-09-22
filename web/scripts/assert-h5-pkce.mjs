#!/usr/bin/env node
/**
 * G15 leftover: H5 Authorization Code + PKCE on /m/*.
 * Does not claim product G15, native APP WebView, or WeChat/DingTalk production.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { authorizeUrlHasSecret, challengeS256 } from "../src/mobile/auth/pkce.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  login: read("src/mobile/products/p3-studio/pages/LoginPage.vue"),
  callback: read("src/mobile/products/p3-studio/pages/CallbackPage.vue"),
  client: read("src/api/client.js"),
  router: read("src/router.js"),
  ledger: read("src/views/studio/LedgerListView.vue"),
  formStudio: read("src/views/studio/FormStudioView.vue"),
  todos: read("src/views/studio/TodoInboxView.vue"),
  doc: read("../docs/g15-h5-pkce-browser.md"),
  appWebView: read("src/mobile/products/p3-studio/pages/AppWebViewHarnessPage.vue"),
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

requireIncludes("login", files.login, [
  "mobile-pkce-login",
  "createS256",
  "savePendingPkce",
  'pkce: "s256"',
  "code_challenge",
]);
requireIncludes("callback", files.callback, ["consumePendingPkce", "code_verifier", "/m/callback"]);
requireIncludes("client", files.client, ["code_challenge", "pkce"]);
requireIncludes("router", files.router, ["session.accountId", "session.contextId"]);
requireIncludes("doc", files.doc, [
  "Authorization Code + PKCE",
  "does not close product G15",
  "APP WebView",
  "WeChat",
  "Playwright",
  "loopback HTTPS",
  "AuditPort",
]);
forbidIncludes("doc", files.doc, ["closes product G15", "product G15 is closed", "native APP accepted"]);
forbidIncludes("desktop ledger", files.ledger, ["mobile-pkce-login", "createS256", "NxMobileShell"]);
forbidIncludes("desktop form studio", files.formStudio, ["mobile-pkce-login", "/m/callback"]);
forbidIncludes("desktop todos", files.todos, ["mobile-pkce-login", "savePendingPkce"]);
requireIncludes("APP WebView harness leftover", files.appWebView, ["NexusAppWebView", "非原生"]);

if (authorizeUrlHasSecret("/m/callback?code=x&code_verifier=leak")) {
  failures.push("authorize URL with verifier must be rejected");
}
if (authorizeUrlHasSecret("/m/callback?code=x&state=s")) {
  failures.push("callback without secrets must be allowed");
}

const rfcVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
const rfcChallenge = await challengeS256(rfcVerifier);
if (rfcChallenge !== "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM") {
  failures.push(`RFC 7636 vector mismatch: ${rfcChallenge}`);
}

if (failures.length) {
  console.error("FAIL G15 H5 PKCE leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: H5 /m/* Authorization Code + PKCE wired; desktop P3 untouched; product G15 / native APP / WeChat leftover");
