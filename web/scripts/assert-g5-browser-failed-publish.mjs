#!/usr/bin/env node
/**
 * G5 leftover: browser failed-publish click / PublishHook honesty.
 * Does not claim product G5 close. Does not reopen G18.
 * Sibling leftover rows in docs/README.md must stay.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  view: read("src/views/studio/FormStudioView.vue"),
  live: read("src/api/p3-live.js"),
  pkg: read("package.json"),
  docsIndex: read("../docs/README.md"),
  doc: read("../docs/g5-browser-failed-publish.md"),
  mid: read("../docs/g5-mid-publish-failure.md"),
  claim: read("../docs/planning/团队领取任务清单_V1.1.md"),
  walk: read("../docs/g5-browser-failed-publish/walk.json"),
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

requireIncludes("FormStudioView", files.view, [
  'data-testid="form-publish"',
  'data-testid="form-publish-log"',
  "simulatePublishFailure",
]);
requireIncludes("p3-live publish", files.live, [
  "/api/v1/p3/forms/${id}/publish",
  "/api/v1/p3/forms/${id}/publish-log",
  "UI 不再模拟失败",
  "rolled_back: false",
]);
requireIncludes("package.json", files.pkg, ["assert:g5-browser-failed-publish"]);
requireIncludes("docs/README leftover rows", files.docsIndex, [
  "g5-mid-publish-failure",
  "g5-browser-failed-publish",
  "g9-live-multi-jvm-replica",
  "g6-recovery-port-honesty",
  "g6-recovery-port-dry-run",
  "g2-h5-every-page",
  "linux-arm-profile-honesty",
  "g10-file-reconcile-honesty",
  "g4-export-wizard-deny",
  "g15-wx-dt-prod-gap-honesty",
  "g3-claim-crash-window",
  "g1-datascope-or-in-ast",
  "g17-mfa-recovery-email-honesty",
  "g9-redis-down-invalidate-honesty",
  "g2-form-write-honesty",
  "g7-withdraw-browser-honesty",
]);
requireIncludes("doc", files.doc, [
  "SchemaServicePublishHook",
  "does **not** claim product G5 close",
  "honest SKIP",
  "Keep both leftover rows",
  "G18 stays closed",
]);
requireIncludes("mid leftover", files.mid, ["g5-browser-failed-publish.md"]);
requireIncludes("claim list", files.claim, [
  "g5-browser-failed-publish.md",
  "g5-mid-publish-failure.md",
  "g7-withdraw-browser-honesty.md",
  "不要宣称产品 G5 关闭",
]);
forbidIncludes("doc", files.doc, ["product G5 closed", "closes G5", "reopen G18"]);
forbidIncludes("claim list", files.claim, ["产品 G5 已关闭"]);

const walk = JSON.parse(files.walk);
if (walk.result === "PASS" && walk.live_jar !== true) {
  failures.push("walk.json must not PASS without live_jar=true");
}
if (walk.g5_product_close === true) {
  failures.push("walk.json must not claim product G5 close");
}
if (walk.simulate_publish_log_is_fail_click === true) {
  failures.push("walk.json must not treat publish-log as a fail-click");
}

if (failures.length) {
  console.error("FAIL G5 browser failed-publish honesty leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log(
  "PASS: G5 browser failed-publish honesty; both leftover rows kept; product G5 leftover",
);
