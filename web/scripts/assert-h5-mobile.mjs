#!/usr/bin/env node
/**
 * G15 H5: mobile login-redirect / file / routing + F014 host-message wiring.
 * Does not claim Keycloak PKCE, a native APP WebView binary, or WeChat/DingTalk production.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  DEFAULT_MOBILE_HOME,
  isSafeMobileReturn,
  safeMobileReturn,
} from "../src/mobile/auth/safeReturn.js";
import { FILE_ALLOWED_MIME, FILE_MAX_SIZE_BYTES, inferMime, validateUploadFile } from "../src/mobile/file/constraints.js";
import { HOST_CHANNELS, hostCapabilities, postHost } from "../src/mobile/host/hostBridge.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  router: read("src/router.js"),
  app: read("src/App.vue"),
  client: read("src/api/client.js"),
  filesApi: read("src/api/files.js"),
  login: read("src/mobile/products/p3-studio/pages/LoginPage.vue"),
  callback: read("src/mobile/products/p3-studio/pages/CallbackPage.vue"),
  home: read("src/mobile/products/p3-studio/pages/HomePage.vue"),
  filesPage: read("src/mobile/products/p3-studio/pages/FilesPage.vue"),
  fileDetail: read("src/mobile/products/p3-studio/pages/FileDetailPage.vue"),
  hostPage: read("src/mobile/products/p3-studio/pages/HostMessagePage.vue"),
  harness: read("src/mobile/products/p3-studio/pages/HostHarnessPage.vue"),
  mpHarness: read("src/mobile/products/p3-studio/pages/MiniProgramWebViewHarnessPage.vue"),
  appWebViewHarness: read("src/mobile/products/p3-studio/pages/AppWebViewHarnessPage.vue"),
  shell: read("src/mobile/layouts/NxMobileShell.vue"),
  hostBridge: read("src/mobile/host/hostBridge.js"),
  ledger: read("src/views/studio/LedgerListView.vue"),
  ledgerDetail: read("src/views/studio/LedgerDetailView.vue"),
  formList: read("src/views/studio/FormListView.vue"),
  formStudio: read("src/views/studio/FormStudioView.vue"),
  todos: read("src/views/studio/TodoInboxView.vue"),
  notify: read("src/views/studio/NotifyInboxView.vue"),
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
  'path: "/m/login"',
  'path: "/m/callback"',
  'path: "/m/p3/files"',
  'path: "/m/p3/files/:id"',
  'path: "/m/p3/home"',
  'path: "/m/p3/forms"',
  'path: "/m/p3/lists"',
  'path: "/m/p3/todos"',
  'path: "/m/p3/query"',
  'path: "/m/p3/notifications"',
  'path: "/m/host"',
  'path: "/m/host-harness"',
  'path: "/m/mp-webview-harness"',
  'path: "/m/app-webview-harness"',
  "beforeEach",
  'path: "/m/login"',
  'query: { return: returnTo }',
  'path: "/p3/ledgers"',
  'path: "/p3/forms"',
  'path: "/p3/todos"',
  'postHost("route"',
]);
requireIncludes("App.vue", files.app, ["NxMobileShell", "route.meta.mobile", "route.meta.harness", "AppShell"]);
requireIncludes("files API", files.filesApi, ["/api/v1/files", "signed-url", "ARCH-024"]);
requireIncludes("login page", files.login, ["safeMobileReturn", "mobile-stub-login", "authorize-url", 'postHost("login"']);
requireIncludes("callback", files.callback, ["consumePendingRedirect", "/m/callback"]);
requireIncludes("files page", files.filesPage, [
  "validateUploadFile",
  "files.upload",
  "AbortController",
  "file-demo-oversize",
  "file-demo-mime",
  'postHost("file"',
]);
requireIncludes("file detail", files.fileDetail, ["signDownload", 'postHost("file"']);
requireIncludes("shell", files.shell, ["mobile-back", "history.length", "useHostPort", 'postHost("close"']);
requireIncludes("host page", files.hostPage, ["host-out-login", "host-out-route", "host-out-file", "host-out-close"]);
requireIncludes("harness", files.harness, ["host-iframe", "nexus-host", "/m/host?embedded=true"]);
requireIncludes("mp harness", files.mpHarness, ["mp-webview", "mp-capsule", "MP_WEBVIEW_CHILD_SRC"]);
requireIncludes("app WebView harness", files.appWebViewHarness, ["NexusAppWebView", "app-webview-send-login", "非原生"]);
requireIncludes("hostBridge", files.hostBridge, ["HostBridgePort", "postMessage", "nexus-h5", "nexus-host"]);

forbidIncludes("hostBridge", files.hostBridge, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("host page", files.hostPage, ["isWeChat", "isDingTalk"]);
forbidIncludes("mp harness", files.mpHarness, ["isWeChat", "isDingTalk", "wx.miniProgram"]);
forbidIncludes("home", files.home, ["未在本切片验收"]);

for (const [name, text] of Object.entries({
  ledger: files.ledger,
  ledgerDetail: files.ledgerDetail,
  formList: files.formList,
  formStudio: files.formStudio,
  todos: files.todos,
  notify: files.notify,
})) {
  forbidIncludes(`desktop ${name}`, text, ["/m/p3", "NxMobileShell", "mobile-stub-login", "hostBridge"]);
}

if (/headers\[["']X-Tenant-Id["']\]\s*=/.test(files.filesApi + files.client)
    || /["']X-Tenant-Id["']\s*:/.test(files.filesApi + files.client)) {
  failures.push("file client must not send X-Tenant-Id");
}

if (isSafeMobileReturn("https://evil.example/m/p3/home")) {
  failures.push("off-site return must be rejected");
}
if (isSafeMobileReturn("//evil.example/m/p3/home")) {
  failures.push("protocol-relative return must be rejected");
}
if (isSafeMobileReturn("/m/p3/files?access_token=abc")) {
  failures.push("token in return must be rejected");
}
if (isSafeMobileReturn("/m/p3/home?tenant_id=1")) {
  failures.push("tenant_id in return must be rejected");
}
if (isSafeMobileReturn("/p3/ledgers")) {
  failures.push("desktop path is not an H5 return");
}
if (safeMobileReturn("/m/p3/files?tab=recent") !== "/m/p3/files?tab=recent") {
  failures.push("deep-link query return must be kept");
}
if (safeMobileReturn("/evil", DEFAULT_MOBILE_HOME) !== DEFAULT_MOBILE_HOME) {
  failures.push("unsafe return must fall back to mobile home");
}

const huge = { name: "big.bin", size: FILE_MAX_SIZE_BYTES + 1, type: "text/plain" };
if (validateUploadFile(huge).ok) {
  failures.push("oversize file must fail client validation");
}
const badType = { name: "x.exe", size: 12, type: "application/x-msdownload" };
if (validateUploadFile(badType).ok) {
  failures.push("disallowed MIME must fail client validation");
}
const okFile = { name: "note.txt", size: 12, type: "text/plain" };
if (!validateUploadFile(okFile).ok || inferMime(okFile) !== "text/plain") {
  failures.push("text/plain must be allowed");
}
if (!FILE_ALLOWED_MIME.includes("application/pdf")) {
  failures.push("pdf must stay on the allow list");
}

if (HOST_CHANNELS.join(",") !== "login,route,file,close") {
  failures.push("host bridge must only expose login/route/file/close");
}
const caps = hostCapabilities();
if (caps.accepted !== true) {
  failures.push("must accept F014 postMessage contract on mobile browser");
}
if (!caps.otherHostsOpen?.includes("APP WebView")) {
  failures.push("must keep APP WebView listed as leftover");
}
if (!caps.otherHostsOpen?.includes("WeChat/DingTalk 小程序 production")) {
  failures.push("must keep WeChat/DingTalk production listed as leftover");
}
if (postHost("eval", {}).ok) {
  failures.push("unknown host channel must be rejected");
}
if (postHost("login", { access_token: "x" }).ok) {
  failures.push("host login must reject access_token");
}

if (failures.length) {
  console.error("FAIL G15 H5 mobile wiring");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: H5 mobile login-redirect / file / routing / F014 host-message wired; desktop P3 pages untouched; APP WebView / WeChat/DingTalk leftover");
