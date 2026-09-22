# G15 leftover — WeChat / DingTalk production host contract stub

Work package: a **production-host-shaped harness** for WeChat and DingTalk.  
Same HostBridgePort channels as the mobile-browser iframe host and the #117 / #114 leftovers (`login` / `route` / `file` / `close`).  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker. Desktop ledger / form / todo pages are unchanged. G18 stays closed and is not redone.

**This does not close product G15.** It does **not** accept WeChat or DingTalk production. Vendor origins stay denied. No vendor JSAPI is loaded. APP WebView native stays open. Mobile-browser PKCE leftover is [g15-h5-pkce-browser.md](./g15-h5-pkce-browser.md). Orthogonal to open PRs and to the desktop G4 PKCE click leftover (#125).

## What this slice adds

| Surface | Route | Behavior |
|---|---|---|
| Production-host stub parent | `/m/wx-dt-harness` | Vendor chrome (WeChat / DingTalk switch) wrapping a web-view-shaped iframe. Parent posts `nexus-host` envelopes through HostBridgePort. Banner states this is a stub, not production accept. |
| Embedded H5 | `/m/host?embedded=true&host=wechat-prod-stub` or `host=dingtalk-prod-stub` | Same HostMessage page. `hostCapabilities().host` is `wechat-prod-stub` / `dingtalk-prod-stub`. Handshake is `login/ready`. |
| Origin allowlist | parent + HostBridgePort | Only the H5 page origin. `https://servicewechat.com`, `https://open.weixin.qq.com`, `https://h5.dingtalk.com`, `https://n.dingtalk.com`, `https://aflow.dingtalk.com`, and `https://evil.example` are `origin_rejected`. |
| Channel allowlist | parent + HostBridgePort | Only `login` / `route` / `file` / `close`. `eval` and tokens / `tenant_id` stay rejected. |
| web-view src | `isAllowedVendorHostSrc` | Same-origin `/m/*` only. Off-site, token-bearing, and harness paths are denied. |

F014 is not forked. Business pages still do not branch on WeChat / DingTalk (H5 规范 §80.2). Host differences stay in `web/src/mobile/host/`.

Envelope (unchanged):

```text
{
  source: "nexus-h5" | "nexus-host",
  channel: "login" | "route" | "file" | "close",
  requestId: "h5-…",
  payload: { action, path?, returnTo?, fileId?, fileName? }
}
```

`hostCapabilities().productionAccepted` is always `false`. `describeVendorProdContract().vendors.*.productionAccepted` is `false` for both vendors. `otherHostsOpen` stays `APP WebView` / `WeChat/DingTalk 小程序 production`.

## Honest leftovers (not claimed)

| Item | Status |
|---|---|
| WeChat 小程序 production accept | **Not accepted.** No vendor JSAPI, no servicewechat.com allow, no WeChat domain walk. This file is a **stub**. |
| DingTalk 小程序 production accept | **Not accepted.** No DingTalk JSAPI, no h5.dingtalk.com allow. This file is a **stub**. |
| APP WebView native binary | **Open.** Web harness is [g15-app-webview-host.md](./g15-app-webview-host.md). Native-bridge **stub** is [g15-native-bridge-stub.md](./g15-native-bridge-stub.md) (not store-ready). |
| 小程序 web-view sim | Simulated parent is [g15-miniprogram-webview.md](./g15-miniprogram-webview.md). |
| Keycloak Authorization Code + PKCE | Mobile-browser leftover is [g15-h5-pkce-browser.md](./g15-h5-pkce-browser.md). Desktop leftover is [g4-pkce-browser-click.md](./g4-pkce-browser-click.md). Not walked inside this stub. |
| Product G15 | **Open** until production hosts are walked on the same contract. Production-gap honesty stub is [g15-wx-dt-prod-gap-honesty.md](./g15-wx-dt-prod-gap-honesty.md). |
| G18 / P0 / M0 | Closed or out of scope. Not redone. |
| Desktop P3 | Untouched. |

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
npm run assert:h5-runtime
npm run assert:isolation
npm run assert:p3-live
npm run build
npm run dev
```

Open `http://127.0.0.1:5173/m/wx-dt-harness`. The phone chrome is a **stub**. Switch 微信桩 / 钉钉桩. Use 入站 login/route/file/close and 入站拒绝样例.

## Checks

`npm test` — HostBridgePort units plus vendor production-stub units (origin / channel / src allowlist, both vendors, handshake).

`npm run assert:h5-wx-dt` — fake-window protocol under `host=wechat-prod-stub` and `host=dingtalk-prod-stub`: handshake `login/ready`, four channels both ways, vendor / evil origins rejected, `eval` / `access_token` rejected, harness wiring, desktop P3 views do not import the mobile shell. No Docker, no Playwright.

## Evidence

Recorded on this agent VM after rebase onto `origin/main` `47d04c7` (#125 desktop PKCE click leftover). Node / Vitest from `web/package.json`. No Docker. No Playwright. No WeChat / DingTalk SDK. No JAR / PostgreSQL required for this contract. G18 stays closed. AuditPort / TenantContext / desktop P3 untouched.

```bash
cd web
npm test
npm run assert:h5
npm run assert:h5-host
npm run assert:h5-mp-webview
npm run assert:h5-app-webview
npm run assert:h5-wx-dt
npm run assert:h5-runtime
npm run assert:isolation
npm run assert:p3-live
npm run build
```

```text
(evidence filled after npm run)
```

Browser walk of `/m/wx-dt-harness` is recorded after this assert. Product G15 remains **open**. WeChat / DingTalk production remains **not accepted**.
