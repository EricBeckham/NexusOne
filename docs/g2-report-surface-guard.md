# G2 leftover — report query / export on PermissionEngine

S0+S1 leftover from [g2-permission.md](./g2-permission.md): report UI / report HTTP were not on this engine. This package wires existing report-like query and export entry points through the **same** `PermissionService.decide` `Decision` as list/detail/flow (`SurfaceGuard` / `POST /api/v1/permission/check`). **This is not a product-wide G2 close.** Desktop ledger / form / todo pages are unchanged. **G18 stays closed.** AuditPort is unchanged. Honesty leftover beyond this page (report + dump SurfaceGuard harness PASS; live JAR honest SKIP) is [g2-report-physical-dump-honesty.md](./g2-report-physical-dump-honesty.md). This #124 leftover is not rewritten.

Reuse only: M0-05 `PermissionEngine` and the existing list (`ledger` / `read`) plus AuthorizedExport (`ledger.export.csv` / `submit`) cells. Java 21 / Boot **4.1.1**. No Docker. Orthogonal to G15 APP WebView (#114), G1 DataScope AST (#115), G9 failover (#121).

## What this package adds

| Surface | Entry | Cell | Denied (`no_grant` / `explicit_deny` / `tenant_boundary`) |
|---|---|---|---|
| Report decision | `GET /api/v1/p3/reports/decision` | both cells, no throw | returns `{query, export, engine}` |
| Report query | `POST /api/v1/p3/reports/query` | `ledger` / `read` | 403 `P2.PERM.0003` / `0002` / `0001` **before** ledger list |
| Report export | `POST /api/v1/p3/reports/export` | `ledger.export.csv` / `submit` | 403 (same codes) **before** `AuthorizedExport` / IsolatedExport sinks |
| Desktop report UI | `/p3/reports` | same cells via `perm.check` then the HTTP above | 403 shown as desktop codes |
| H5 query export | `/m/p3/query` export button | export cell after existing `ledger/read` query | 403 (same codes) **before** export HTTP |

Vue interprets `!allowed` the same way as Java `SurfaceGuard.requireAllowed`. In-process twin is `ReportSurfaceGuard` (same isolation-only `tenant_only` skip as `FormSurfaceGuard` / `H5SurfaceGuard` when no P2 `TenantContext`).

`g2_self_reader` can query and cannot export. `g2_dept_manager` can query and export. Demo `tenant_member` seed is still ALL-scope bootstrap, not G2-complete.

Export submits the existing `ledger.export.csv` Task type. It is **not** a physical ledger dump (`pg_dump` / callers that skip `AuthorizedExport`).

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G2 | Open. Physical ledger dump / raw export is the sibling leftover [g2-physical-dump-surface.md](./g2-physical-dump-surface.md). |
| Desktop `/p3/ledgers` `/p3/forms` `/p3/todos` | Unchanged. Do not import the report helper into those pages. |
| Full report / BI (datasets, charts, subscriptions) | Out of V1.1. This leftover is permission wiring only. |
| H5 list/detail/form/todo | Closed on main by #116 ([g2-h5-permission.md](./g2-h5-permission.md)). |
| Direct `IsolatedExport` / `QueryCompiler` callers | Still need product wiring. Isolation leftovers stay G1. |
| AuditPort same-TX / hash-chain rewrite | Unchanged |
| G18 reopen | No |

## Evidence

```bash
cd web
npm test -- src/permission/reportGuard.test.js
npm run assert:g2-report-surface
npm run assert:h5-permission
npm run assert:h5-runtime

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2ReportSameEngineTest,ReportSurfaceGuardTest,G2ReportSurfaceAgreementTest,ReportSurfaceApiTest,G2SameEngineTest,G2SurfaceAgreementTest,G2H5SameEngineTest,FormSurfaceGuardTest \
  -Dsurefire.failIfNoSpecifiedTests=false

./scripts/g2-report-surface-guard.sh
```

Recorded on this agent VM after the change (OpenJDK / Maven / Boot / Node versions filled from the test run). Isolation-only hosts without P2 stay tenant-only. Desktop P3 ledger / form / todo views still do not import the report helper. No live JAR + issued-context browser walk on this VM.
