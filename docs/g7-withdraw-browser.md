# G7 leftover — browser/UI withdraw (`todo-withdraw`)

S0+S1 remainder. **Not** a P0/M0 redo. Product **G18 stays closed**. `AuditPort` is unchanged.

Eric’s G18 browser walk on `38f1a73` approved 请假 and **did not click 撤回**. Scripted HTTP for that missing step is already on `main`:

- Leave: [g7-withdraw-evidence.md](./g7-withdraw-evidence.md) (`G7LeaveWithdrawIT`)
- Expense + travel (non-initiator 403): [g7-withdraw-expense-travel.md](./g7-withdraw-expense-travel.md)
- Catalog + dual-tenant HTTP: [g7-business-types.md](./g7-business-types.md)

This package only adds **UI click evidence** for the existing Vue control `data-testid=todo-withdraw` (ghost **撤回** on `/p3/todos?box=initiated`). The walk uses **stub login** (`data-testid=stub-login`) and a local stub API so it runs **without Docker** and without a live PostgreSQL listener.

**Not claimed:** G7 three-type product close, G18 browser reopen, live product-JAR withdraw click, G2 / G4 remainder, full S0+S1 release. Vue `TodoInboxView.vue` is not rewritten. `AuditPort` is not touched.

Leave UI is the required leftover. Expense / travel are optional extra clicks of the **same** control against stub rows. Walking only leave (or walking stub expense/travel) is **not** a product G7 close.

## What is proven

1. Vitest + happy-dom mounts `TodoInboxView`, selects a pending **我发起** leave row, clicks `data-testid=todo-withdraw`, and asserts `p3.withdrawTodo(id, comment, instance_id)` plus `approval_status=WITHDRAWN`.
2. Playwright + **system Chrome** (no Playwright browser download) against Vite preview:
   - Stub login (`data-testid=stub-login`) issues `X-Nexus-Context-Id` (never `X-Tenant-Id`)
   - `/p3/todos?box=initiated` shows pending leave (and stub expense / travel)
   - Click **撤回** → notice `已 withdraw。approval_status=WITHDRAWN`
   - Button hides after the row is no longer pending
   - Stub records `POST /api/v1/p3/path/instances/{id}/withdraw` with Bearer + issued context

HTTP withdraw on the G18 path host remains the product proof. This leftover is the **browser control**.

## How to run

Baseline: Java 21, Spring Boot **4.1.1**, Node 22, system Chrome, no Docker.

```bash
./scripts/g7/run-withdraw-browser.sh
```

Equivalents:

```bash
# Always-on Vue click (no Chrome, no JAR)
cd web && npm test -- src/views/studio/todo-withdraw.browser.test.js

# Playwright walk (stub API + Vite preview + system Chrome)
cd scripts/g7 && npm install
cd web && npm install && npm run build
node scripts/g7/stub-withdraw-api.mjs &
NEXUS_API_ORIGIN=http://127.0.0.1:18087 npx vite preview --host 127.0.0.1 --port 4173
node scripts/g7/withdraw-leave-browser.mjs \
  --base http://127.0.0.1:4173 \
  --stub http://127.0.0.1:18087 \
  --json-out docs/g7-withdraw-browser/walk.json
```

If Playwright or Chrome is missing, the walk writes `result=SKIP` and exits 0. HTTP withdraw on main stays proven.

Live product JAR (optional confirmation, not this leftover):

Same G18 loop as [web/README.md](../web/README.md): stub login, issued `X-Nexus-Context-Id`, never `X-Tenant-Id`. UI `http://127.0.0.1:5173`. 待办 → **我发起** → **撤回**.

## Browser steps (leave)

1. Open `/login`. Click **生成 token 并登录** (`data-testid=stub-login`).
2. Stay on the issued tenant context.
3. 待办 → **我发起** (`/p3/todos?box=initiated`).
4. Select the pending 请假 instance.
5. Click **撤回** (`data-testid=todo-withdraw`).
6. Notice `已 withdraw。approval_status=WITHDRAWN`. Button hidden.

Expense / travel use the same button when those initiated rows are pending. Catalog provision + live JAR is **not** required for this leftover.

## Evidence

Recorded on this branch with `./scripts/g7/run-withdraw-browser.sh`. Artifact: [walk.json](./g7-withdraw-browser/walk.json). Screenshots: [01-stub-login](./g7-withdraw-browser/01-stub-login.png), [02-initiated](./g7-withdraw-browser/02-initiated.png), [03-withdrawn](./g7-withdraw-browser/03-withdrawn.png).

| Item | Value |
|---|---|
| JDK | OpenJDK **21** |
| Spring Boot | **4.1.1** |
| Node | **22** |
| Browser | system Chrome + `playwright-core` (no Docker, no bundled Chromium download) |
| Login | stub (`data-testid=stub-login`) |
| Control | `data-testid=todo-withdraw` |
| `AuditPort` | **untouched** |
| Docker | no |

| Suite | Result |
|---|---|
| Vitest `todo-withdraw.browser.test.js` | Vue click of `todo-withdraw` → `withdrawTodo(..., instance_id)` + `WITHDRAWN` |
| Playwright walk | stub login → 我发起 → 撤回 (leave required; expense/travel optional same control) |
| live product JAR | **not claimed** — HTTP on main already recorded |

## Honest gaps

| Item | Status |
|---|---|
| Leave withdraw HTTP | **on main** [g7-withdraw-evidence.md](./g7-withdraw-evidence.md) |
| Expense / travel withdraw HTTP | **on main** [g7-withdraw-expense-travel.md](./g7-withdraw-expense-travel.md) |
| Leave UI click of `todo-withdraw` | **this package** (Vitest + Playwright against stub login) |
| Expense / travel UI click | same control on stub rows if the walk includes them; **not** a catalog/live-JAR close |
| Live product-JAR browser withdraw | optional confirmation; not required to keep G18 closed |
| G7 three-type product close | **not claimed** |
| G18 | **stays closed** |
