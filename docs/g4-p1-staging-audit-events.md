# G4 leftover: P1 staging `audit-events` query / export honesty

S0+S1 PostgreSQL slice. Not an M0 redo. **Product G18 stays closed.** This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules and does **not** claim a product-wide G4 close.

HTTP export wizard begin/complete is already on `main` via #152 ([g4-export-wizard.md](./g4-export-wizard.md)). This leftover is **orthogonal**: it documents and harnesses the existing P1 staging read, it does **not** redo the wizard, and it does **not** switch `GET /tenants/{id}/audit-events` onto M0-11.

Keep both leftover rows in [docs/README.md](./README.md): `g4-export-wizard` and this file.

## What this slice covers

P1 `GET /api/v1/p1/tenants/{id}/audit-events` still reads staging `platform.audit_event` through `AuditRepository.listByTenant` / `listByType` (M0-03). Hash-chain lookup stays `AuditPort.findByTraceId` / `GET /api/v1/audit/traces/{traceId}`.

| Piece | Behaviour |
|---|---|
| Query path | Same GET: optional `event_type`, `limit` capped 1–200 |
| Export path | Harness writes a JSON bundle from the staging repository (not IsolatedExport, not the #152 wizard) |
| Staging-like evidence | In-memory repository + Zonky PG **15** product `NexusOneApplication` |
| Live `:5432` | Honest **SKIP** when password, connection, or `platform.audit_event` is missing |
| Honesty flags | `switched_off_staging=false`, `product_g4_closed=false`, `http_export_wizard=false` |

GET is **not** rewired. A staging-only INSERT is visible on GET and absent from the hash chain. A hash-chain-only `AuditPort.append` is visible on `findByTraceId` and absent from GET.

## How to run (no Docker)

```bash
./scripts/g4-p1-staging-audit-events.sh
```

Equivalent:

```bash
mvn -pl nexus-p1-tenant,nexus-audit-core,nexus-bootstrap -am test \
  -Dtest=P1StagingAuditEventsHonestyTest,P1StagingAuditEventsLiveSkipTest,G4P1StagingAuditEventsIT,JdbcAuditChainTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Java **21** / Boot **4.1.1** / Zonky PostgreSQL **15.x**. No Docker. Live `127.0.0.1:5432` is optional.

```bash
# optional live staging export; SKIP when password / table missing
export NEXUS_DB_PASSWORD=...   # env only
./scripts/g4-p1-staging-audit-events.sh
```

`NEXUS_G4_P1_STAGING_SKIP_LIVE=1` forces the live probe to SKIP.

See [engineering/g4-p1-staging-audit-events-EVIDENCE.md](./engineering/g4-p1-staging-audit-events-EVIDENCE.md). Live output is gitignored under `.local/g4-p1-staging-audit-events/`.

## Honest leftover G4 rows

This is **not** a product-wide G4 close.

| Still open | Why |
|---|---|
| Switching P1 `GET /tenants/{id}/audit-events` off staging | This leftover only shows the current staging query/export path |
| Human laptop + live Keycloak CA / SPA-generated verifier | #125 is stub-IdP Playwright. HTTP-driver + HTTPS loopback remain on `main` |
| Product export UI / report BI | #152 is HTTP begin/complete, not a product wizard UI. Not redone here |
| Live `:5432` staging when credentials / table are missing | Honest SKIP |
| G6 `RecoveryPort` / G17 classified | Not this package |

G18 remains closed. Same-TX `append` joining is unchanged. HTTP export wizard leftover (#152) stays documented.
