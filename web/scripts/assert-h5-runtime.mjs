#!/usr/bin/env node
/**
 * G15 F010: H5 runtime pages (forms / lists / todos / query / notify jumps).
 * Does not claim APP WebView, 小程序, or Keycloak PKCE.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { DEFAULT_MOBILE_HOME } from "../src/mobile/auth/safeReturn.js";
import { HOST_CHANNELS } from "../src/mobile/host/hostBridge.js";
import { resolveNotifyJump } from "../src/mobile/runtime/notifyJump.js";
import { buildRecordQuery } from "../src/mobile/runtime/recordQuery.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  router: read("src/router.js"),
  live: read("src/api/p3-live.js"),
  facade: read("src/api/p3.js"),
  client: read("src/api/client.js"),
  shell: read("src/mobile/layouts/NxMobileShell.vue"),
  home: read("src/mobile/products/p3-studio/pages/HomePage.vue"),
  forms: read("src/mobile/products/p3-studio/pages/FormsPage.vue"),
  fill: read("src/mobile/products/p3-studio/pages/FormFillPage.vue"),
  lists: read("src/mobile/products/p3-studio/pages/ListsPage.vue"),
  listRecords: read("src/mobile/products/p3-studio/pages/ListRecordsPage.vue"),
  record: read("src/mobile/products/p3-studio/pages/RecordDetailPage.vue"),
  todos: read("src/mobile/products/p3-studio/pages/TodosPage.vue"),
  todoDetail: read("src/mobile/products/p3-studio/pages/TodoDetailPage.vue"),
  query: read("src/mobile/products/p3-studio/pages/QueryPage.vue"),
  notify: read("src/mobile/products/p3-studio/pages/NotifyPage.vue"),
  hostBridge: read("src/mobile/host/hostBridge.js"),
  ledger: read("src/views/studio/LedgerListView.vue"),
  ledgerDetail: read("src/views/studio/LedgerDetailView.vue"),
  formList: read("src/views/studio/FormListView.vue"),
  formStudio: read("src/views/studio/FormStudioView.vue"),
  desktopTodos: read("src/views/studio/TodoInboxView.vue"),
  desktopNotify: read("src/views/studio/NotifyInboxView.vue"),
  doc: read("../docs/g15-f010-h5-runtime.md"),
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

requireIncludes("router", files.router, [
  'path: "/m/p3/forms"',
  'path: "/m/p3/forms/:id"',
  'path: "/m/p3/lists"',
  'path: "/m/p3/lists/:id"',
  'path: "/m/p3/lists/:id/records/:recordId"',
  'path: "/m/p3/todos"',
  'path: "/m/p3/todos/:id"',
  'path: "/m/p3/query"',
  'path: "/m/p3/notifications"',
  'path: "/p3/ledgers"',
  'path: "/p3/forms"',
  'path: "/p3/todos"',
]);

requireIncludes("p3 facade", files.facade, ["getRecord", "listRecords", "submitStart", "listTodos"]);
requireIncludes("p3-live", files.live, [
  "recordListQuery",
  "/api/v1/p3/ledgers/${ledgerId}/records",
  "getRecord",
  "X-Nexus-Context-Id",
]);
requireIncludes("shell", files.shell, ['to="/m/p3/forms"', 'to="/m/p3/todos"', 'to="/m/p3/notifications"']);
requireIncludes("home", files.home, [
  'to="/m/p3/forms"',
  'to="/m/p3/lists"',
  'to="/m/p3/todos?box=inbox"',
  'to="/m/p3/query"',
  'to="/m/p3/notifications"',
]);
requireIncludes("forms", files.forms, ["listForms", "/m/p3/forms/"]);
requireIncludes("fill", files.fill, ["submitStart", "running_fields", "nexusSequentialApproval"]);
requireIncludes("lists", files.lists, ["listLedgers", "/m/p3/lists/"]);
requireIncludes("list records", files.listRecords, ["listRecords", "listFields"]);
requireIncludes("record", files.record, ["getRecord"]);
requireIncludes("todos", files.todos, ["listTodos", 'box: next']);
requireIncludes("todo detail", files.todoDetail, ["approveTodo", "rejectTodo", "withdrawTodo"]);
requireIncludes("query", files.query, ["buildRecordQuery", "listRecords", "m-query-run"]);
requireIncludes("notify", files.notify, ["resolveNotifyJump", "notify.list", "m-notify-demo-todo"]);
requireIncludes("doc", files.doc, ["F010", "APP WebView", "PKCE", "X-Nexus-Context-Id"]);

forbidIncludes("fill", files.fill, ["validateForm", "publishForm", "designer", "NxMobileShell"]);
forbidIncludes("lists", files.lists, ["provisionBusinessType", "createLedger", "addField"]);
forbidIncludes("query", files.query, ["X-Tenant-Id", "tenant_id="]);
forbidIncludes("hostBridge", files.hostBridge, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("doc", files.doc, ["closes product G15", "PKCE accepted", "APP WebView accepted"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  desktopTodos: files.desktopTodos,
  desktopNotify: files.desktopNotify,
})) {
  forbidIncludes(`desktop ${name}`, text, ["/m/p3", "NxMobileShell", "notifyJump", "hostBridge"]);
}

if (!files.live.includes("getRecord") || !files.facade.includes("getRecord")) {
  failures.push("getRecord must be on the live P3 facade");
}

if (/headers\[["']X-Tenant-Id["']\]\s*=/.test(files.live + files.facade + files.client)
    || /["']X-Tenant-Id["']\s*:/.test(files.live + files.facade + files.client)) {
  failures.push("runtime client must not send X-Tenant-Id");
}

if (HOST_CHANNELS.join(",") !== "login,route,file,close") {
  failures.push("must reuse F014 HostBridgePort; do not add a second bridge");
}

if (resolveNotifyJump({ template_code: "approval.task.assigned" }) !== "/m/p3/todos?box=inbox") {
  failures.push("assigned notify must jump to inbox");
}
if (resolveNotifyJump({ jump: "/p3/todos" }) !== DEFAULT_MOBILE_HOME) {
  failures.push("desktop notify jump must be discarded");
}
if (resolveNotifyJump({ jump: "/m/p3/todos?access_token=x" }) !== DEFAULT_MOBILE_HOME) {
  failures.push("token notify jump must be discarded");
}
if (JSON.stringify(buildRecordQuery({ tenant_id: "x", title: "ok" })) !== JSON.stringify({ title: "ok" })) {
  failures.push("query builder must drop tenant_id");
}

if (failures.length) {
  console.error("FAIL G15 F010 H5 runtime");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: F010 H5 runtime forms/lists/todos/query/notify jumps wired; desktop P3 untouched; APP WebView / 小程序 / PKCE leftover");
