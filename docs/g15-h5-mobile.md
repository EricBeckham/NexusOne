# G15 H5 — mobile login redirect, files, routing (S1 partial)

Work package: new **mobile** pages only. First acceptance host is a **mobile browser**.  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker. Desktop ledger / form / todo pages are not part of this change.

**This does not close product G15.** G15 in 需求规格 V1.1 §12 also asks for host-message acceptance and other hosts (APP WebView / 小程序 web-view). Those stay open.

## What this slice adds

| Need | Route | Behavior |
|---|---|---|
| F011 login redirect | `/m/login?return=…` → `/m/callback` | Unauthenticated `/m/*` is sent to login with a **safe** Return URL. Stub login (same `stub.` token as M0-12) returns to that path. Token / `tenant_id` / off-site returns are dropped. |
| F012 files | `/m/p3/files`, `/m/p3/files/:id` | Client size (20 MB) and MIME checks, then `POST /api/v1/files`. Download is `POST .../signed-url` after metadata. Progress + cancel. Never MinIO admin APIs, never `X-Tenant-Id`. |
| F013 routing | `/m/p3/home`, files + query `tab`, file deep link | History mode. Refresh keeps `sessionStorage` session. Tenant stays in the issued context, not the URL. Back uses `history.back`, else mobile home. |

Desktop `/p3/ledgers`, `/p3/forms`, `/p3/todos` (and their views) are unchanged.

Issued TenantContext for File Service uses the existing `IssuedTenantIsolationFilter` binder (now also `/api/v1/files`). Demo file headers stay test-only.

## Honest gaps (not claimed)

| Item | Status |
|---|---|
| F014 host messages | Contract-only `hostBridge` (`login` / `route` / `file` / `close`). **Not accepted.** No APP WebView / 小程序 walk. |
| Keycloak Authorization Code + PKCE | **Not accepted.** `/m/callback` lands code/state and POSTs `/api/v1/auth/login`; the confidential client does not write Access Token into the SPA or URL. Browser session is still stub token, same as M0-12. |
| F010 full H5 runtime | Forms / lists / todos / query / notify jumps are **not** in this slice. |
| Other hosts | APP WebView, 小程序 web-view: adaptation list only. |
| Live S3 / MinIO download | Product default `nexus.files.storage=memory`. Signed URL against real S3 is M0-10, not re-run here. |
| G18 / P0 / M0 | Closed or out of scope. Not redone. |

## How to run

```bash
cd web
npm install
npm run assert:h5
npm run assert:isolation
npm run assert:p3-live
npm run build
npm run dev
```

Open a mobile-width browser at `http://127.0.0.1:5173/m/p3/files` (redirects to `/m/login?return=…`). Stub login, then files / home / back / refresh.

Live upload needs the JAR + issued `X-Nexus-Context-Id` (tenant membership). Missing context is a **403**, not a fixture list.

## Checks

`npm run assert:h5` — safe return URL, MIME/size, host-channel denylist, `/m` routes present, desktop P3 views do not import the mobile shell.

## Browser walk (this agent VM, 2026-09-22)

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

Host messages / PKCE / live File Service upload against issued context were **not** walked. Client size/MIME checks are in `assert:h5` plus on-page 演示 buttons.

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

Product G15 remains **open**. This slice is still login-redirect / files / routing only.
