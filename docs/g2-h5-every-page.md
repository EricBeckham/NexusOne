# G2 leftover — H5 every-page SurfaceGuard coverage honesty

S0+S1 leftover from [g2-permission.md](./g2-permission.md) and [g2-h5-permission.md](./g2-h5-permission.md): #116 put H5 list / detail / form / todo (and query) on the same `SurfaceGuard`. That is **not** every `/m/*` page. This package **documents and statically guards** which routes are covered versus still open. **This is not a product-wide G2 close.** `g2_close=NOT_CLAIMED`.

It does **not** redo #116 H5 `/check`, #150 todo HTTP, #144 isolation-only `tenant_only` / catalog, or #137 physical dump. It does **not** put home / files / notify / login / host / harness on `SurfaceGuard`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. **G18 stays closed.**

Reuse only: the existing #116 Vue `useH5Surface` / `H5SurfaceGuard` twin. Java 21 / Boot **4.1.1**. No Docker. Orthogonal to #155 ops `pg_dump` honesty ([g2-ops-pg-dump.md](./g2-ops-pg-dump.md)) — that leftover row stays. Follow-on honest PASS/SKIP leftover (does **not** redo this leftover): [g2-h5-every-page-form-write-honesty.md](./g2-h5-every-page-form-write-honesty.md). Follow-on remainder honesty leftover (does **not** redo this leftover): [g2-h5-every-page-form-write-remainder-honesty.md](./g2-h5-every-page-form-write-remainder-honesty.md).

## What this leftover covers

| `/m/*` route | Coverage | Cell / note |
|---|---|---|
| `/m/p3/lists`, `/m/p3/lists/:id`, `/m/p3/lists/:id/records/:recordId` | **COVERED** (#116) | `ledger` / `read` via `useH5Surface` |
| `/m/p3/query` | **COVERED** (#116) | `ledger` / `read`; export button is #124 `ReportSurface` |
| `/m/p3/forms`, `/m/p3/forms/:id` | **COVERED** (#116) | `form` / `read` + fill `form` / `write` |
| `/m/p3/todos`, `/m/p3/todos/:id` | **COVERED** (#116) | `process.task` / `read` + approve/reject/withdraw |
| `/m`, `/m/login`, `/m/callback` | **OPEN** | redirect / public auth |
| `/m/host`, `/m/host-harness`, `/m/mp-webview-harness`, `/m/app-webview-harness`, `/m/wx-dt-harness`, `/m/native-bridge-harness` | **OPEN** | G15 host / harness leftovers |
| `/m/p3/home` | **OPEN** | session home; no `SurfaceGuard` |
| `/m/p3/files`, `/m/p3/files/:id` | **OPEN** | File Service; no `SurfaceGuard` |
| `/m/p3/notifications` | **OPEN** | notify jump; no `SurfaceGuard` |

Thin wrappers: `H5PageSurfaceCatalog` + JS `h5PageInventory.js`. The static gate parses `web/src/router.js`. A new `/m/*` route without a catalog row fails. A fake close-claim fixture fails. Covered Vue files must keep `useH5Surface`. Open Vue files must not claim it.

ArchUnit note: `H5SurfaceGuard` must still depend on `SurfaceGuard` (#116 twin; this leftover does not rewrite it).

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G2 | Open. `g2_close=NOT_CLAIMED`. |
| Putting SurfaceGuard on home / files / notify / login / host | Still open. This leftover only inventories them. |
| H5 `/m/*` list/detail/form/todo (#116) | Sibling leftover on `main`. Not rewritten. |
| Todo list HTTP (#150) | Sibling leftover. Not rewritten. |
| Isolation-only `tenant_only` / catalog (#144) | Sibling leftover. Not rewritten. |
| Physical dump HTTP (#137) | Sibling leftover. Not rewritten. |
| Ops `pg_dump` (#155) | Sibling leftover [g2-ops-pg-dump.md](./g2-ops-pg-dump.md). Row stays in `docs/README.md`. |
| AuditPort same-TX / hash-chain rewrite | Unchanged |
| G18 reopen | No |

## Evidence

```bash
./scripts/g2-h5-every-page.sh
```

Equivalent:

```bash
cd web && npm test -- src/mobile/permission/h5PageInventory.test.js && npm run assert:h5-every-page

mvn -pl nexus-permission-core,nexus-bootstrap -am test \
  -Dtest=H5PageSurfaceHonestyGateTest,G2H5PageHonestyTest,H5PageSurfaceHonestyArchTest \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -f nexus-permission-core/pom.xml -q exec:java@h5-page-honesty \
  -Dexec.args="--repo ."
```

See [engineering/g2-h5-every-page-EVIDENCE.md](./engineering/g2-h5-every-page-EVIDENCE.md). Desktop `/p3` unchanged. No live JAR + issued-context browser walk on this VM.
