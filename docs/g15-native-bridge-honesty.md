# G15 leftover — APP WebView native-bridge honesty beyond #151

S0+S1 remainder after #114 APP WebView **web** harness, #117 simulated 小程序 web-view, #132 WeChat/DingTalk production-host **stub**, #151 APP WebView native-bridge **stub**, and #167 WeChat/DingTalk production-gap honesty. Rebased onto `origin/main` `476ceaa` (Merge #177 G4 Keycloak SPA PKCE verifier honesty leftover after #180). **This does not close product G15.** It does **not** close product G15 native-bridge. It extends honest **PASS/SKIP**. It is **not** IPA/APK production accept.

Keep **all sibling leftover rows** in [docs/README.md](./README.md): [g15-native-bridge-stub](./g15-native-bridge-stub.md) (#151), [g15-wx-dt-prod-gap-honesty](./g15-wx-dt-prod-gap-honesty.md) (#167), [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md) (#180), [s0-secret-rotate-without-restart](./s0-secret-rotate-without-restart.md), [g4-pkce-spa-verifier](./g4-pkce-spa-verifier.md), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md) (#177), [g7-withdraw-browser](./g7-withdraw-browser.md), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md), and [g7-query-expense-travel](./g7-query-expense-travel.md). Do not drop this file. Follow-on honest PASS/SKIP leftover (does **not** redo this leftover): [g15-wx-dt-native-bridge-honesty.md](./g15-wx-dt-native-bridge-honesty.md). Remainder leftover beyond #198 (does **not** redo this leftover): [g15-wx-dt-native-bridge-remainder.md](./g15-wx-dt-native-bridge-remainder.md).

Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker. Desktop ledger / form / todo pages are unchanged. G18 stays closed. P0 / M0 are not redone. F014 envelope on main is reused, not rewritten. Do **not** redo #114 / #117 / #132 / #151 / #167.

## What this leftover is (and is not)

| Surface | Route / artifact | Behavior |
|---|---|---|
| Honesty contract | `web/src/mobile/host/nativeBridgeHonesty.js` | Same HostBridgePort channels as #151. `storeReady` / `nativeBinary` / `productionAccepted` stay `false`. `g15Close=NOT_CLAIMED`. `stubContract=PASS` (the #151 shape still holds). IPA/APK = **SKIP**. |
| Honesty inventory page | `/m/native-bridge-honesty-harness` | Gap list + origin/channel reject probes. Banner states **不是上架 IPA/APK**. Not a second native-bridge chrome. Does **not** redo `/m/native-bridge-harness`. |
| Honesty gate | `scripts/g15-native-bridge-honesty.sh --verify-report` | Rejects `g15_close=CLOSED` and `ios_ipa_production` / `android_apk_production` / `store_ready=PASS` without an observed native walk. |

`describeNativeBridgeHonestyContract().iosIpaProduction` / `androidApkProduction` / `storeReadyStatus` are always `SKIP`.

## Not claimed vs leftovers already on main

| Existing leftover | What it is | What this leftover is not |
|---|---|---|
| #114 [g15-app-webview-host.md](./g15-app-webview-host.md) | Web injection of `window.NexusAppWebView` | Not that web object. Not a native binary either. |
| #117 [g15-miniprogram-webview.md](./g15-miniprogram-webview.md) | Simulated 小程序 web-view parent | Not a mini-program. Not production. |
| #132 [g15-wx-dt-prod-host.md](./g15-wx-dt-prod-host.md) | WeChat / DingTalk production-host **stub** | Not vendor JSAPI. Production still **not** accepted. |
| #151 [g15-native-bridge-stub.md](./g15-native-bridge-stub.md) | iOS WK / Android JS native-bridge stub | Not store-ready. This leftover does **not** redo `/m/native-bridge-harness`. |
| #167 [g15-wx-dt-prod-gap-honesty.md](./g15-wx-dt-prod-gap-honesty.md) | WeChat / DingTalk production-gap honesty | Not WeChat / DingTalk production. Row kept. |
| #171 [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) | Browser failed-publish honesty leftover | Orthogonal. Sibling leftover row kept. |
| #180 [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) | SecretPort OS getenv snapshot honesty leftover | Orthogonal. Sibling leftover rows `s0-secret-getenv-snapshot-honesty` / `s0-secret-rotate-without-restart` kept. |
| #177 [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) | Live Keycloak CA / SPA PKCE verifier honesty leftover | Orthogonal. Sibling leftover rows `g4-pkce-spa-verifier` / `g4-pkce-spa-verifier-honesty` kept. |
| #175 [g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) | Vue `todo-withdraw` live JAR/IdP honesty | Orthogonal. Rows `g7-withdraw-browser` / `g7-query-expense-travel` kept. |

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

## Honest leftovers (not claimed)

| Item | Status |
|---|---|
| #151 stub contract (iOS WK / Android JS shapes, same F014 envelope) | **PASS.** Shape leftover already on main. Honesty leftover re-asserts it. |
| iOS store-ready IPA / TestFlight / App Store | **SKIP.** No Xcode, no WKWebView product host, no IPA walk. |
| Android store-ready APK / Play | **SKIP.** No Gradle, no `addJavascriptInterface` product host, no APK walk. |
| WeChat / DingTalk production | **Not accepted.** Honesty leftover is [g15-wx-dt-prod-gap-honesty.md](./g15-wx-dt-prod-gap-honesty.md). |
| Product G15 | **Open.** Honesty gate rejects `g15_close=CLOSED`. |
| G18 / P0 / M0 | Closed or out of scope. Not redone. |
| Desktop P3 | Untouched. |

## How to run

```bash
./scripts/g15-native-bridge-honesty.sh
./scripts/g15-native-bridge-honesty.sh --verify-report \
  scripts/g15-native-bridge-honesty/fixtures/honest-leftover.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g15-native-bridge-honesty.sh --verify-report \
  scripts/g15-native-bridge-honesty/fixtures/fake-g15-close.env fail
./scripts/g15-native-bridge-honesty.sh --verify-report \
  scripts/g15-native-bridge-honesty/fixtures/fake-ipa-apk-pass.env fail
```

```bash
cd web
npm test -- src/mobile/host/nativeBridgeHonesty.test.js
npm run assert:h5-native-bridge-honesty
```

Open `http://127.0.0.1:5173/m/native-bridge-honesty-harness`. The page is a **honesty leftover**. It is not an APP. It is not an IPA/APK.

See [engineering/g15-native-bridge-honesty-EVIDENCE.md](./engineering/g15-native-bridge-honesty-EVIDENCE.md).
