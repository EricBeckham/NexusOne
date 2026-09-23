/**
 * Honesty inventory twin of Java H5PageSurfaceCatalog.
 * Covered pages already call useH5Surface (#116). Open pages stay leftover.
 * Not a product-wide G2 close. Does not redo #116/#150/#144/#137.
 */
export const ENGINE = "PermissionEngine";
export const G2_CLOSE = "NOT_CLAIMED";

export const COVERED_PAGES = Object.freeze([
  Object.freeze({ path: "/m/p3/forms", vue: "FormsPage.vue", cell: "form/read" }),
  Object.freeze({ path: "/m/p3/forms/:id", vue: "FormFillPage.vue", cell: "form/read + form/write" }),
  Object.freeze({ path: "/m/p3/lists", vue: "ListsPage.vue", cell: "ledger/read" }),
  Object.freeze({ path: "/m/p3/lists/:id", vue: "ListRecordsPage.vue", cell: "ledger/read" }),
  Object.freeze({
    path: "/m/p3/lists/:id/records/:recordId",
    vue: "RecordDetailPage.vue",
    cell: "ledger/read",
  }),
  Object.freeze({ path: "/m/p3/todos", vue: "TodosPage.vue", cell: "process.task/read" }),
  Object.freeze({
    path: "/m/p3/todos/:id",
    vue: "TodoDetailPage.vue",
    cell: "process.task/read + approve/reject + process.instance/withdraw",
  }),
  Object.freeze({ path: "/m/p3/query", vue: "QueryPage.vue", cell: "ledger/read" }),
]);

export const OPEN_PAGES = Object.freeze([
  Object.freeze({ path: "/m", vue: "", reason: "redirect only" }),
  Object.freeze({ path: "/m/login", vue: "LoginPage.vue", reason: "public auth" }),
  Object.freeze({ path: "/m/callback", vue: "CallbackPage.vue", reason: "public auth" }),
  Object.freeze({ path: "/m/host", vue: "HostMessagePage.vue", reason: "F014 host" }),
  Object.freeze({ path: "/m/host-harness", vue: "HostHarnessPage.vue", reason: "harness" }),
  Object.freeze({ path: "/m/mp-webview-harness", vue: "MiniProgramWebViewHarnessPage.vue", reason: "harness" }),
  Object.freeze({ path: "/m/app-webview-harness", vue: "AppWebViewHarnessPage.vue", reason: "harness" }),
  Object.freeze({ path: "/m/wx-dt-harness", vue: "WeChatDingTalkHarnessPage.vue", reason: "harness" }),
  Object.freeze({ path: "/m/native-bridge-harness", vue: "NativeBridgeHarnessPage.vue", reason: "harness" }),
  Object.freeze({ path: "/m/wx-dt-prod-gap-harness", vue: "WeChatDingTalkProdGapHonestyPage.vue", reason: "honesty harness" }),
  Object.freeze({ path: "/m/native-bridge-honesty-harness", vue: "NativeBridgeHonestyPage.vue", reason: "honesty harness" }),
  Object.freeze({ path: "/m/p3/home", vue: "HomePage.vue", reason: "session home" }),
  Object.freeze({ path: "/m/p3/files", vue: "FilesPage.vue", reason: "file service" }),
  Object.freeze({ path: "/m/p3/files/:id", vue: "FileDetailPage.vue", reason: "file detail" }),
  Object.freeze({ path: "/m/p3/notifications", vue: "NotifyPage.vue", reason: "notify jump" }),
  Object.freeze({ path: "/m/p4/market", vue: "MarketPage.vue", reason: "P4 catalog shell; permission cell pending" }),
]);

export function coveredPaths() {
  return COVERED_PAGES.map((page) => page.path);
}

export function openPaths() {
  return OPEN_PAGES.map((page) => page.path);
}
