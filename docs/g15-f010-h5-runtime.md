# G15 F010 — H5 runtime (forms / lists / todos / query / notify jumps)

Work package: **mobile runtime pages only** under `web/src/mobile`. First acceptance host is a **mobile browser**.  
Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker. Desktop ledger / form / todo pages are unchanged.

**This does not close product G15.** APP WebView and WeChat / DingTalk production 小程序 stay open. A simulated web-view parent is [g15-miniprogram-webview.md](./g15-miniprogram-webview.md). Keycloak Authorization Code + PKCE stays open. G18 stays closed and is not redone. F014 HostBridgePort is reused; this slice does not fork a second bridge.

F010 in 需求规格 V1.1: 发布后的应用提供响应式 H5 运行入口，覆盖表单、列表、流程待办、查询、文件和通知跳转.

## What this slice adds

| Surface | Route | Behavior |
|---|---|---|
| Forms | `/m/p3/forms`, `/m/p3/forms/:id` | List published forms. Fill uses `POST /api/v1/p3/path/submit-start` (physical row + sequential approval). No designer / Validate / Publish on H5. |
| Lists | `/m/p3/lists`, `/m/p3/lists/:id`, `/m/p3/lists/:id/records/:recordId` | Ledger cards → record cards → detail. `GET /api/v1/p3/ledgers` and `.../records`. No create-ledger / field designer. |
| Todos | `/m/p3/todos`, `/m/p3/todos/:id` | Inbox / done / initiated. Approve / reject / withdraw reuse path APIs. |
| Query | `/m/p3/query` | Field-equals filters on `GET /api/v1/p3/ledgers/{id}/records`. Same isolation kernel as desktop list. No second Query AST. |
| Notify jumps | `/m/p3/notifications` | `GET /api/v1/notifications` then jump to a **safe** `/m/*` path. `approval.task.assigned` → inbox; approved/rejected → 我发起. Explicit `todo_id` / `form_id` / `ledger_id` / `file_id` deep-link when present. |
| Files | `/m/p3/files` | Already on main (F012). Linked from home + bottom tabs. |

Desktop `/p3/ledgers`, `/p3/forms`, `/p3/todos`, `/p3/notifications` stay the modeling / G18 shell.

Issued TenantContext is `X-Nexus-Context-Id` after membership checks. The UI never sends `X-Tenant-Id`. Tenant is not placed in the URL. Missing context is a **403**, not a fixture list.

## Host / session

- Auth is the existing F011 stub hop (`stub.` token, same as M0-12). `/m/*` without a session goes to `/m/login?return=…`.
- F014 `hostBridge` (`login` / `route` / `file` / `close`) is unchanged. Runtime pages do not branch on WeChat / DingTalk.
- Refresh keeps `sessionStorage`. Back uses `history.back`, else mobile home.

## Honest leftovers (not claimed)

| Item | Status |
|---|---|
| APP WebView | **Not accepted.** Same HostBridge contract, no native host walk. |
| 小程序 web-view | Simulated parent in [g15-miniprogram-webview.md](./g15-miniprogram-webview.md). **WeChat / DingTalk production not accepted.** |
| Keycloak Authorization Code + PKCE | **Not accepted.** Browser session is still stub token. |
| H5 designer / BPMN | **Out of scope.** Desktop first (H5 规范 §52.8 / §91). |
| Product G15 | **Open** until other hosts are walked. |
| G18 / P0 / M0 | Closed or out of scope. Not redone. |
| Live JAR + issued context walk | Optional. This slice asserts wiring + stub-session routes. Empty lists / 403 without PostgreSQL are honest, not fixtures. |
| H5 permission | List/detail/form/todo now call `PermissionService.decide` via `/permission/check`. See [g2-h5-permission.md](./g2-h5-permission.md). Not a product-wide G2 close. |

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

Open a mobile-width browser at `http://127.0.0.1:5173/m/p3/home` (redirects to `/m/login?return=…` when logged out). Stub login, then 表单 / 列表 / 待办 / 查询 / 通知 / 文件.

## Checks

`npm test` — HostBridgePort units plus F010 `notifyJump` / `recordQuery` units.

`npm run assert:h5` — previous F011–F014 wiring plus the new `/m/p3/forms|lists|todos|query|notifications` routes. Desktop P3 views still do not import the mobile shell.

`npm run assert:h5-runtime` — runtime pages call live P3 / notify APIs; fill has no designer; lists do not provision; notify jumps drop desktop / token / `tenant_id`; query builder drops reserved columns; HostBridge channels stay `login,route,file,close`.
