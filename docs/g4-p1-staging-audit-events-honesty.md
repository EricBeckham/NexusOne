# G4 leftover — P1 staging `audit-events` honesty beyond #156

S0+S1 remainder after the #156 leftover ([g4-p1-staging-audit-events.md](./g4-p1-staging-audit-events.md)). Rebased onto `origin/main` `514c9b7` (after #194 S0+S1 claim-list tip-sync / #186 RecoveryPort dry-run leftover honesty / #185 live Redis/MinIO isolation leftover honesty). **G18 stays closed.** This leftover does **not** redo #147 SPA PKCE, #152 export wizard, #166 export-wizard-deny, or #156 staging harness. It does **not** switch `GET /tenants/{id}/audit-events` off staging. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim product G4 close. `g4_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g4-p1-staging-audit-events` (#156), `g6-recovery-port-dry-run-honesty` (#186), `s0s1-live-redis-minio-isolation-honesty` (#185), `g4-hot-purge-90d-honesty` (#183), `g17-separation-honesty` (#187), `g4-export-wizard` (#152), `g4-export-wizard-deny` (#166), `g4-pkce-spa-verifier` (#147), `g4-pkce-spa-verifier-honesty` (#177), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `s0-secret-getenv-snapshot-honesty` (#180), `g5-browser-failed-publish` (#171), and `g1-fieldpolicy-mask-second-auth-honesty` (#184). Keep this file.

#156 already proves in-memory / Zonky staging-like GET + JSON export, and SKIPs live `:5432` when password / table are missing. The leftover here is the **honesty gate** beyond that: staging-like query/export stays **PASS**. Live `:5432` `platform.audit_event` is **PASS** only when the listener is up, credentials are present, **and** a query+export walk was observed. Missing live DB is honest **SKIP**. It never fabricates `PASS`. GET stays on staging.

## What this leftover covers

| Path | Behavior |
|---|---|
| Staging-like query/export | In-memory `AuditRepository` + `P1StagingAuditEventsExporter`. Always-green **PASS**. Not a redo of the #156 Zonky IT. |
| Product GET still on staging | `TenantController` still uses `audit.listByTenant` / `listByType`. `switched_off_staging=false`. |
| Live `:5432` | `live_staging_5432=PASS` only with listener + credential + observed query/export. Otherwise honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake live PASS, `g4_close=CLOSED`, `switched_off_staging=true`, and #147/#152/#156/#166 redo flags. |
| Leftover rows | `docs/README.md` keeps #156 and this honesty leftover plus all sibling leftover rows. |

This leftover does **not** start PostgreSQL. Default product GET is unchanged.

## How to run

```bash
# Always-green staging-like PASS + live :5432 probe (honest SKIP when DB is down)
./scripts/g4-p1-staging-audit-events-honesty.sh

# Honesty gate only
./scripts/g4-p1-staging-audit-events-honesty.sh --verify-report \
  scripts/g4-p1-staging-audit-events-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-p1-staging-audit-events-honesty.sh --verify-report \
  scripts/g4-p1-staging-audit-events-honesty/fixtures/fake-live-pass.env
./scripts/g4-p1-staging-audit-events-honesty.sh --verify-report \
  scripts/g4-p1-staging-audit-events-honesty/fixtures/fake-g4-close.env
./scripts/g4-p1-staging-audit-events-honesty.sh --verify-report \
  scripts/g4-p1-staging-audit-events-honesty/fixtures/fake-switched-off-staging.env
```

Always-green Java (does **not** redo #156 Zonky / export-wizard suites):

```bash
mvn -pl nexus-p1-tenant -am test \
  -Dtest=G4P1StagingAuditEventsHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #156 leftover stays the reproduction command for the original harness:

```bash
# do not run for this leftover
# ./scripts/g4-p1-staging-audit-events.sh
```

`NEXUS_G4_P1_STAGING_SKIP_LIVE=1` forces the live probe to SKIP.

See [engineering/g4-p1-staging-audit-events-honesty-EVIDENCE.md](./engineering/g4-p1-staging-audit-events-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g4-p1-staging-audit-events-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G4 close | Explicitly `g4_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Switching P1 GET off staging | `switched_off_staging=false`. Still open. |
| #156 staging harness | Sibling leftover on `main`. `redo_156=false`. |
| #152 / #166 export wizard | Sibling leftovers. `redo_152=false` / `redo_166=false`. |
| #147 SPA PKCE | Sibling leftover. `redo_147=false`. |
| Live `:5432` on this agent VM | Credential unset / nothing on `:5432`. Honesty SKIP. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / not redone. |
| Docker / Compose | Forbidden. Native PostgreSQL only when the operator already started it. |

## Honest leftovers (not claimed)

- Switching P1 `GET /tenants/{id}/audit-events` off staging onto M0-11
- Live `:5432` `platform.audit_event` query+export on a host that actually has that database
- Product export UI / report BI
- Human laptop + live Keycloak CA / SPA verifier
- Product G4 close
