# G4 leftover: HTTP export wizard begin / complete on the hash chain

S0+S1 PostgreSQL slice. Not an M0 redo. **Product G18 stays closed.** This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules and does **not** claim a product-wide G4 close.

Login / permission-change / admin / accepted IsolatedExport / isolation `P1.M02.019` are already on `main` (#77 / #86 / #92). Desktop Playwright PKCE click is on `main` via #125 ([g4-pkce-browser-click.md](./g4-pkce-browser-click.md)). SurfaceGuard deny is on `main` via #136 ([g4-guard-deny-audit.md](./g4-guard-deny-audit.md)). Orthogonal leftovers. Keep both leftover rows.

The gap here: report / dump HTTP already guards `ledger.export.csv`/`submit`, but there was no **wizard** path that writes export **begin** then **complete** (or deny) on the same hash chain. Same-trace walk still called IsolatedExport in-process.

## What this slice covers

Reuse `ReportSurfaceGuard` + `AuthorizedExport`. Begin / complete use **`append`** (same-TX join). Deny reuses the #136 `appendNewTransaction` pair. `JdbcHashChainedAuditPort` is not redesigned.

| Surface | Cell | On chain |
|---|---|---|
| `GET /api/v1/p3/exports/wizard/decision` | `ledger.export.csv` / `submit` decide | **does not write** |
| `POST /api/v1/p3/exports/wizard/begin` | same cell via `ReportSurfaceGuard` | `p3.export.begun` (`EXPORT` / `append`) |
| `POST /api/v1/p3/exports/wizard/complete` | same cell then `AuthorizedExport.submit` | `p3.export.submitted` + `p3.export.completed` |
| Deny on begin / complete | `no_grant` / `explicit_deny` / `tenant_boundary` | `p2.permission.denied` + `isolation.guard.reject` |

Not a product export UI / BI / physical dump. Isolation-only hosts with no P2 `TenantContext` stay `tenant_only` and skip wizard audit writes.

## Evidence

Primary evidence is MockMvc / HTTP (Zonky PG 15). Browser wizard is an honest **SKIP** when Playwright / Chrome cannot run.

```
./scripts/g4-export-wizard.sh
# pending-this-run
```

| Suite | Result | What it proved |
|---|---|---|
| `G4ExportWizardAuditTest` | pending | begin + complete on one in-memory chain; deny writes #136 pair; isolation-only writes 0 |
| `ExportWizardSessionsTest` | pending | tenant match / 404 / 409 replay |
| `ExportWizardApiTest` | pending | HTTP 403 before IsolatedExport; decision writes 0; dept_manager begin+complete |
| `G4ExportWizardIT` | pending | Product `NexusOneApplication` + Zonky PG 15: deny 403 + JDBC rows; grant then begin+complete on `c259`; GET `/audit/traces/{id}` returns EXPORT; `verifyChain()` intact |
| `JdbcAuditChainTransactionTest` | pending | Same-TX `append` rules unchanged |
| Browser wizard | **SKIP** | No live JAR + issued-context Playwright walk on this VM |

## Honest leftover G4 rows

This is **not** a product-wide G4 close.

| Still open | Why |
|---|---|
| Human laptop + live Keycloak CA / SPA-generated verifier | #125 is stub-IdP Playwright. HTTP-driver + HTTPS loopback remain on `main`. |
| Switching P1 `GET /tenants/{id}/audit-events` off staging | Honesty leftover is [g4-p1-staging-audit-events.md](./g4-p1-staging-audit-events.md). GET still reads staging. |
| Isolation-only `tenant_only` skip | No P2 context still skips wizard audit writes (G2 leftover) |
| Full product export UI / report BI / physical dump | Narrow HTTP begin/complete only |
| Browser click of `/p3/exports/wizard` | Honest SKIP unless Playwright + issued context are present |
| G6 `RecoveryPort` / G17 classified | Not this package |

G18 remains closed. Same-TX `append` joining is unchanged. Desktop PKCE leftover (#125) and SurfaceGuard deny leftover (#136) stay documented.
