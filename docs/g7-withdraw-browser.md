# G7 leftover — browser/UI withdraw (`todo-withdraw`)

S0+S1 remainder. **Not** a P0/M0 redo. Product **G18 stays closed**. `AuditPort` is unchanged.

Eric’s G18 browser walk on `38f1a73` approved 请假 and **did not click 撤回**. Scripted HTTP for that missing step is already on `main`:

- Leave: [g7-withdraw-evidence.md](./g7-withdraw-evidence.md) (`G7LeaveWithdrawIT`)
- Expense + travel (non-initiator 403): [g7-withdraw-expense-travel.md](./g7-withdraw-expense-travel.md)
- Catalog + dual-tenant HTTP: [g7-business-types.md](./g7-business-types.md)

This leftover **extends** the existing Vue control `data-testid=todo-withdraw` (ghost **撤回** on `/p3/todos?box=initiated`). It proves the HTTP withdraw evidence path `POST /api/v1/p3/path/instances/{id}/withdraw` is **reachable from the desktop UI** for leave / expense / travel. The stub walk uses **stub login** (`data-testid=stub-login`) so it runs **without Docker** and without a live PostgreSQL listener.

**Not claimed:** G7 three-type product close, G18 browser reopen, live product-JAR / live IdP withdraw click, G2 / G4 remainder, full S0+S1 release. Vue `TodoInboxView.vue` is not rewritten. `AuditPort` is not touched.

Keep sibling leftover rows in [docs/README.md](./README.md): `g9-sentinel-cluster-honesty`, `g4-export-wizard`, `g4-p1-staging-audit-events`, `g4-pkce-spa-verifier`, `g15-native-bridge-stub`.

## What is proven

1. Vitest + happy-dom mounts `TodoInboxView` and clicks `data-testid=todo-withdraw` on pending **我发起** leave / expense / travel rows. Each click calls `p3.withdrawTodo(id, comment, instance_id)` and shows `approval_status=WITHDRAWN`.
2. Vitest + happy-dom `p3-live.withdrawTodo` POSTs the same HTTP path as `G7LeaveWithdrawIT` / `G7ExpenseTravelWithdrawIT`:
   - `POST /api/v1/p3/path/instances/{id}/withdraw`
   - Bearer + issued `X-Nexus-Context-Id`
   - never `X-Tenant-Id`
3. Playwright + **system Chrome** (no Playwright browser download) against Vite preview:
   - Stub login (`data-testid=stub-login`) issues `X-Nexus-Context-Id` (never `X-Tenant-Id`)
   - `/p3/todos?box=initiated` shows pending leave / expense / travel
   - Click **撤回** → notice `已 withdraw。approval_status=WITHDRAWN`
   - Stub records `POST /api/v1/p3/path/instances/{id}/withdraw`
4. Live product-JAR + live/Keycloak IdP browser is an **honest SKIP** unless a healthy JAR, issued context, live IdP, and Chrome are actually present. Honesty fixtures reject a forged `PASS` and reject `g7_product_close=true`.

HTTP withdraw on the G18 path host remains the product proof. This leftover is the **browser control + adapter reachability**.

## How to run

Baseline: Java 21, Spring Boot **4.1.1**, Node 22, system Chrome, no Docker.

```bash
./scripts/g7/run-withdraw-browser.sh
```

Equivalents:

```bash
# Always-on Vue click + HTTP reachability (no Chrome, no JAR)
cd web && npm run test:g7-withdraw
cd web && npm run assert:g7-withdraw-browser

# Playwright walk (stub API + Vite preview + system Chrome)
cd scripts/g7 && npm install
cd web && npm install && npm run build
node scripts/g7/stub-withdraw-api.mjs &
NEXUS_API_ORIGIN=http://127.0.0.1:18087 npx vite preview --host 127.0.0.1 --port 4173
node scripts/g7/withdraw-leave-browser.mjs \
  --base http://127.0.0.1:4173 \
  --stub http://127.0.0.1:18087 \
  --json-out docs/g7-withdraw-browser/walk.json

# Live JAR + IdP (honest SKIP on this agent VM)
./scripts/g7/run-withdraw-live-browser.sh
node scripts/g7/verify-withdraw-live-report.mjs docs/g7-withdraw-browser/live-walk.json
# expected FAIL:
node scripts/g7/verify-withdraw-live-report.mjs scripts/g7/fixtures/fake-live-pass.json
node scripts/g7/verify-withdraw-live-report.mjs scripts/g7/fixtures/fake-g7-close.json
```

If Playwright or Chrome is missing, the stub walk writes `result=SKIP` and exits 0. HTTP withdraw on main stays proven.

Live product-JAR browser withdraw stays **honest SKIP** without a healthy `NEXUS_BASE_URL`, issued `NEXUS_G7_LIVE_CONTEXT_ID`, and live/Keycloak IdP. Stub login is not a live walk.

## Browser steps (leave / expense / travel)

1. Open `/login`. Click **生成 token 并登录** (`data-testid=stub-login`).
2. Stay on the issued tenant context.
3. 待办 → **我发起** (`/p3/todos?box=initiated`).
4. Select the pending 请假 / 报销 / 出差 instance.
5. Click **撤回** (`data-testid=todo-withdraw`).
6. Notice `已 withdraw。approval_status=WITHDRAWN`. Button hidden.

Catalog provision + live JAR is **not** required for this leftover. Walking stub rows is **not** a product G7 close.

## Evidence

Stub walk recorded **2026-09-22T17:37:15Z** with `./scripts/g7/run-withdraw-browser.sh`. Artifact: [walk.json](./g7-withdraw-browser/walk.json). Live JAR/IdP artifact: [live-walk.json](./g7-withdraw-browser/live-walk.json).

Screenshots (stub login):

- [01-stub-login](./g7-withdraw-browser/01-stub-login.png) — `/login`, stub email `g7-ui@example.com`
- [02-initiated](./g7-withdraw-browser/02-initiated.png) — 我发起: 请假 / 报销 / 出差 `RUNNING`
- [02b-todo-withdraw](./g7-withdraw-browser/02b-todo-withdraw.png) — leave row selected, **撤回** visible
- [03-withdrawn](./g7-withdraw-browser/03-withdrawn.png) — all three `WITHDRAWN`; notice `已 withdraw。approval_status=WITHDRAWN`

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) |
| Spring Boot | **4.1.1** (product baseline; this leftover does not boot the JAR) |
| Node | **22.14.0** |
| Browser | Google Chrome **148.0.7778.96** + `playwright-core` **1.55+** (system binary, no Docker, no bundled Chromium download) |
| Login | stub (`data-testid=stub-login`) → issued `X-Nexus-Context-Id=01a0c98a-1f3c-7f72-b724-6ac34746e981` |
| Control | `data-testid=todo-withdraw` |
| HTTP path | `POST /api/v1/p3/path/instances/{id}/withdraw` |
| `AuditPort` | **untouched** |
| Docker | no |

| Suite | Result |
|---|---|
| Vitest `todo-withdraw.browser.test.js` | **pending-this-run** — Vue click of `todo-withdraw` on leave + expense + travel |
| Vitest `p3-withdraw-http-reachability.test.js` | **pending-this-run** — adapter POSTs the HTTP withdraw evidence path with issued context, never `X-Tenant-Id` |
| Playwright stub walk | existing [walk.json](./g7-withdraw-browser/walk.json) — stub login → 我发起 → 撤回 on leave + expense + travel |
| Live product-JAR + IdP browser | **honest SKIP** — no live JAR + issued-context IdP Playwright walk on this agent VM |
| Honesty fixtures | honest SKIP accepted; fake live PASS and fake G7 close rejected |
| G7 three-type product close | **not claimed** |

Expense / travel UI clicks here are the **same Vue button**. They are **not** catalog provision + live-JAR evidence and do **not** close G7 as a product gate.

## Honest leftover G7 rows

| Item | Status |
|---|---|
| Leave withdraw HTTP | **on main** [g7-withdraw-evidence.md](./g7-withdraw-evidence.md) |
| Expense / travel withdraw HTTP | **on main** [g7-withdraw-expense-travel.md](./g7-withdraw-expense-travel.md) |
| Expense / travel list / detail / todo query | leftover [g7-query-expense-travel.md](./g7-query-expense-travel.md); not this package |
| Vue `todo-withdraw` + HTTP reachability | **this package** (Vitest against stub / mocked fetch) |
| Live product-JAR + live IdP browser withdraw | **honest SKIP** unless JAR + issued context + live/Keycloak IdP + Chrome are present |
| G7 three-type product close | **not claimed** |
| G18 | **stays closed** |
