#!/usr/bin/env node
/**
 * G2 leftover: H5 /m/* list/detail/form/todo on the same PermissionEngine.
 * Does not claim product-wide G2, report UI, or AuditPort changes.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { DENY_CODES, H5_SURFACES } from "../src/mobile/permission/surfaceCatalog.js";
import { denyCode, requireAllowed } from "../src/mobile/permission/surfaceGuard.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  catalog: read("src/mobile/permission/surfaceCatalog.js"),
  guard: read("src/mobile/permission/surfaceGuard.js"),
  hook: read("src/mobile/permission/useH5Surface.js"),
  lists: read("src/mobile/products/p3-studio/pages/ListsPage.vue"),
  listRecords: read("src/mobile/products/p3-studio/pages/ListRecordsPage.vue"),
  record: read("src/mobile/products/p3-studio/pages/RecordDetailPage.vue"),
  query: read("src/mobile/products/p3-studio/pages/QueryPage.vue"),
  forms: read("src/mobile/products/p3-studio/pages/FormsPage.vue"),
  fill: read("src/mobile/products/p3-studio/pages/FormFillPage.vue"),
  todos: read("src/mobile/products/p3-studio/pages/TodosPage.vue"),
  todoDetail: read("src/mobile/products/p3-studio/pages/TodoDetailPage.vue"),
  javaCatalog: read("../nexus-permission-core/src/main/java/one/nexus/permission/surface/H5SurfaceCatalog.java"),
  javaGuard: read("../nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/H5SurfaceGuard.java"),
  ledger: read("src/views/studio/LedgerListView.vue"),
  ledgerDetail: read("src/views/studio/LedgerDetailView.vue"),
  formList: read("src/views/studio/FormListView.vue"),
  formStudio: read("src/views/studio/FormStudioView.vue"),
  desktopTodos: read("src/views/studio/TodoInboxView.vue"),
  doc: read("../docs/g2-h5-permission.md"),
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

requireIncludes("JS catalog", files.catalog, [
  'resource: "ledger"',
  'action: "read"',
  'resource: "form"',
  'action: "write"',
  'resource: "process.task"',
  'resource: "process.instance"',
  "P2.PERM.0003",
  "P2.PERM.0002",
  "P2.PERM.0001",
  "PermissionEngine",
]);
requireIncludes("JS guard", files.guard, ["requireAllowed", "ENGINE", "no_grant"]);
requireIncludes("hook", files.hook, ["perm.check", "requireCell"]);
requireIncludes("lists", files.lists, ["H5_SURFACES.listRead", "useH5Surface", "listLedgers"]);
requireIncludes("list records", files.listRecords, ["H5_SURFACES.listRead", "listRecords"]);
requireIncludes("record", files.record, ["H5_SURFACES.listRead", "getRecord"]);
requireIncludes("query", files.query, ["H5_SURFACES.listRead", "listRecords"]);
requireIncludes("forms", files.forms, ["H5_SURFACES.formRead", "listForms"]);
requireIncludes("fill", files.fill, ["H5_SURFACES.formRead", "H5_SURFACES.formWrite", "submitStart"]);
requireIncludes("todos", files.todos, ["H5_SURFACES.todoRead", "listTodos"]);
requireIncludes("todo detail", files.todoDetail, [
  "H5_SURFACES.todoRead",
  "H5_SURFACES.todoApprove",
  "H5_SURFACES.todoReject",
  "H5_SURFACES.todoWithdraw",
  "approveTodo",
]);
requireIncludes("Java catalog", files.javaCatalog, [
  'LIST_RESOURCE = "ledger"',
  'FORM_RESOURCE = "form"',
  'TODO_RESOURCE = "process.task"',
  'TODO_READ = "read"',
  'INSTANCE_WITHDRAW = "withdraw"',
]);
requireIncludes("Java H5SurfaceGuard", files.javaGuard, [
  "SurfaceGuard.require",
  "requireListRead",
  "requireFormWrite",
  "requireTodoRead",
  "requireTodoApprove",
]);
requireIncludes("doc", files.doc, [
  "H5",
  "SurfaceGuard",
  "no_grant",
  "explicit_deny",
  "tenant_boundary",
  "P2.PERM.0003",
  "G18 stays closed",
  "not a product-wide G2",
]);

forbidIncludes("fill", files.fill, ["validateForm", "publishForm", "NxMobileShell"]);
forbidIncludes("lists", files.lists, ["provisionBusinessType", "createLedger", "addField"]);
forbidIncludes("doc", files.doc, ["closes product G2", "AuditPort rewrite", "reopen G18"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  desktopTodos: files.desktopTodos,
})) {
  forbidIncludes(`desktop ${name}`, text, [
    "H5SurfaceGuard",
    "useH5Surface",
    "H5_SURFACES",
    "/m/p3",
    "NxMobileShell",
  ]);
}

if (H5_SURFACES.listRead.resource !== "ledger" || H5_SURFACES.formWrite.action !== "write") {
  failures.push("H5 catalog must reuse desktop ledger/form cells");
}
if (denyCode("no_grant") !== "P2.PERM.0003" || DENY_CODES.explicit_deny !== "P2.PERM.0002") {
  failures.push("deny codes must match desktop PermissionException");
}
try {
  requireAllowed({ allowed: false, reason: "tenant_boundary" });
  failures.push("tenant_boundary must deny");
} catch (err) {
  if (err.code !== "P2.PERM.0001") {
    failures.push(`tenant_boundary must be P2.PERM.0001, got ${err.code}`);
  }
}

if (failures.length) {
  console.error("FAIL G2 H5 permission");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: H5 /m/* list/detail/form/todo on SurfaceGuard; desktop P3 untouched; product G2 leftover");
