# G15 leftover — APP WebView native-bridge contract stub

Work package: a **native-bridge-shaped harness** for the leftover APP WebView package.  
Same HostBridgePort envelope as #114 / #117 / #132 (`login` / `route` / `file` / `close`).  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker. Desktop ledger / form / todo pages are unchanged. G18 stays closed and is not redone.

**This does not close product G15.** It is **not** a store-ready IPA/APK, not an Xcode / Gradle project, not WKWebView / Android WebView product wiring, and **not** WeChat / DingTalk production. The F014 envelope on main is reused, not rewritten.

## What this leftover is (and is not)

| Surface | Route | Behavior |
|---|---|---|
| Native-bridge stub parent | `/m/native-bridge-harness` | iOS WK / Android JS chrome wrapping `/m/host?embedded=true&host=native-bridge-stub`. Banner states this is a stub, not a store package. |
| iOS handler shape | `webkit.messageHandlers.nexusHost.postMessage(envelope)` | WKScriptMessageHandler name only. No WKWebView, no `.xcodeproj`, no TestFlight / App Store. |
| Android handler shape | `NexusNativeBridge.postMessage(json)` | JavascriptInterface name only. No `addJavascriptInterface`, no Gradle / APK / Play. |
| Same F014 port | `web/src/mobile/host/hostBridge.js` | No second bridge. H5 still encodes `{ source, channel, requestId, payload }`. Tokens / `tenant_id` / `eval` / off-site paths stay rejected. |
| Evidence assert | `npm run assert:h5-native-bridge` | Fake-window walk of both platform shapes, four channels both ways. No Playwright. No native toolchain. |

`describeNativeBridgeContract().storeReady` / `storePackage` / `nativeBinary` / `productionAccepted` / `iosProject` / `androidProject` are always `false`.

## Not claimed vs web simulations already on main

| Existing leftover | What it is | What this stub is not |
|---|---|---|
| #114 [g15-app-webview-host.md](./g15-app-webview-host.md) | Web injection of `window.NexusAppWebView` | Not that web object. Not a native binary either. |
| #117 [g15-miniprogram-webview.md](./g15-miniprogram-webview.md) | Simulated 小程序 web-view parent | Not a mini-program. Not production. |
| #132 [g15-wx-dt-prod-host.md](./g15-wx-dt-prod-host.md) | WeChat / DingTalk production-host **stub** | Not vendor JSAPI. Production still **not** accepted. |

Envelope (unchanged from [g15-h5-mobile.md](./g15-h5-mobile.md)):

```text
{
  source: "nexus-h5" | "nexus-host",
  channel: "login" | "route" | "file" | "close",
  requestId: "h5-…",
  payload: { action, path?, returnTo?, fileId?, fileName? }
}
```

Business pages still do not branch on WeChat / DingTalk (H5 规范 §80.2). Host differences stay in `web/src/mobile/host/`.

`hostCapabilities().otherHostsOpen` stays `APP WebView` / `WeChat/DingTalk 小程序 production`.

## Honest leftovers (not claimed)

| Item | Status |
|---|---|
| APP WebView **store-ready native package** | **Open.** No IPA/APK, no store listing, no WKWebView / Android WebView product host. This file is a **stub**. Honesty leftover is [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (PASS/SKIP; not IPA/APK accept). |
| #114 web `NexusAppWebView` harness | Already on main. Not re-claimed as native. |
| #117 simulated 小程序 web-view | Already on main. Not production. |
| WeChat / DingTalk production | **Not accepted.** Contract stub is [g15-wx-dt-prod-host.md](./g15-wx-dt-prod-host.md). |
| Keycloak Authorization Code + PKCE | Mobile-browser leftover is [g15-h5-pkce-browser.md](./g15-h5-pkce-browser.md). Not walked inside this stub. |
| Product G15 | **Open** until a store-ready native host (and production 小程序) walk the same envelope. Honesty leftover does **not** close it. |
| G18 / P0 / M0 | Closed or out of scope. Not redone. Desktop P3 is not rewritten. |

## How to run

```bash
cd web
npm install
npm test
npm run assert:h5
npm run assert:h5-host
npm run assert:h5-mp-webview
npm run assert:h5-app-webview
npm run assert:h5-wx-dt
npm run assert:h5-native-bridge
npm run assert:h5-runtime
npm run assert:isolation
npm run assert:p3-live
npm run build
npm run dev
```

Open `http://127.0.0.1:5173/m/native-bridge-harness`. Switch iOS WK 桩 / Android JS 桩. Use 入站 login/route/file/close and 入站拒绝样例. The page is a **stub**. It is not an APP.

## Checks

`npm test` — HostBridgePort units plus native-bridge stub units (iOS / Android handler shapes, same envelope, origin / channel / src allowlist).

`npm run assert:h5-native-bridge` — fake-window protocol under both platform shapes: handshake host `native-bridge-stub`, four channels both ways, no `NexusAppWebView` / `window.parent` leak, vendor / evil origins rejected, `eval` / `access_token` rejected, harness + claim-list leftover row keep the store-ready leftover, desktop P3 views do not import the mobile shell. No Docker, no Playwright, no Xcode / Gradle.

## Evidence

Recorded on this agent VM after rebase onto `origin/main` `057268b` (`#156` G4 P1 staging audit-events leftover). Node / Vitest from `web/package.json`. No Docker. No Playwright. No Xcode / Android SDK. No JAR / PostgreSQL required for this contract. G18 stays closed. AuditPort / TenantContext / desktop P3 untouched. F014 envelope not rewritten. `docs/README.md` keeps both leftover rows `g4-export-wizard` (#152) and `g4-p1-staging-audit-events` (#156).

```bash
cd web
npm test
npm run assert:h5
npm run assert:h5-host
npm run assert:h5-mp-webview
npm run assert:h5-app-webview
npm run assert:h5-wx-dt
npm run assert:h5-native-bridge
npm run assert:h5-runtime
npm run assert:isolation
npm run assert:p3-live
npm run build
```

```text
Rebase onto origin/main 057268b (#156 G4 P1 staging audit-events leftover).
Recorded 2026-09-22T19:04:00Z  Node 22.14.0  Vitest 3.2.7
vitest run src/mobile/host  42 passed (42)
  hostBridge 18  wechatDingTalkHost 7  nativeBridgeStub 6
  miniprogramWebView 6  appWebViewHost 5
assert:h5                PASS  desktop P3 untouched; APP WebView / WeChat leftover
assert:h5-host           PASS  mobile-browser F014 leftover
assert:h5-mp-webview     PASS  #117 sim leftover
assert:h5-app-webview    PASS  #114 web harness leftover
assert:h5-wx-dt          PASS  #132 production stub leftover
assert:h5-native-bridge  PASS  iOS WK / Android JS stub; not store-ready; #114/#117/#132 leftover
assert:h5-runtime        PASS
assert:isolation         PASS
assert:p3-live           PASS
vite build               119 modules, NativeBridgeHarnessPage emitted
docs/README.md keeps g4-export-wizard (#152) and g4-p1-staging-audit-events (#156)
```

Browser walk of `/m/native-bridge-harness` is recorded after this assert. Product G15 remains **open**. Store-ready APP WebView native package remains **not claimed**. WeChat / DingTalk production remains **not accepted**.
