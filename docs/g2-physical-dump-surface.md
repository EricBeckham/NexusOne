# G2 leftover — physical ledger dump / raw export on PermissionEngine

S0+S1 leftover from [g2-permission.md](./g2-permission.md): physical dump / raw export were not on this engine. Report query/export is already on `main` via #124 ([g2-report-surface-guard.md](./g2-report-surface-guard.md)). This package wires the leftover dump HTTP through the **same** `PermissionService.decide` `Decision` as desktop list / AuthorizedExport (`SurfaceGuard` / `POST /api/v1/permission/check`). **This is not a product-wide G2 close.** Desktop ledger / form / todo / report pages are unchanged. **G18 stays closed.** AuditPort is unchanged.

Reuse only: M0-05 `PermissionEngine` and the existing list (`ledger` / `read`) plus AuthorizedExport (`ledger.export.csv` / `submit`) cells. Dump uses `AuthorizedExport.writeProjected` (raw CSV). Report export stays Task `AuthorizedExport.submit`. Java 21 / Boot **4.1.1**. No Docker. Orthogonal to G15 WeChat/DingTalk stub (#132), G4 PKCE click (#125), G4 SurfaceGuard deny audit (#136), G6 RecoveryPort dry-run (#135), G1 QueryAst product opt-in (#130), G1 export FieldPolicy drop (#134), S0+S1 claim-list tip align (#138), and G7 expense/travel query (#140). Dump `require` reuses the #136 `SurfaceGuard.require(PermissionModule)` path; AuditPort same-TX is unchanged.

## What this package adds

| Surface | Entry | Cell | Denied (`no_grant` / `explicit_deny` / `tenant_boundary`) |
|---|---|---|---|
| Dump decision | `GET /api/v1/p3/dumps/decision` | both cells, no throw | returns `{read, dump, engine, physical_dump}` |
| Physical dump / raw export | `POST /api/v1/p3/dumps/export` | `ledger.export.csv` / `submit` then `ledger` / `read` | 403 `P2.PERM.0003` / `0002` / `0001` **before** ledger list / `writeProjected` |
| Desktop dump UI | `/p3/dumps` | same cells via `perm.check` then the HTTP above | 403 shown as desktop codes |
| Bypass gate | `SurfaceGuardBypassGate` | `/dumps` + IsolatedExport sinks | unguarded dump / raw `writeProjected` fail CI |

Vue interprets `!allowed` the same way as Java `SurfaceGuard.requireAllowed`. In-process twin is `DumpSurfaceGuard` (same isolation-only `tenant_only` skip as `FormSurfaceGuard` / `H5SurfaceGuard` / `ReportSurfaceGuard` when no P2 `TenantContext`).

`g2_self_reader` can read and cannot dump. `g2_dept_manager` can read and dump. Hidden FieldPolicy cells are dropped from the CSV projection. Demo `tenant_member` seed is still ALL-scope bootstrap, not G2-complete.

Dump writes projected CSV through `AuthorizedExport.writeProjected`. It is **not** report Task submit and **not** `pg_dump` / RecoveryPort.

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G2 | Open. H5 every page, allowlist leftovers, and `pg_dump` ops stay off this close. |
| Desktop `/p3/ledgers` `/p3/forms` `/p3/todos` `/p3/reports` | Unchanged. Do not import the dump helper into those pages. |
| Report query/export | Closed on main by #124. This leftover does not redo it. |
| SurfaceGuard bypass CI | Closed on main by #122. This leftover only adds `/dumps` + dump fixtures. |
| H5 dump page | Not added. H5 `/m/p3/query` export stays AuthorizedExport Task submit. |
| `pg_dump` / RecoveryPort / G6 runbooks | Ops leftover. Not a product dump. |
| `TodoController` | Allowlisted leftover. Catalog / isolation-only `tenant_only` honesty is the sibling leftover [g2-tenant-only-catalog.md](./g2-tenant-only-catalog.md). |
| AuditPort same-TX / hash-chain rewrite | Unchanged |
| G18 reopen | No |

## Evidence

```bash
cd web
npm test -- src/permission/dumpGuard.test.js
npm run assert:g2-physical-dump
npm run assert:g2-report-surface

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2DumpSameEngineTest,DumpSurfaceGuardTest,G2DumpSurfaceAgreementTest,DumpSurfaceApiTest,SurfaceGuardBypassGateTest,G2ReportSameEngineTest,G2SameEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false

./scripts/g2-physical-dump-surface.sh
```

Recorded on this agent VM after rebase onto `origin/main` `6fce50d` (Merge #134). OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**, Node **v22.14.0**, npm **10.9.7**, Vitest **3.2.7**. JS: `dumpGuard.test.js` 5/5 PASS; `assert:g2-physical-dump` PASS; `assert:g2-report-surface` PASS. `mvn -pl … -am` is blocked by pre-existing G17 `DualStackTlsProbe` (`SignatureAndHashAlgorithm.ecdsa_secp256r1_sha256`) — not this leftover. Isolation-only hosts without P2 stay tenant-only. Desktop P3 ledger / form / todo / report views still do not import the dump helper. No live JAR + issued-context browser walk on this VM.
