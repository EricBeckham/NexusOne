# G15 leftover — simulated 小程序 web-view host contract

Work package: a **mini-program-shaped harness** that acts as a simulated web-view parent.  
Same HostBridgePort channels as the mobile-browser iframe host (`login` / `route` / `file` / `close`).  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker. Desktop ledger / form / todo pages are unchanged. G18 stays closed and is not redone.

**This does not close product G15.** It does **not** accept WeChat or DingTalk production 小程序. APP WebView stays open. Keycloak Authorization Code + PKCE stays open.

## What this slice adds

| Surface | Route | Behavior |
|---|---|---|
| Simulated parent | `/m/mp-webview-harness` | Mini-program chrome (status / nav / capsule) wrapping a `<web-view>`-shaped iframe. Parent posts `nexus-host` envelopes through HostBridgePort. |
| Embedded H5 | `/m/host?embedded=true&host=mp-webview` | Same HostMessage page. `hostCapabilities().host` is `miniprogram-webview-sim`. Handshake is `login/ready`. |
| Origin allowlist | parent + HostBridgePort | Only the H5 page origin. `https://servicewechat.com`, `https://open.weixin.qq.com`, `https://h5.dingtalk.com`, and `https://evil.example` are `origin_rejected`. |
| Channel allowlist | parent + HostBridgePort | Only `login` / `route` / `file` / `close`. `eval` and tokens / `tenant_id` stay rejected. |
| web-view src | `isAllowedWebViewSrc` | Same-origin `/m/*` only. Off-site, token-bearing, and harness paths are denied. |

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

## Honest leftovers (not claimed)

| Item | Status |
|---|---|
| WeChat 小程序 production | **Not accepted.** No `wx.miniProgram`, no servicewechat.com allow, no WeChat domain walk. |
| DingTalk 小程序 production | **Not accepted.** No DingTalk JSAPI, no h5.dingtalk.com allow. |
| APP WebView | **Not accepted.** Same contract, no native host walk. |
| Keycloak Authorization Code + PKCE | **Not accepted.** Browser session is still the stub token. |
| Product G15 | **Open** until production hosts are walked on the same contract. |
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
npm run assert:h5-runtime
npm run assert:isolation
npm run assert:p3-live
npm run build
npm run dev
```

Open `http://127.0.0.1:5173/m/mp-webview-harness`. The phone chrome is a **simulation**. Use 入站 login/route/file/close and 入站拒绝样例.

## Checks

`npm test` — HostBridgePort units plus simulated web-view parent units (origin / channel / src allowlist, handshake).

`npm run assert:h5-mp-webview` — fake-window protocol under `host=mp-webview`: handshake `login/ready`, four channels both ways, WeChat / DingTalk / evil origins rejected, `eval` / `access_token` rejected, harness wiring, desktop P3 views do not import the mobile shell. No Docker, no Playwright.

## Evidence

Recorded on this agent VM after the change (Node / Vitest versions filled from the test run). No Docker. No WeChat / DingTalk SDK.

```bash
cd web
npm test
npm run assert:h5-mp-webview
```
