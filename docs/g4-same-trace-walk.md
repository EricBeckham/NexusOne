# G4 remainder: one same-trace walk

S0+S1 PostgreSQL slice. Not an M0 redo. **Product G18 stays closed.** This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules.

Categories were already wired on `main` (#77 login / permission / admin / guard-reject, #86 accepted export). The leftover was **one real walk** that puts those categories on a single `trace_id` and shows they are findable on `AuditPort.findByTraceId` and `GET /api/v1/audit/traces/{traceId}`.

## What this close covers

One product IT (`G4SameTraceWalkIT`) on `NexusOneApplication` + Zonky PostgreSQL 15:

1. **Login** — stub IdP `POST /api/v1/auth/login` with `X-Trace-Id` (not browser Authorization Code + PKCE).
2. **Permission change** — `POST /api/v1/permission/grants`.
3. **Admin** — `PUT /api/v1/p1/tenants/{id}/config` (`p1.tenant.config.updated`) so the admin row shares the login tenant and is visible on GET (tenant-scoped). Tenant-create remains the #77 path; this walk keeps that create on a **linked** `SETUP_TRACE` fixture only.
4. **Export** — product `IsolatedExport` submit / writeFile / dispatch through the boot `IsolationKernel` + `AuditPortIsolationSink` (`AuditPort.append`). There is no HTTP export controller on `main`.
5. **Guard-reject** — cross-tenant `IsolatedExport.submit` inside a Spring TX that rolls back. Persist is `AuditPort.appendNewTransaction` (survival semantics already on `main`). `GUARD_REJECT` / `P1.M02.019` remains after rollback.

All five walk categories share **one** `WALK_TRACE`. `audits.verifyChain()` stays intact. GET returns the tenant-scoped subset (login / permission / admin-config / export / guard-reject).

This is **not** a headless browser PKCE walk. MockMvc + IsolatedExport is the product path that can run without Docker.

## Linked traces

| Trace | Role |
|---|---|
| `0199a1c0-0000-7000-8000-00000000c203` (`SETUP_TRACE`) | Fixture `POST /api/v1/p1/tenants` so the walk tenant exists in P1. Not claimed as the walk. |
| `0199a1c0-0000-7000-8000-00000000c204` (`WALK_TRACE`) | Login → permission → admin config → export → guard-reject |

## Evidence

Recorded **2026-09-22T16:43:41Z** on this agent VM. `./scripts/g4-same-trace-walk.sh` → **BUILD SUCCESS**. No Docker. No live `127.0.0.1:5432`. Stub IdP (not Keycloak PKCE).

```
./scripts/g4-same-trace-walk.sh
# Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS  Total time: 38.240 s  Finished at: 2026-09-22T16:43:41Z
```

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** (`starting PostgreSQL 15.18 on x86_64-pc-linux-gnu`) |
| Flyway | 16 migrations on schema `platform`, head **V550** |
| Walk trace | `0199a1c0-0000-7000-8000-00000000c204` |
| Linked fixture | `0199a1c0-0000-7000-8000-00000000c203` (`p1.tenant.created`) |
| Result | **1/1** |

| Suite | Result | What it proved |
|---|---|---|
| `G4SameTraceWalkIT` | **1/1** | One `WALK_TRACE` holds LOGIN / PERMISSION / ADMIN (`p1.tenant.config.updated`) / EXPORT (`p3.export.submitted` + file) / GUARD_REJECT (`P1.M02.019`); GET `/api/v1/audit/traces/{id}` returns those categories for the login tenant; `verifyChain()` intact; guard-reject row survives the surrounding TX rollback |

Log on this run (same `WALK_TRACE`): four `EXPORT_ACCEPTED` then `ISOLATION_REJECT code=P1.M02.019`.

## Honest leftover G4 rows

This is **not** a product-wide G4 close.

| Still open | Why |
|---|---|
| Browser Authorization Code + PKCE login | HTTP-driver + native Keycloak 26.x PKCE is [g4-pkce-login-audit.md](./g4-pkce-login-audit.md). HTTPS loopback leftover is [g4-pkce-https-browser.md](./g4-pkce-https-browser.md). Desktop Playwright leftover (stub IdP) is [g4-pkce-browser-click.md](./g4-pkce-browser-click.md). Human laptop + live Keycloak still needs to trust the local CA. |
| 365-day archive expiry | Closed on `main` via #90 (`purgeImplemented=true`). Not reopened here. |
| Hot purge after 90 days | Leftover implementation is [g4-hot-purge-90d.md](./g4-hot-purge-90d.md). This walk does not DELETE hot rows |
| Object-store / cold archive | Same-PG table only |
| HTTP export wizard / report UI | `ledger.export.csv` is the guarded surface, not a physical ledger dump |
| Switching P1 `GET /tenants/{id}/audit-events` off staging | Unchanged from #77 |
| G6 / G17 | Not this package |

G18 remains closed. Same-TX `append` joining is unchanged.
