#!/usr/bin/env node
/**
 * G7 leftover: Vue todo-withdraw / browser evidence honesty.
 * Does not redo #103/#113/#140/#159. Does not claim product G7 close.
 * Both leftover rows in docs/README.md must stay.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  BOTH_LEFTOVER_ROWS,
  REASONS,
  RESULTS,
  describeWithdrawBrowserHonesty,
  evaluateLiveJarIdpWithdraw,
} from "../src/api/g7WithdrawBrowserHonesty.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  honesty: read("src/api/g7WithdrawBrowserHonesty.js"),
  test: read("src/api/g7WithdrawBrowserHonesty.test.js"),
  pkg: read("package.json"),
  docsIndex: read("../docs/README.md"),
  doc: read("../docs/g7-withdraw-browser-honesty.md"),
  browser: read("../docs/g7-withdraw-browser.md"),
  query: read("../docs/g7-query-expense-travel.md"),
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

requireIncludes("honesty module", files.honesty, [
  "g7-withdraw-browser-honesty",
  "#113/#159",
  "#140",
  "#103",
  "g7Close: \"NOT_CLAIMED\"",
  "closed_unchanged",
  "live_jar_not_healthy",
  "stub_login_is_not_live_walk",
]);
requireIncludes("honesty test", files.test, [
  "BOTH_LEFTOVER_ROWS",
  "fake-live-pass.env",
  "fake-g7-close.env",
  "honest-skip.env",
]);
requireIncludes("package.json", files.pkg, [
  "test:g7-withdraw-browser-honesty",
  "assert:g7-withdraw-browser-honesty",
]);
requireIncludes("docs/README leftover rows", files.docsIndex, [
  "g7-withdraw-browser",
  "g7-query-expense-travel",
  "g7-withdraw-browser-honesty",
]);
requireIncludes("honesty doc", files.doc, [
  "G18 stays closed",
  "honest SKIP",
  "NOT_CLAIMED",
  "#103",
  "#113",
  "#140",
  "#159",
  "Keep both leftover rows",
]);
requireIncludes("browser leftover pointer", files.browser, ["g7-withdraw-browser-honesty.md"]);
requireIncludes("query leftover pointer", files.query, ["g7-withdraw-browser-honesty.md"]);
requireIncludes("claim list", files.claim, [
  "g7-withdraw-browser.md",
  "g7-query-expense-travel.md",
  "g7-withdraw-browser-honesty.md",
  "不宣称产品 G7 关闭",
]);
requireIncludes("root README", files.readme, ["g7-withdraw-browser-honesty.md"]);
forbidIncludes("honesty doc", files.doc, ["product G7 closed", "closes G7", "reopen G18"]);
forbidIncludes("claim list", files.claim, ["产品 G7 已关闭", "完整 G7 关闭"]);

const bridge = describeWithdrawBrowserHonesty();
if (bridge.g7Close !== "NOT_CLAIMED" || bridge.g18 !== "closed_unchanged") {
  failures.push("bridge must not claim G7 or reopen G18");
}
if (bridge.redo103 || bridge.redo113 || bridge.redo140 || bridge.redo159) {
  failures.push("bridge must not redo #103/#113/#140/#159");
}
if (
  !BOTH_LEFTOVER_ROWS.every((row) => bridge.keepBothLeftoverRows.includes(row))
) {
  failures.push("bridge must keep both leftover rows");
}

const skipped = evaluateLiveJarIdpWithdraw();
if (skipped.result !== RESULTS.SKIP || skipped.reason !== REASONS.LIVE_JAR_UNHEALTHY) {
  failures.push("default live walk must SKIP when JAR/IdP are unavailable");
}
if (skipped.g7Close !== "NOT_CLAIMED") {
  failures.push("SKIP must not claim product G7 close");
}

if (failures.length) {
  console.error("FAIL G7 withdraw browser leftover honesty");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log(
  "PASS: G7 withdraw-browser leftover honesty; both leftover rows kept; product G7 not claimed",
);
