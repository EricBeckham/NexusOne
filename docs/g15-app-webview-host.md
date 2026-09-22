# G15 leftover — APP WebView host contract (web harness)

Work package: **web-only** HostBridgePort walk for the APP WebView leftover.  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker. Desktop ledger / form / todo pages are unchanged. G18 stays closed and is not redone.

**This does not close product G15.** 需求规格 V1.1 §12 still requires a real APP WebView host (and WeChat / DingTalk production 小程序) on the same contract. A simulated 小程序 web-view parent is already on main ([g15-miniprogram-webview.md](./g15-miniprogram-webview.md)). This slice records that the existing F014 envelope works when a web page **simulates** the APP WebView `postMessage` channels. It does **not** claim an iOS / Android binary, WKWebView, Android WebView, React Native WebView, or any store package.

## What this leftover walk covers

| Surface | Route / API | Behavior |
|---|---|---|
| Simulated APP host | `/m/app-webview-harness` | Web chrome around `/m/host?embedded=true`. Injects `window.NexusAppWebView` and sends `login` / `route` / `file` / `close` with `source: "nexus-host"`. Banner states this is not a native package. |
| Same F014 port | `web/src/mobile/host/hostBridge.js` | No second bridge. H5 still encodes `{ source, channel, requestId, payload }`. Tokens / `tenant_id` / `eval` / off-site paths stay rejected. |
| Top-level WebView target | `window.NexusAppWebView.postMessage` | When the injected object exists, `resolvePostTarget` prefers it over `window.parent`. That is the APP WebView shape (`parent === window`). |
| Evidence assert | `npm run assert:h5-app-webview` | Fake-window walk of all four channels both ways. Proves the APP sim envelope matches the mobile-browser iframe envelope. No Playwright. No Docker. |

Business pages still do not branch on WeChat / DingTalk (H5 规范 §80.2). Host differences stay in `HostBridgePort`.

Envelope (unchanged from [g15-h5-mobile.md](./g15-h5-mobile.md)):

```text
{
  source: "nexus-h5" | "nexus-host",
  channel: "login" | "route" | "file" | "close",
  requestId: "h5-…",
  payload: { action, path?, returnTo?, fileId?, fileName? }
}
```

| Channel | H5 → simulated APP | Simulated APP → H5 |
|---|---|---|
| login | `ready` / `request` / `done` — never a token | `request` / `session_expired` → `/m/login` |
| route | `changed` + safe `/m/…` path | `navigate` + safe `/m/…` path |
| file | `upload` / `download` / `open` + `fileId` — never bytes or signed URL | `open` + `fileId` → `/m/p3/files/:id` |
| close | `close` | `close` → `history.back` or home |

## Honest leftovers (not claimed)

| Item | Status |
|---|---|
| APP WebView native binary | **Open.** No iOS / Android project, no WKWebView / Android WebView wiring, no store package. |
| 小程序 web-view sim | Simulated parent is [g15-miniprogram-webview.md](./g15-miniprogram-webview.md). |
| WeChat / DingTalk production | **Open.** |
| Keycloak Authorization Code + PKCE | **Open.** Browser session is still the M0-12 stub token. |
| Product G15 | **Open** until a real native host (and 小程序) walk the same envelope. |
| G18 / P0 / M0 | Closed or out of scope. Not redone. Desktop P3 is not rewritten. |

`hostCapabilities().otherHostsOpen` is `APP WebView` / `WeChat/DingTalk 小程序 production`. `nativeBinary` is always `false` in this slice.

## How to run

```bash
cd web
npm install
npm test
npm run assert:h5
npm run assert:h5-host
npm run assert:h5-app-webview
npm run build
npm run dev
```

Open `http://127.0.0.1:5173/m/app-webview-harness`. The page is a web simulation. It is not an APP.

## Checks

`npm test` — existing HostBridgePort units plus APP WebView injection / same-envelope tests.

`npm run assert:h5-app-webview` — top-level `NexusAppWebView` handshake `login/ready`; four channels both ways; APP sim envelope equals iframe envelope; origin / `eval` / `access_token` rejected; harness + doc keep the native leftover; desktop P3 views untouched.

## Evidence

Recorded on this agent VM. Node from `npm test` (Vitest). No Docker. No Playwright. No native toolchain. No JAR / PostgreSQL required for this contract.

```bash
cd web
npm test
npm run assert:h5
npm run assert:h5-host
npm run assert:h5-app-webview
```

```text
Recorded 2026-09-22T17:35:24Z  Node 22.14.0  Vitest 3.2.7  Vite 6.4.3
vitest run  28 passed (28)  hostBridge 15  appWebViewHost 5
assert:h5              PASS  desktop P3 untouched; native APP leftover
assert:h5-host         PASS  mobile-browser F014; native APP leftover
assert:h5-app-webview  PASS  same envelope login/route/file/close; native APP leftover
assert:h5-runtime      PASS
assert:isolation       PASS
assert:p3-live         PASS
vite build             95 modules, AppWebViewHarnessPage emitted
```

Browser walk of `/m/app-webview-harness` is recorded after the first push. Product G15 remains **open**.
