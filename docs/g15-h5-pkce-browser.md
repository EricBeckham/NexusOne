# G15 leftover — H5 mobile-browser Authorization Code + PKCE

Work package: **human PKCE login walk on `/m/*`**.  
Baseline: Java 21, Spring Boot **4.1.1**, Node 22, system Chrome. No Docker. Desktop ledger / form / todo pages are unchanged. **G18 stays closed.** `AuditPort` is unchanged.

Builds on G4 HTTP-driver PKCE ([g4-pkce-login-audit.md](./g4-pkce-login-audit.md), #108) and the loopback HTTPS leftover ([g4-pkce-https-browser.md](./g4-pkce-https-browser.md), #120). This does **not** close product G15. APP WebView **web** harness is already on main ([g15-app-webview-host.md](./g15-app-webview-host.md), #114) and is not duplicated. Simulated 小程序 web-view is [g15-miniprogram-webview.md](./g15-miniprogram-webview.md) (#117). Native APP / WeChat / DingTalk production stay open. The production-host **stub** is [g15-wx-dt-prod-host.md](./g15-wx-dt-prod-host.md).

## What this leftover covers

| Surface | Behavior |
|---|---|
| Browser-held S256 | `web/src/mobile/auth/pkce.js` mints `code_verifier` in the page. Verifier stays in `sessionStorage` (`nexus.g15.pending-pkce`). Never in the URL. |
| Authorize | `/m/login` `data-testid=mobile-pkce-login` calls `GET /api/v1/auth/authorize-url?pkce=s256&code_challenge=…`. When the client supplies the challenge, the API does **not** return `code_verifier` (G4 BFF mint without `code_challenge` is unchanged). |
| Callback | `/m/callback` POSTs `{ code, redirect_uri, code_verifier }` to `/api/v1/auth/login`, then `router.replace` strips query. Token / tenant_id stay out of the address bar. |
| Session | Mobile guard accepts issued `accountId` / `contextId` so a confidential client does not have to write an Access Token into the SPA. |
| Playwright / stub | `./scripts/g15/run-h5-pkce-browser.sh` walks the hop against a PKCE-validating stub so Secure-cookie Keycloak is not required for this leftover. Wrong verifier is `400 P2.IAM.PKCE`. |
| Loopback HTTPS | Documented below. Reuses #120 certs / native Keycloak HTTPS so a laptop browser can honor `Secure` cookies. Not claimed as a live walk in this package unless the optional script is run. |

Stub login (`data-testid=mobile-stub-login`) and the confidential authorize button stay for F011 / M0-04. Product default authorize URL is still confidential-client **without** PKCE.

## Honest leftovers (not claimed)

| Item | Status |
|---|---|
| Product G15 | **Open.** This is one leftover walk on the mobile browser. |
| APP WebView native binary | **Open.** Web harness is #114. Not re-walked here. |
| WeChat / DingTalk production | **Open.** Simulated 小程序 parent is #117. Production-host stub is [g15-wx-dt-prod-host.md](./g15-wx-dt-prod-host.md). |
| Live Keycloak human laptop click | **Optional.** Needs #120 CA trust (or the insecure-origin flag). This leftover’s required evidence is Playwright + stub. G4 hash-chain LOGIN remains the HTTP/2 driver on main. |
| SPA vs BFF on desktop | Desktop `/login` is still stub token. Not changed. |
| `GET /api/v1/auth/callback` | Still does not carry the verifier. H5 uses POST `/login`. |
| G4 product close / 90-day hot purge | **Open.** `AuditPort` not touched. |
| G18 / P0 / M0 | Closed or out of scope. Not redone. |

## How to run

```bash
# Always-green Java slice (no Keycloak)
mvn -pl nexus-p2-iam -am test \
  -Dtest=PkceTest,AuthPkceApiTest,SessionPkceLoginAuditTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# H5 wiring + Playwright stub walk (system Chrome, no Docker)
./scripts/g15/run-h5-pkce-browser.sh
```

Equivalents:

```bash
cd web
npm install
npm test -- src/mobile/auth/pkce.test.js src/mobile/auth/pkceSession.test.js src/mobile/products/p3-studio/pages/h5-pkce-login.browser.test.js
npm run assert:h5-pkce
npm run assert:h5
```

If Playwright or Chrome is missing, the walk writes `result=SKIP` and exits 0. Wiring asserts still run.

## Documented loopback HTTPS (Secure cookies)

Keycloak 26 sets `AUTH_SESSION_ID` / `KC_RESTART` as `Secure; SameSite=None` even on `http://127.0.0.1`. A real browser drops those cookies. Prefer the #120 leftover:

```bash
./scripts/g4-pkce-https-certs.sh
# source /tmp/g4-https.env
# nexus-p2-iam/scripts/start-native-keycloak-https.sh
# Trust .local/g4-https/server.crt in the laptop browser
# Point NEXUS_KEYCLOAK_CLIENT_ID=nexus-web
# Redirect URI http(s)://127.0.0.1:5173/m/callback is in nexus-realm.json
```

Insecure-origin fallback (not the primary path): Chromium
`--unsafely-treat-insecure-origin-as-secure=http://127.0.0.1:8180`.

This package does **not** claim that live Keycloak H5 click as evidence.

## Browser steps (stub)

1. Open `/m/p3/files` logged out → `/m/login?return=/m/p3/files`.
2. Click **PKCE 授权登录** (`data-testid=mobile-pkce-login`).
3. Browser holds the verifier; authorize-url echoes the challenge only.
4. Stub hops `code` / `state` to `/m/callback` (no verifier in the query).
5. POST `/api/v1/auth/login` with `code_verifier`. Lands `/m/p3/files`.
6. `/p3/ledgers` stays the desktop M0-12 page.

## Evidence

Recorded on this agent VM after `./scripts/g15/run-h5-pkce-browser.sh`. Artifact: [walk.json](./g15-h5-pkce-browser/walk.json).

| Item | Value |
|---|---|
| JDK | OpenJDK **21** |
| Maven | **3.8.x / 3.9.x** as on the agent |
| Spring Boot | **4.1.1** |
| Node | **22** |
| Browser | system Chrome + `playwright-core` (no Docker, no bundled Chromium download) |
| `AuditPort` | **untouched** |
| Docker | no |

| Suite | Result |
|---|---|
| `PkceTest` / `AuthPkceApiTest` | pending-this-run — client-held challenge, BFF mint unchanged |
| Vitest H5 PKCE | pending-this-run |
| `npm run assert:h5-pkce` | pending-this-run |
| Playwright stub walk | pending-this-run |
| live Keycloak HTTPS H5 click | **not claimed** |

Product G15 remains **open**.
