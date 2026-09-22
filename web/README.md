# NexusOne Web (M0-12 + G15 H5 mobile)

Vue 3 shell for tenant admin, org/identity, permission snapshot, and **P3 台账 / 表单 / 待办 / 通知 / 审计**.
G15 adds a **separate** mobile tree under `src/mobile/` (`/m/login`, `/m/callback`, `/m/p3/home`, `/m/p3/files`, `/m/p3/forms`, `/m/p3/lists`, `/m/p3/todos`, `/m/p3/query`, `/m/p3/notifications`, `/m/host`, `/m/host-harness`, `/m/mp-webview-harness`, `/m/app-webview-harness`, `/m/wx-dt-harness`). Desktop ledger / form / todo pages are unchanged. F014 host messages are accepted on the **mobile browser** (postMessage + iframe harness). A **simulated** 小程序 web-view parent reuses the same HostBridgePort channels. `/m/wx-dt-harness` is a WeChat / DingTalk **production-host contract stub** (same channels + allowlist); production is **not** accepted. `/m/app-webview-harness` is a **web** simulation of APP WebView channels on the same envelope — not a native package. F010 runtime pages reuse the same live P3 / notify APIs. H5 list/detail/form/todo reads and writes call `POST /api/v1/permission/check` on the same PermissionEngine as desktop ([docs/g2-h5-permission.md](../docs/g2-h5-permission.md)). This is **not** a full G15 close — APP WebView native binary / WeChat/DingTalk production stay open. Mobile-browser PKCE leftover is [docs/g15-h5-pkce-browser.md](../docs/g15-h5-pkce-browser.md). See [docs/g15-h5-mobile.md](../docs/g15-h5-mobile.md), [docs/g15-f010-h5-runtime.md](../docs/g15-f010-h5-runtime.md), [docs/g15-miniprogram-webview.md](../docs/g15-miniprogram-webview.md), [docs/g15-app-webview-host.md](../docs/g15-app-webview-host.md), and [docs/g15-wx-dt-prod-host.md](../docs/g15-wx-dt-prod-host.md).

- Tenant / org pages talk to merged **M0-03** and **M0-04** HTTP APIs.
- Permission pages talk to **M0-05** `/api/v1/permission` (health / snapshot / check).
- Ledger / form / todo pages talk to live **M0-06/07/08** HTTP plus the G18/G7 path (`/api/v1/p3/path/submit-start`, `/api/v1/p3/business-types`).
- Notify: `/api/v1/notifications`. Audit by trace (read-only): `/api/v1/audit/traces/{traceId}`.
- Product G18 (one vertical slice) is closed by the 2026-09-22 browser record in [docs/g18-business-path.md](../docs/g18-business-path.md). The earlier `ace55ff` walk only reached tenant / org / permission snapshot.

## Run

Backend (PostgreSQL 15.x, no Docker):

```bash
export NEXUS_DB_URL='jdbc:postgresql://127.0.0.1:5432/nexusone'
export NEXUS_DB_USER='nexus'
export NEXUS_DB_PASSWORD   # env only
export NEXUS_P1_DEMO_CONTEXT_ENABLED=true
export NEXUS_PLATFORM_BOOTSTRAP_TOKEN='m0-12-bootstrap'
export NEXUS_IAM_IDP=stub
mvn -q -DskipTests package
java -jar nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar
```

Frontend (Vite proxies `/api` and `/actuator` to the JAR so the browser never attaches `X-Tenant-Id`):

```bash
cd web
npm install
npm run dev
```

Open `http://127.0.0.1:5173/login`. Default Vite proxy target is `:8080`; set `NEXUS_API_ORIGIN=http://127.0.0.1:18085` if the JAR listens on 18085.

- Stub login generates the same `stub.<base64url(json)>` token format as `StubIdentityProvider`.
- Enable **平台操作** and set the demo actor / bootstrap token (same values as the backend env).
- Tenant pages use P1 demo PlatformContext headers (local JAR only).
- Person / org / position / P3 / permission / notify / audit pages use `Authorization` + `X-Nexus-Context-Id`.

Checks (no running server required):

```bash
cd web
npm run assert:isolation
npm run assert:p3-live
npm run assert:h5
npm run assert:h5-host
npm run assert:h5-mp-webview
npm run assert:h5-runtime
npm run assert:h5-permission
npm run assert:h5-app-webview
npm run assert:h5-wx-dt
npm run assert:h5-pkce
npm run assert:g2-report-surface
npm test
npm run test:g7-withdraw
npm run test:g4-pkce
npm run build
```

## Live P3

MOCK adapters are removed. The UI always calls live HTTP. Missing TenantContext is **403**. Empty lists are empty tenants, not fixtures.

Do **not** set `VITE_NEXUS_P3_USE_MOCK` (retired). Do **not** send `X-Tenant-Id`.

## Pages

| Route | API | Notes |
|---|---|---|
| `/p1/tenants` | `GET /api/v1/p1/tenants` | Directory |
| `/p1/tenants/new` | `POST /api/v1/p1/tenants` | Optional P2 admin bootstrap |
| `/p1/tenants/:id` | config / quota / entitlement / audit | Enable/disable is confirm=true |
| `/p2/persons` | `/api/v1/persons` | Tenant-scoped by issued context |
| `/p2/org` | `/api/v1/departments/tree` | |
| `/p2/positions` | positions + employments | Position ≠ Role |
| `/p2/permissions/*` | `/api/v1/permission/health\|snapshot\|check` | No GET role catalog |
| `/p3/ledgers` | GET list + POST `/api/v1/p3/business-types/{code}/provision` | G7 请假/报销/出差；quota from P1 if platform mode |
| `/p3/ledgers/:id` | ledger + `.../records` + POST `.../fields` | No field PUT |
| `/p3/forms` | GET list + POST create | Bind selected ledger field_code |
| `/p3/forms/:id` | validate / publish / **submit-start** | Fill writes physical row + starts Flowable. Each click sends `Idempotency-Key` (G3) |
| `/p3/todos` | `/api/v1/p3/todos` + `/api/v1/p3/path/tasks/*` + withdraw | 同意/驳回/「我发起」撤回 (`data-testid=todo-withdraw`). G7 leftover walk: [docs/g7-withdraw-browser.md](../docs/g7-withdraw-browser.md) |
| `/p3/notifications` | `GET /api/v1/notifications` | Mark read |
| `/p3/audit` | `GET /api/v1/audit/traces/{traceId}` | Read-only, tenant-filtered |
| `/p3/reports` | `GET/POST /api/v1/p3/reports/*` after `ledger/read` + `ledger.export.csv/submit` | G2 leftover. Not a physical dump; desktop ledger/form/todo pages unchanged |
| `/login` | stub token **or** `authorize-url?pkce=s256` | Stub card unchanged. Desktop PKCE leftover: `data-testid=pkce-login` → [docs/g4-pkce-browser-click.md](../docs/g4-pkce-browser-click.md) |
| `/callback` | `POST /api/v1/auth/login` with `code` + `code_verifier` | Desktop leftover only. Not `/m/callback`. Not product Keycloak acceptance |
| `/m/login` | stub token + **PKCE** (`mobile-pkce-login`) + safe `return` | F011 stub hop stays; H5 PKCE leftover: [g15-h5-pkce-browser.md](../docs/g15-h5-pkce-browser.md) |
| `/m/callback` | `code` / `state` + sessionStorage `code_verifier` → `POST /api/v1/auth/login` | Strips query after hop; no token / verifier in URL |
| `/m/p3/home` | session in `sessionStorage` | F013 refresh / back |
| `/m/p3/files` | `POST/GET /api/v1/files` | F012 size + MIME + signed download |
| `/m/p3/files/:id` | metadata + signed-url | Deep link; tenant stays in issued context |
| `/m/host` | HostBridgePort | F014 login/route/file/close; web fallback when not embedded |
| `/m/host-harness` | iframe parent | Mobile-browser acceptance host (`nexus-host` postMessage) |
| `/m/mp-webview-harness` | simulated 小程序 web-view parent | Same HostBridgePort channels; not WeChat / DingTalk production |
| `/m/app-webview-harness` | simulated APP chrome | Web-only APP WebView host (`NexusAppWebView` + same F014 envelope). Not a native binary. |
| `/m/wx-dt-harness` | WeChat / DingTalk production-host stub | Same HostBridgePort channels + origin/channel allowlist. **Not** production accept. |
| `/m/p3/forms` | `GET /api/v1/p3/forms` after `form/read` check | F010 runtime list. Designer stays desktop |
| `/m/p3/forms/:id` | `form/read` then `form/write` + `submit-start` | Fill published form. No Validate / Publish |
| `/m/p3/lists`, `/m/p3/lists/:id` | `ledger/read` then ledgers + records | F010 record cards |
| `/m/p3/todos`, `/m/p3/todos/:id` | `process.task/read` then path approve/reject/withdraw | F010 inbox / done / 我发起 |
| `/m/p3/query` | `ledger/read` then `GET .../records?field=`; export button is `ledger.export.csv/submit` | Field-equals query; never `tenant_id`. Export is AuthorizedExport, not dump |
| `/m/p3/notifications` | `/api/v1/notifications` | Jump to safe `/m/*` only |

Do not treat this shell as a product Keycloak Authorization Code + PKCE acceptance. Desktop leftover click evidence is [docs/g4-pkce-browser-click.md](../docs/g4-pkce-browser-click.md) (stub IdP, not a G4 close). H5 `/m/*` leftover is [g15-h5-pkce-browser.md](../docs/g15-h5-pkce-browser.md) (Playwright/stub). Product default authorize URL remains M0-04’s confidential client without PKCE. G15 F014 is accepted on the mobile-browser postMessage contract, the simulated 小程序 web-view parent, the APP WebView **web** harness, and the WeChat / DingTalk **production-host stub**. APP WebView native binary and WeChat / DingTalk production are not accepted. Product G15 stays open.
