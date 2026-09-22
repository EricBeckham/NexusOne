# G15 H5 — mobile login redirect, files, routing, host messages

Work package: **mobile** pages. First acceptance host is a **mobile browser**.  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker. Desktop ledger / form / todo pages are not part of this change.

**This does not close product G15.** 需求规格 V1.1 §12 still requires other hosts (APP WebView / 小程序 web-view) on the same contract. Those stay open. P0 / M0 / G18 are not redone.

## What is closed on the mobile browser

| Need | Route | Behavior |
|---|---|---|
| F011 login redirect | `/m/login?return=…` → `/m/callback` | Unauthenticated `/m/*` is sent to login with a **safe** Return URL. Stub login (same `stub.` token as M0-12) returns to that path. Token / `tenant_id` / off-site returns are dropped. |
| F012 files | `/m/p3/files`, `/m/p3/files/:id` | Client size (20 MB) and MIME checks, then `POST /api/v1/files`. Download is `POST .../signed-url` after metadata. Progress + cancel. Never MinIO admin APIs, never `X-Tenant-Id`. |
| F013 routing | `/m/p3/home`, files + query `tab`, file deep link | History mode. Refresh keeps `sessionStorage` session. Tenant stays in the issued context, not the URL. Back uses `history.back`, else mobile home. |
| F014 host messages | `/m/host`, `/m/host-harness` | HostBridgePort exposes only `login` / `route` / `file` / `close` over same-origin `postMessage` (`nexus-h5` ↔ `nexus-host`). Standalone mobile browser uses the web fallback. The iframe parent at `/m/host-harness` is the mobile-browser acceptance host. Tokens / `tenant_id` / unknown channels / off-site routes are rejected. |
| F010 runtime | `/m/p3/forms`, `/m/p3/lists`, `/m/p3/todos`, `/m/p3/query`, `/m/p3/notifications` | Published-app H5 runtime. Details and leftovers: [g15-f010-h5-runtime.md](./g15-f010-h5-runtime.md). |

Desktop `/p3/ledgers`, `/p3/forms`, `/p3/todos` (and their views) are unchanged.

Issued TenantContext for File Service uses the existing `IssuedTenantIsolationFilter` binder (now also `/api/v1/files`). Demo file headers stay test-only.

## F014 contract (accepted on mobile browser)

Envelope:

```text
{
  source: "nexus-h5" | "nexus-host",
  channel: "login" | "route" | "file" | "close",
  requestId: "h5-…",
  payload: { action, path?, returnTo?, fileId?, fileName? }
}
```

`window.parent.postMessage(envelope, location.origin)` when `parent !== window` (or `?embedded=true`).  
Host → H5 uses the same envelope with `source: "nexus-host"`.

| Channel | Outbound (H5 → host) | Inbound (host → H5) | Web fallback |
|---|---|---|---|
| login | `ready` / `request` / `done` — never a token | `request` / `session_expired` → `/m/login` | `/m/login?return=…` |
| route | `changed` + safe `/m/…` path | `navigate` + safe `/m/…` path | `router.replace` |
| file | `upload` / `download` / `open` + `fileId` — never bytes or signed URL | `open` + `fileId` → `/m/p3/files/:id` | same routes |
| close | `close` (embedded back / 关闭) | `close` → `history.back` or home | same |

Business pages do not branch on WeChat / DingTalk (H5 规范 §80.2). All host differences stay in `web/src/mobile/host/hostBridge.js`.

## Honest gaps (not claimed)

| Item | Status |
|---|---|
| APP WebView | **Not accepted.** Same contract, no native host walk. |
| 小程序 web-view | **Not accepted.** Same contract, no mini-program walk. |
| Keycloak Authorization Code + PKCE | **Not accepted.** `/m/callback` lands code/state and POSTs `/api/v1/auth/login`; the confidential client does not write Access Token into the SPA or URL. Browser session is still stub token, same as M0-12. |
| F010 full H5 runtime | Mobile-browser pages now in [g15-f010-h5-runtime.md](./g15-f010-h5-runtime.md). Other hosts / PKCE still open. |
| Live S3 / MinIO download | Product default `nexus.files.storage=memory`. Signed URL against real S3 is M0-10, not re-run here. |
| G18 / P0 / M0 | Closed or out of scope. Not redone. |
| Product G15 | **Open** until other hosts are walked on the same contract. |

## How to run

```bash
cd web
npm install
npm test
npm run assert:h5
npm run assert:h5-host
npm run assert:isolation
npm run assert:p3-live
npm run build
npm run dev
```

Open a mobile-width browser at `http://127.0.0.1:5173/m/host` (standalone web fallback) or `http://127.0.0.1:5173/m/host-harness` (iframe parent).  
`/m/p3/files` still redirects to `/m/login?return=…` when logged out.

Live upload needs the JAR + issued `X-Nexus-Context-Id` (tenant membership). Missing context is a **403**, not a fixture list.

## Checks

`npm test` — Vitest HostBridgePort: channel allowlist, origin, secrets, inbound apply, handshake.

`npm run assert:h5` — safe return URL, MIME/size, host-channel denylist, `/m` + `/m/host` routes, desktop P3 views do not import the mobile shell.

`npm run assert:h5-host` — fake-window protocol: handshake `login/ready`, four channels both ways, evil origin / `eval` / `access_token` rejected. No Docker, no Playwright.

## Browser walk (F011 / F012 / F013 — #75, 2026-09-22)

Vite `http://127.0.0.1:5173`, Chromium **375×812**. No JAR / PostgreSQL on this VM (login API 500; stub token still completes the hop).

| Step | Result |
|---|---|
| `/m/p3/files` while logged out | Redirect `/m/login?return=/m/p3/files` |
| Stub 登录并回跳 | Lands `/m/p3/files` mobile shell (no desktop sidebar) |
| 最近 tab + `demo-file-id` | `/m/p3/files?tab=recent` then `/m/p3/files/demo-file-id` (API error expected) |
| Home → 打开文件页 | `/m/p3/files?tab=upload` |
| Refresh | Stays on files, session kept |
| 结束会话并回跳登录 | `/m/login?return=/m/p3/files?tab=recent` then stub login returns there |
| `return=https://evil.example/m/p3/home` | Discarded; shown target `/m/p3/home` |
| `/p3/ledgers` | Desktop M0-12 sidebar + 创建台账 unchanged |

## Follow-up after rebase onto `origin/main` `92b57f8` (#80 then #82/#83/#74)

Recorded on this agent VM **2026-09-22T15:57:03Z**. OpenJDK **21.0.10**, Apache Maven **3.9.9** (not on PATH at boot; unpacked under `/tmp` for this run), Boot **4.1.1**. No Docker.

### IssuedTenantIsolationFilterTest (covers `/api/v1/files`)

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=IssuedTenantIsolationFilterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`BUILD SUCCESS`. `IssuedTenantIsolationFilterTest` **Tests run: 5, Failures: 0, Errors: 0, Skipped: 0**. `isHandled` asserts `/api/v1/files` and `/api/v1/files/abc/signed-url`. This is the binder unit test, not a live upload.

### Issued-context live upload — SKIP

No PostgreSQL 15 listener (`psql` missing, nothing on `:5432`). No product JAR process. Did **not** start PG, did **not** package `nexus-bootstrap`, did **not** POST `/api/v1/files` with a real `Bearer` + `X-Nexus-Context-Id`. Not a pass and not a fixture.

## F014 host-message evidence (this slice)

Recorded on this agent VM **2026-09-22T16:43:45Z**. Node from `npm test` (Vitest **3.2.7**). No Docker. No Playwright. No JAR / PostgreSQL required for this contract.

```bash
cd web
npm test
npm run assert:h5
npm run assert:h5-host
npm run assert:isolation
npm run assert:p3-live
npm run build
```

```text
vitest run  src/mobile/host/hostBridge.test.js  14 passed (14)
assert:h5       PASS  login-redirect / file / routing / F014 wired; desktop P3 untouched
assert:h5-host  PASS  handshake login/ready; 4 channels; origin/eval/token rejected
assert:isolation PASS
assert:p3-live   PASS
vite build       81 modules, HostMessagePage + HostHarnessPage emitted
```

Browser walk of `/m/host` + `/m/host-harness` is recorded after the first push. Product G15 remains **open** (APP WebView / 小程序 leftover).
