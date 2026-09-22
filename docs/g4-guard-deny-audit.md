# G4 leftover: SurfaceGuard deny / permission-deny on the hash chain

S0+S1 PostgreSQL slice. Not an M0 redo. **Product G18 stays closed.** This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules and does **not** claim a product-wide G4 close.

Login / permission-change / admin / accepted export / isolation `P1.M02.019` are already on `main` (#77 / #86 / #92). Desktop Playwright PKCE click is on `main` via #125 ([g4-pkce-browser-click.md](./g4-pkce-browser-click.md)). Orthogonal to that leftover.

The gap here: product `FormSurfaceGuard` / `H5SurfaceGuard` / `ReportSurfaceGuard` / `PermissionDataScopeHook` / flow + submit-start / Job+Task `AuthorizedSubmit` still called `SurfaceGuard.require(service, resource, action)` and **threw without writing** `p2.permission.denied` + `isolation.guard.reject`.

## What this slice covers

Reuse the existing `PermissionModule` `AuditPort` (same port already used for grant/assign). Deny uses **`appendNewTransaction`** so a surrounding business rollback cannot erase the reject. `append` joining is unchanged.

| Surface | Deny cell | On chain |
|---|---|---|
| Form list / fill / studio | `form` / `read` or `write` | `p2.permission.denied` + `isolation.guard.reject` |
| H5 `/m/*` twin | same desktop cells | same pair |
| Report query | `ledger` / `read` | same pair |
| Report export / Job / Task | `ledger.export.csv` / `submit` or `job`/`task` `submit` | same pair |
| List/detail hook + flow + submit-start | existing `SurfaceGuard` cells | same pair |
| `GET /api/v1/p3/reports/decision` | decide only | **does not write** |

Isolation-only hosts with no P2 `TenantContext` stay `tenant_only` and still skip the engine (same leftover as G2). `emitDenied` no-ops when `AuditPort` is null.

`JdbcHashChainedAuditPort` itself is not redesigned.

## Evidence

Recorded **pending-this-run** on this agent VM. `./scripts/g4-guard-deny-audit.sh` → **pending**. No Docker. No live `127.0.0.1:5432`. Stub IdP (not Keycloak). Orthogonal to #125 browser PKCE click.

| Item | Value |
|---|---|
| JDK | OpenJDK **21** |
| Maven | **3.8.x** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** |

```
./scripts/g4-guard-deny-audit.sh
# pending
```

| Suite | Result | What it proved |
|---|---|---|
| `G4SurfaceGuardDenyAuditTest` | pending | `SurfaceGuard` / `SurfaceCatalog` / `AuthorizedSubmit` deny writes both event types; silent overload writes nothing; isolation-only writes nothing; chain intact |
| `FormSurfaceGuardTest` / `H5SurfaceGuardTest` / `ReportSurfaceGuardTest` | pending | Wrapper deny emits; report `decide` does not |
| `G4GuardDenyAuditIT` | pending | Product `NexusOneApplication` + Zonky PG 15: HTTP form list + report query/export 403 land JDBC rows; `GET /decision` writes 0; deny inside a rolled-back Spring TX survives; `verifyChain()` intact |
| `G4AuditCoverageIT` / `JdbcAuditChainTransactionTest` | pending | Existing login/permission/admin/isolation-reject + same-TX `append` rules unchanged |

## Honest leftover G4 rows

This is **not** a product-wide G4 close.

| Still open | Why |
|---|---|
| Human laptop + live Keycloak CA / SPA-generated verifier | #125 is stub-IdP Playwright. HTTP-driver + HTTPS loopback remain on `main`. |
| HTTP export wizard / report UI | Leftover implementation is [g4-export-wizard.md](./g4-export-wizard.md). Not a product export UI |
| Switching P1 `GET /tenants/{id}/audit-events` off staging | Unchanged from #77 |
| Isolation-only `tenant_only` skip | No P2 context still skips this engine (G2 leftover) |
| Callers that still use `SurfaceGuard.require(service, resource, action)` without `AuditPort` | Tests / fixtures / silent overload. Product wrappers now pass the module port. |
| G6 `RecoveryPort` / G17 classified | Not this package |

G18 remains closed. Same-TX `append` joining is unchanged.
