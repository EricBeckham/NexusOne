#!/usr/bin/env node
/**
 * G7 leftover: Vue/desktop todo-withdraw reaches the HTTP withdraw evidence path.
 * Does not claim product G7 close. Does not reopen G18. Does not rewrite AuditPort.
 * Sibling leftover rows in docs/README.md must stay.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  view: read("src/views/studio/TodoInboxView.vue"),
  live: read("src/api/p3-live.js"),
  client: read("src/api/client.js"),
  facade: read("src/api/p3.js"),
  vueTest: read("src/views/studio/todo-withdraw.browser.test.js"),
  httpTest: read("src/api/p3-withdraw-http-reachability.test.js"),
  pkg: read("package.json"),
  docsIndex: read("../docs/README.md"),
  doc: read("../docs/g7-withdraw-browser.md"),
  claim: read("../docs/planning/团队领取任务清单_V1.1.md"),
  liveWalk: read("../docs/g7-withdraw-browser/live-walk.json"),
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

requireIncludes("TodoInboxView", files.view, [
  'data-testid="todo-withdraw"',
  "withdrawTodo",
  "instance_id",
]);
requireIncludes("p3-live withdraw", files.live, [
  "/api/v1/p3/path/instances/${instanceId}/withdraw",
]);
requireIncludes("client isolation", files.client, [
  "X-Nexus-Context-Id",
  "x-tenant-id",
]);
requireIncludes("p3 facade", files.facade, ["withdrawTodo: (...args) => live.withdrawTodo(...args)"]);
requireIncludes("vue test", files.vueTest, [
  "请假审批",
  "报销审批",
  "出差审批",
  "todo-withdraw",
  "leave",
  "expense",
  "travel",
]);
requireIncludes("http reachability test", files.httpTest, [
  "/api/v1/p3/path/instances/",
  "/withdraw",
  "X-Nexus-Context-Id",
  "leave",
  "expense",
  "travel",
  "P3.FLOW.NO_INSTANCE",
]);
requireIncludes("package.json", files.pkg, [
  "test:g7-withdraw",
  "assert:g7-withdraw-browser",
  "p3-withdraw-http-reachability.test.js",
]);
requireIncludes("docs/README leftover rows", files.docsIndex, [
  "g7-withdraw-browser",
  "g9-sentinel-cluster-honesty",
  "g4-export-wizard",
  "g4-p1-staging-audit-events",
  "g4-pkce-spa-verifier",
  "g15-native-bridge-stub",
  "g17-admin-mfa-recovery-email",
  "g2-ops-pg-dump",
]);
requireIncludes("doc", files.doc, [
  "POST /api/v1/p3/path/instances/{id}/withdraw",
  "Not claimed",
  "G18 stays closed",
  "live product-JAR",
  "honest SKIP",
]);
requireIncludes("claim list", files.claim, ["g7-withdraw-browser.md", "不宣称产品 G7 关闭"]);
forbidIncludes("doc", files.doc, ["product G7 closed", "closes G7", "reopen G18"]);
forbidIncludes("claim list", files.claim, ["产品 G7 已关闭", "完整 G7 关闭"]);

const liveWalk = JSON.parse(files.liveWalk);
if (liveWalk.result === "PASS" && liveWalk.live_jar !== true) {
  failures.push("live-walk.json must not PASS without live_jar=true");
}
if (liveWalk.g7_product_close === true) {
  failures.push("live-walk.json must not claim product G7 close");
}

if (failures.length) {
  console.error("FAIL G7 withdraw browser leftover");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log(
  "PASS: Vue todo-withdraw reaches HTTP withdraw path; sibling leftover rows kept; product G7 leftover",
);
