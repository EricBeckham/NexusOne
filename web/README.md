# NexusOne Web — Five-product desktop shell + separate G15 H5 mobile

Vue 3 desktop shell for five products: **星域** tenant administration, **星权** identity and permissions, **星构** studio, **星市** app catalog, and **星联** integrations. The shared product shell provides each product's own navigation, color, header, and home page.
G15 adds a **separate** mobile tree under `src/mobile/` (`/m/login`, `/m/callback`, `/m/p3/home`, `/m/p3/files`, `/m/p3/forms`, `/m/p3/lists`, `/m/p3/todos`, `/m/p3/query`, `/m/p3/notifications`, `/m/host`, `/m/host-harness`, `/m/mp-webview-harness`, `/m/app-webview-harness`, `/m/wx-dt-harness`, `/m/native-bridge-harness`, `/m/wx-dt-prod-gap-harness`, `/m/native-bridge-honesty-harness`). Desktop ledger / form / todo pages are unchanged. F014 host messages are accepted on the **mobile browser** (postMessage + iframe harness). A **simulated** 小程序 web-view parent reuses the same HostBridgePort channels. `/m/wx-dt-harness` is a WeChat / DingTalk **production-host contract stub** (same channels + allowlist); production is **not** accepted. `/m/wx-dt-prod-gap-harness` is the production mini-program **acceptance-gap honesty stub** — not production验收 vs #117/#132/#151. `/m/app-webview-harness` is a **web** simulation of APP WebView channels on the same envelope — not a native package. `/m/native-bridge-harness` is an iOS WK / Android JS **native-bridge stub** — not a store-ready IPA/APK. `/m/native-bridge-honesty-harness` is the native-bridge **honesty leftover** beyond #151 — stub contract PASS, IPA/APK SKIP, not production accept. F010 runtime pages reuse the same live P3 / notify APIs. H5 list/detail/form/todo reads and writes call `POST /api/v1/permission/check` on the same PermissionEngine as desktop ([docs/g2-h5-permission.md](../docs/g2-h5-permission.md)). This is **not** a full G15 close — APP WebView store-ready native package / WeChat/DingTalk production stay open. Mobile-browser PKCE leftover is [docs/g15-h5-pkce-browser.md](../docs/g15-h5-pkce-browser.md). See [docs/g15-h5-mobile.md](../docs/g15-h5-mobile.md), [docs/g15-f010-h5-runtime.md](../docs/g15-f010-h5-runtime.md), [docs/g15-miniprogram-webview.md](../docs/g15-miniprogram-webview.md), [docs/g15-app-webview-host.md](../docs/g15-app-webview-host.md), [docs/g15-wx-dt-prod-host.md](../docs/g15-wx-dt-prod-host.md), [docs/g15-native-bridge-stub.md](../docs/g15-native-bridge-stub.md), [docs/g15-native-bridge-honesty.md](../docs/g15-native-bridge-honesty.md), [docs/g15-wx-dt-prod-gap-honesty.md](../docs/g15-wx-dt-prod-gap-honesty.md), [docs/g15-wx-dt-native-bridge-honesty.md](../docs/g15-wx-dt-native-bridge-honesty.md), and [docs/g15-wx-dt-native-bridge-remainder.md](../docs/g15-wx-dt-native-bridge-remainder.md).

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
npm run assert:h5-native-bridge
npm run assert:h5-native-bridge-honesty
npm run assert:h5-wx-dt-native-bridge-honesty
npm run assert:h5-wx-dt-native-bridge-remainder
npm run assert:h5-pkce
npm run assert:g2-report-surface
npm run assert:g7-withdraw-browser
npm run test:g7-withdraw-browser-honesty
npm run assert:g7-withdraw-browser-honesty
npm test
npm run test:g7-withdraw
npm run test:g4-pkce
npm run test:g17-totp-enroll
npm run assert:g17-totp-enroll
npm run test:g17-vue-otp-writeback
npm run assert:g17-vue-otp-writeback
npm run test:g17-mfa-recovery-email-honesty
npm run assert:g17-mfa-recovery-email-honesty
npm run build
```

## Live P3

MOCK adapters are removed. The UI always calls live HTTP. Missing TenantContext is **403**. Empty lists are empty tenants, not fixtures.

Do **not** set `VITE_NEXUS_P3_USE_MOCK` (retired). Do **not** send `X-Tenant-Id`.

## Pages

### Desktop product routes

`/` is the product picker. Each `/pN` route is that product's home and is wrapped in the shared desktop shell. Existing deep links remain available.

| Product | Home | Available routes |
|---|---|---|
| 星域 · Nexus Tenant | `/p1` | `/p1/tenants`, `/p1/tenants/new`, `/p1/tenants/:id` |
| 星权 · Nexus IAM | `/p2` | `/p2/persons`, `/p2/org`, `/p2/positions`, `/p2/permissions/:kind?`, `/p2/mfa/enroll`, `/p2/mfa/recovery`, `/p2/mfa/email` |
| 星构 · Nexus Studio | `/p3` | `/p3/ledgers`, `/p3/ledgers/:id`, `/p3/ledgers/:id/records/:recordId`, `/p3/forms`, `/p3/forms/:id`, `/p3/todos`, `/p3/todos/:id`, `/p3/notifications`, `/p3/audit`, `/p3/reports`, `/p3/dumps`, `/p3/exports/wizard` |
| 星市 · Nexus Market | `/p4` | `/p4/market` (app catalog) |
| 星联 · Nexus Connect | `/p5` | `/p5/connect` (integration overview) |
| Authentication | — | `/login`, `/callback` (outside the product shell) |

H5 remains a separate `/m/*` surface under `src/mobile/`; this web-shell wave updates desktop only. See the H5 route table below.

| Route | API | Notes |
|---|---|---|
| `/p1/tenants` | `GET /api/v1/p1/tenants` | Directory |
| `/p1/tenants/new` | `POST /api/v1/p1/tenants` | Optional P2 admin bootstrap |
| `/p1/tenants/:id` | config / quota / entitlement / audit | Enable/disable is confirm=true |
| `/p2/persons` | `/api/v1/persons` | Tenant-scoped by issued context |
| `/p2/org` | `/api/v1/departments/tree` | |
| `/p2/positions` | positions + employments | Position ≠ Role |
| `/p2/mfa/enroll` | in-app TOTP leftover | Copy + 6-digit confirm + error paths. Keycloak otp write-back honesty panel (`totp-writeback-*`) SKIP when live IdP / product endpoint is absent. [docs/g17-totp-enrollment-ux.md](../docs/g17-totp-enrollment-ux.md) · [docs/g17-vue-otp-writeback.md](../docs/g17-vue-otp-writeback.md) |
| `/p2/mfa/recovery` | recovery-code leftover | Local one-time codes after TOTP leftover. Not Keycloak write. [docs/g17-admin-mfa-recovery-email.md](../docs/g17-admin-mfa-recovery-email.md) · delivery honesty [docs/g17-mfa-recovery-email-honesty.md](../docs/g17-mfa-recovery-email-honesty.md) · combined leftover [docs/g17-mfa-recovery-vue-otp-honesty.md](../docs/g17-mfa-recovery-vue-otp-honesty.md) · remainder leftover [docs/g17-mfa-recovery-vue-otp-remainder-honesty.md](../docs/g17-mfa-recovery-vue-otp-remainder-honesty.md) |
| `/p2/mfa/email` | email MFA leftover | Request contract; SMTP off → honest SKIP. Not a mailbox delivery. [docs/g17-admin-mfa-recovery-email.md](../docs/g17-admin-mfa-recovery-email.md) · delivery honesty [docs/g17-mfa-recovery-email-honesty.md](../docs/g17-mfa-recovery-email-honesty.md) · combined leftover [docs/g17-mfa-recovery-vue-otp-honesty.md](../docs/g17-mfa-recovery-vue-otp-honesty.md) · remainder leftover [docs/g17-mfa-recovery-vue-otp-remainder-honesty.md](../docs/g17-mfa-recovery-vue-otp-remainder-honesty.md) |
| `/p2/permissions/*` | `/api/v1/permission/health\|snapshot\|check` | No GET role catalog |
| `/p3/ledgers` | GET list + POST `/api/v1/p3/business-types/{code}/provision` | G7 请假/报销/出差；quota from P1 if platform mode |
| `/p3/ledgers/:id` | ledger + `.../records` + POST `.../fields` | No field PUT. G7 leftover list/detail/query for expense+travel: [docs/g7-query-expense-travel.md](../docs/g7-query-expense-travel.md). Honesty leftover: [docs/g7-query-expense-travel-honesty.md](../docs/g7-query-expense-travel-honesty.md). Query honesty remainder leftover: [docs/g7-query-expense-travel-honesty-remainder.md](../docs/g7-query-expense-travel-honesty-remainder.md). Query/withdraw leftover honesty: [docs/g7-query-withdraw-leftover-honesty.md](../docs/g7-query-withdraw-leftover-honesty.md) |
| `/p3/forms` | GET list + POST create | Bind selected ledger field_code |
| `/p3/forms/:id` | validate / publish / **submit-start** | Fill writes physical row + starts Flowable. Each click sends `Idempotency-Key` (G3) |
| `/p3/todos` | `/api/v1/p3/todos` + `/api/v1/p3/path/tasks/*` + withdraw | 同意/驳回/「我发起」撤回 (`data-testid=todo-withdraw`). G7 leftover walk: [docs/g7-withdraw-browser.md](../docs/g7-withdraw-browser.md). Live JAR/IdP honesty leftover: [docs/g7-withdraw-browser-honesty.md](../docs/g7-withdraw-browser-honesty.md). Expense/travel todo query leftover: [docs/g7-query-expense-travel.md](../docs/g7-query-expense-travel.md). Query honesty leftover: [docs/g7-query-expense-travel-honesty.md](../docs/g7-query-expense-travel-honesty.md). Query honesty remainder leftover: [docs/g7-query-expense-travel-honesty-remainder.md](../docs/g7-query-expense-travel-honesty-remainder.md). Query/withdraw leftover honesty: [docs/g7-query-withdraw-leftover-honesty.md](../docs/g7-query-withdraw-leftover-honesty.md) |
| `/p3/notifications` | `GET /api/v1/notifications` | Mark read |
| `/p3/audit` | `GET /api/v1/audit/traces/{traceId}` | Read-only, tenant-filtered |
| `/p3/reports` | `GET/POST /api/v1/p3/reports/*` after `ledger/read` + `ledger.export.csv/submit` | G2 leftover. Not a physical dump; desktop ledger/form/todo pages unchanged |
| `/p3/dumps` | `GET/POST /api/v1/p3/dumps/*` after `ledger/read` + `ledger.export.csv/submit` | G2 leftover. Raw `writeProjected` CSV; not report Task submit; desktop ledger/form/todo/report unchanged |
| `/p3/exports/wizard` | `POST /api/v1/p3/exports/wizard/begin` + `/complete` after `ledger.export.csv/submit` | G4 leftover. begin/complete (or deny) on the hash chain; not a product export UI |
| `/login` | stub token **or** `authorize-url?pkce=s256` | Stub card unchanged. Desktop PKCE leftover: `data-testid=pkce-login` → [docs/g4-pkce-browser-click.md](../docs/g4-pkce-browser-click.md). Password-only admin leftover: `admin-password-login` → `P2.IAM.MFA_REQUIRED` → `/p2/mfa/enroll` |
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
| `/m/native-bridge-harness` | native-bridge stub | iOS `webkit.messageHandlers.nexusHost` / Android `NexusNativeBridge` shapes. Same F014 envelope. **Not** a store-ready APP. Contrasts #114/#117/#132. |
| `/m/wx-dt-prod-gap-harness` | WeChat / DingTalk production-gap honesty stub | Same F014 envelope. Inventories what is still **not** production验收 vs #117/#132/#151. **Not** a product G15 close. |
| `/m/native-bridge-honesty-harness` | native-bridge honesty leftover | Extends #151 with honest PASS/SKIP. Stub contract PASS; IPA/APK SKIP. **Not** store-ready. **Not** a product G15 close. Combined leftover beyond #167/#181: [docs/g15-wx-dt-native-bridge-honesty.md](../docs/g15-wx-dt-native-bridge-honesty.md) (no new route). Remainder leftover beyond #167/#181/#198: [docs/g15-wx-dt-native-bridge-remainder.md](../docs/g15-wx-dt-native-bridge-remainder.md) (no new route). |
| `/m/p3/forms` | `GET /api/v1/p3/forms` after `form/read` check | F010 runtime list. Designer stays desktop |
| `/m/p3/forms/:id` | `form/read` then `form/write` + `submit-start` | Fill published form. No Validate / Publish |
| `/m/p3/lists`, `/m/p3/lists/:id` | `ledger/read` then ledgers + records | F010 record cards |
| `/m/p3/todos`, `/m/p3/todos/:id` | `process.task/read` then path approve/reject/withdraw | F010 inbox / done / 我发起 |
| `/m/p3/query` | `ledger/read` then `GET .../records?field=`; export button is `ledger.export.csv/submit` | Field-equals query; never `tenant_id`. Export is AuthorizedExport, not dump |
| `/m/p3/notifications` | `/api/v1/notifications` | Jump to safe `/m/*` only |

Do not treat this shell as a product Keycloak Authorization Code + PKCE acceptance. Desktop leftover click evidence is [docs/g4-pkce-browser-click.md](../docs/g4-pkce-browser-click.md) (stub IdP, not a G4 close). H5 `/m/*` leftover is [g15-h5-pkce-browser.md](../docs/g15-h5-pkce-browser.md) (Playwright/stub). Product default authorize URL remains M0-04’s confidential client without PKCE. G15 F014 is accepted on the mobile-browser postMessage contract, the simulated 小程序 web-view parent, the APP WebView **web** harness, the WeChat / DingTalk **production-host stub**, the APP WebView **native-bridge stub**, the WeChat / DingTalk **production-gap honesty stub**, and the APP WebView **native-bridge honesty leftover**. Store-ready APP WebView native package and WeChat / DingTalk production are not accepted. Product G15 stays open.
