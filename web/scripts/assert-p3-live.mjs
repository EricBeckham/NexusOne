#!/usr/bin/env node
/**
 * Guard: P3 UI talks to live M0-06/07/08/G18 HTTP APIs. MOCK adapter is gone.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const files = {
  facade: readFileSync(join(root, "src/api/p3.js"), "utf8"),
  live: readFileSync(join(root, "src/api/p3-live.js"), "utf8"),
  client: readFileSync(join(root, "src/api/client.js"), "utf8"),
  router: readFileSync(join(root, "src/router.js"), "utf8"),
  shell: readFileSync(join(root, "src/layout/AppShell.vue"), "utf8"),
  ledgerList: readFileSync(join(root, "src/views/studio/LedgerListView.vue"), "utf8"),
  formList: readFileSync(join(root, "src/views/studio/FormListView.vue"), "utf8"),
  formStudio: readFileSync(join(root, "src/views/studio/FormStudioView.vue"), "utf8"),
  todos: readFileSync(join(root, "src/views/studio/TodoInboxView.vue"), "utf8"),
  webReadme: readFileSync(join(root, "README.md"), "utf8"),
  pkg: readFileSync(join(root, "package.json"), "utf8"),
};

const failures = [];

if (existsSync(join(root, "src/api/mocks/p3-mock.js"))) {
  failures.push("src/api/mocks/p3-mock.js must be removed");
}
if (files.facade.includes("p3-mock") || files.facade.includes("useP3Mock") || files.facade.includes("VITE_NEXUS_P3_USE_MOCK")) {
  failures.push("p3.js must not import MOCK or honor VITE_NEXUS_P3_USE_MOCK");
}
if (!files.live.includes("/api/v1/p3/ledgers") || !files.live.includes("/api/v1/p3/forms") || !files.live.includes("/api/v1/p3/todos")) {
  failures.push("p3-live.js must call the three P3 live list routes");
}
if (!files.live.includes("/api/v1/p3/applications") || !files.live.includes("/ledgers")) {
  failures.push("p3-live.js must create ledgers via /api/v1/p3/applications/{id}/ledgers");
}
if (!files.live.includes("/api/v1/p3/path/submit-start")) {
  failures.push("p3-live.js must call /api/v1/p3/path/submit-start");
}
if (!files.live.includes("/api/v1/p3/path/tasks/") || !files.live.includes("/approve")) {
  failures.push("p3-live.js must call path approve/reject");
}
if (!files.client.includes("/api/v1/permission")) {
  failures.push("client.js must call /api/v1/permission");
}
if (!files.client.includes("/api/v1/notifications") || !files.client.includes("/api/v1/audit/traces/")) {
  failures.push("client.js must call notifications and audit-by-trace");
}
if (/headers\[["']X-Tenant-Id["']\]\s*=/.test(files.live + files.facade + files.client)
    || /["']X-Tenant-Id["']\s*:/.test(files.live + files.facade + files.client)) {
  failures.push("P3 adapters must not send X-Tenant-Id");
}
if (!files.ledgerList.includes("createLedger") || !files.ledgerList.includes("ledger-create")) {
  failures.push("LedgerListView must expose create-ledger UI");
}
if (!files.formList.includes("createForm") || !files.formStudio.includes("validateForm") || !files.formStudio.includes("publishForm")) {
  failures.push("form views must expose create / validate / publish");
}
if (!files.formStudio.includes("submitStart") || files.formStudio.includes("不写物理台账")) {
  failures.push("form fill must use submit-start and must not claim form_submission-only");
}
if (!files.todos.includes("approveTodo") || !files.todos.includes("todo-approve")) {
  failures.push("TodoInboxView must expose approve");
}
if (!files.router.includes("/p3/notifications") || !files.router.includes("/p3/audit")) {
  failures.push("router must expose notification and audit pages");
}
if (!files.shell.includes("/p3/notifications") || !files.shell.includes("/p3/audit")) {
  failures.push("AppShell sidebar must link notifications and audit");
}
if (files.pkg.includes("assert:p3-mock")) {
  failures.push("package.json must retire assert:p3-mock");
}
if (files.webReadme.includes("src/api/mocks") || files.webReadme.includes("VITE_NEXUS_P3_USE_MOCK=true")) {
  failures.push("web README must not document MOCK as the default");
}
if (!files.webReadme.includes("G18") || files.webReadme.includes("G18 已关闭") || files.webReadme.includes("closes G18")) {
  failures.push("web README must mention G18 without claiming it closed");
}

if (failures.length) {
  console.error("FAIL P3 live wiring");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: P3 UI is live-only (create ledger/form, submit-start, path todos, notify, audit) and never X-Tenant-Id");
