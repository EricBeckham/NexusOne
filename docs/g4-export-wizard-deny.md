# G4 leftover: HTTP export wizard complete-deny on the hash chain

S0+S1 PostgreSQL slice after #152. Not an M0 redo. **Product G18 stays closed.** This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules and does **not** claim a product-wide G4 close.

#152 already landed begin / complete (and begin-deny) on the hash chain ([g4-export-wizard.md](./g4-export-wizard.md)). #156 P1 staging audit-events and #147 live Keycloak CA / SPA verifier are already on `main`. This leftover is the still-open **complete-deny after begin** remainder: revoke / `DENY` after a successful begin must 403, write the #136 pair, and skip IsolatedExport / `p3.export.completed`.

Keep leftover rows in [docs/README.md](./README.md): `g4-export-wizard`, `g4-p1-staging-audit-events`, `g17-vue-otp-writeback`, `g17-gm-tls-nginx-handshake`, `g9-live-multi-jvm-replica`, `g6-recovery-port-honesty`, `g1-fieldpolicy-mask-second-auth`. Follow-on honest PASS/SKIP leftover (does **not** redo this leftover): [g4-export-wizard-deny-honesty.md](./g4-export-wizard-deny-honesty.md).

## What this slice covers

Reuse `ReportSurfaceGuard` + `AuthorizedExport`. Complete deny uses the existing #136 `appendNewTransaction` pair. Begin / complete accepted writes still use `append` (same-TX join). `JdbcHashChainedAuditPort` is not redesigned.

| Surface | Cell | On chain |
|---|---|---|
| `POST /api/v1/p3/exports/wizard/begin` then grant revoked | `ledger.export.csv` / `submit` | `p3.export.begun` stays; complete does **not** write |
| `POST /api/v1/p3/exports/wizard/complete` after revoke / `DENY` | same cell | `p2.permission.denied` + `isolation.guard.reject` |
| IsolatedExport submit / `p3.export.completed` | — | **must not write** on complete-deny |
| `GET /api/v1/p3/exports/wizard/decision` | decide only | **does not write** |

Not a product export UI / BI / physical dump. Isolation-only hosts with no P2 `TenantContext` stay `tenant_only` and skip wizard audit writes.

## Evidence

Primary evidence is MockMvc / HTTP (Zonky PG 15). Browser wizard remains an honest **SKIP** when Playwright / Chrome cannot run.

```
./scripts/g4-export-wizard-deny.sh
# pending-this-run
```

| Suite | Result | What it proved |
|---|---|---|
| `G4ExportWizardDenyAuditTest` | pending | begin then role downgrade: complete writes #136 pair; no IsolatedExport / `p3.export.completed` |
| `ExportWizardApiTest` complete-deny | pending | HTTP begin then `SELF_READER` complete 403; begun stays; deny pair; no completed |
| `G4ExportWizardDenyIT` | pending | Product `NexusOneApplication` + Zonky PG 15: GRANT → begin → DENY → complete 403 `P2.PERM.0002`; JDBC deny pair; no `p3.export.completed` / submit; GET `/decision` writes 0; `verifyChain()` intact |
| `JdbcAuditChainTransactionTest` | pending | Same-TX `append` rules unchanged |
| Vue HTTP deny | pending | `g4-export-wizard-error` surfaces `P2.PERM.0003` / `0002` |
| Browser wizard | **SKIP** | No live JAR + issued-context Playwright walk on this VM |

## Honest leftover G4 rows

This is **not** a product-wide G4 close.

| Still open | Why |
|---|---|
| Switching P1 `GET /tenants/{id}/audit-events` off staging | Honesty leftover is [g4-p1-staging-audit-events.md](./g4-p1-staging-audit-events.md). GET still reads staging. |
| Human laptop + live Keycloak CA / SPA-generated verifier | Evidence path is [g4-pkce-spa-verifier.md](./g4-pkce-spa-verifier.md). Live Keycloak SKIP stays honest. |
| Isolation-only `tenant_only` skip | No P2 context still skips wizard audit writes (G2 leftover) |
| Full product export UI / report BI / physical dump | Narrow HTTP complete-deny only |
| Browser click of `/p3/exports/wizard` deny | Honest SKIP unless Playwright + issued context are present |
| G6 `RecoveryPort` / G17 classified | Not this package |

G18 remains closed. Same-TX `append` joining is unchanged. #152 begin/complete leftover, #156 P1 staging leftover, #147 SPA verifier leftover, #158 Vue otp write-back leftover, and #162 FieldPolicy mask leftover stay documented.
