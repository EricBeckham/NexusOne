# G4 leftover — export-wizard deny / guard-deny honesty beyond #166/#136

S0+S1 remainder after the #166 HTTP export-wizard complete-deny leftover ([g4-export-wizard-deny.md](./g4-export-wizard-deny.md)) and the #136 SurfaceGuard deny-on-chain leftover ([g4-guard-deny-audit.md](./g4-guard-deny-audit.md)). Rebased onto `origin/main` `5109245` (Merge #196 G5 mid-publish/browser leftover honesty; also #195 at `c8cd853`, #191 at `fb333a3`, #198 at `724f5f0`, #200 at `f8e5f15`). **G18 stays closed.** This leftover does **not** redo #136 / #152 / #166 / #190 product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim product G4 close. `g4_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g4-export-wizard-deny` (#166), `g4-guard-deny-audit` (#136), `g4-export-wizard` (#152), `g4-p1-staging-audit-events` (#156), `g4-p1-staging-audit-events-honesty` (#190), `g4-hot-purge-90d` (#123), `g4-hot-purge-90d-honesty` (#183), `g4-pkce-spa-verifier` (#147), `g4-pkce-spa-verifier-honesty` (#177), `s0-secret-rotate-without-restart` (#143), `s0-secret-getenv-snapshot-honesty` (#180), `s0-secret-rotate-without-restart-honesty` (#200), `s0s1-live-redis-minio-isolation-honesty` (#185), `g6-recovery-port-dry-run-honesty` (#186), `g17-separation-honesty` (#187), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g15-wx-dt-prod-gap-honesty` (#167), `g15-wx-dt-native-bridge-honesty` (#198), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g1-queryast-boundsql-guard` (#146), `g1-queryast-boundsql-honesty` (#191), `g3-outbox-consume-crash-honesty` (#195), `g5-browser-failed-publish` (#171), `g5-mid-publish-failure-honesty` (#196), `g7-query-expense-travel-honesty` (#192), and `linux-arm-profile-leftover-honesty` (#193). Keep this file.

#166 already proves in-memory / HTTP / Zonky complete-deny after begin writes the #136 pair and skips IsolatedExport / `p3.export.completed`. #136 already proves Form/H5/报表/Job/Task `SurfaceGuard` deny writes `p2.permission.denied` + `isolation.guard.reject` via `appendNewTransaction`. The leftover here is the **honesty gate** beyond both: those harnesses stay **PASS**. Live product JAR / Playwright browser / `:5432` JDBC / Keycloak issued-context is **PASS** only when the listener is up **and** the walk was observed. Missing live path is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #136 SurfaceGuard deny harness | Thin in-memory `SurfaceGuard.require` with no grant writes the deny pair. Always-green **PASS**. Not a redo of `G4SurfaceGuardDenyAuditTest` / `G4GuardDenyAuditIT`. |
| #166 export-wizard complete-deny harness | Thin in-memory begin then role downgrade: complete writes the #136 pair; no IsolatedExport / `p3.export.completed`. Always-green **PASS**. Not a redo of `G4ExportWizardDenyAuditTest` / `G4ExportWizardDenyIT`. |
| Same-TX `append` | Source scan: reject still uses `appendNewTransaction`; accepted wizard still uses `append`. `same_tx_append_unchanged=PASS`. |
| Live product JAR | `live_jar_wizard_deny=PASS` only with `:8080` **and** `live_wizard_deny_observed=true`. Missing JAR / unobserved walk is honest **SKIP**. Zonky is not a live JAR PASS. |
| Live browser wizard | `live_browser_wizard=PASS` only when a Playwright / issued-context `/p3/exports/wizard` deny click was observed. Otherwise honest **SKIP**. |
| Live `:5432` JDBC | `live_5432_jdbc=PASS` only with listener **and** an observed JDBC deny pair. Zonky #136/#166 is **not** a live walk. |
| Live Keycloak | `live_keycloak_idp=PASS` only when issued-context was observed. Stub IdP / #125 click is **not** a live Keycloak PASS. |
| Honesty gate | `--verify-report` rejects fake live PASS, `g4_close=CLOSED`, and #136/#152/#166/#190 redo flags. Requires leftover rows kept. |
| Leftover rows | `docs/README.md` keeps #136 / #166 and this honesty leftover plus **ALL** sibling leftover rows. |

This leftover does **not** start PostgreSQL, Keycloak, or a product JAR. Default product `AuditPort` same-TX joining is unchanged.

## How to run

```bash
# Always-green harness PASS + live probes (honest SKIP when unavailable)
./scripts/g4-export-wizard-deny-honesty.sh

# Honesty gate only
./scripts/g4-export-wizard-deny-honesty.sh --verify-report \
  scripts/g4-export-wizard-deny-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-export-wizard-deny-honesty.sh --verify-report \
  scripts/g4-export-wizard-deny-honesty/fixtures/fake-live-pass.env
./scripts/g4-export-wizard-deny-honesty.sh --verify-report \
  scripts/g4-export-wizard-deny-honesty/fixtures/fake-g4-close.env
./scripts/g4-export-wizard-deny-honesty.sh --verify-report \
  scripts/g4-export-wizard-deny-honesty/fixtures/fake-redo.env
```

Always-green Java (does **not** redo #136 / #152 / #166 / #190 suites):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G4ExportWizardDenyHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #136 / #166 leftovers stay the reproduction commands for the original harnesses:

```bash
# do not run for this leftover
# ./scripts/g4-guard-deny-audit.sh
# ./scripts/g4-export-wizard-deny.sh
```

`NEXUS_G4_EXPORT_WIZARD_DENY_SKIP_LIVE=1` forces the live probes to SKIP.

See [engineering/g4-export-wizard-deny-honesty-EVIDENCE.md](./engineering/g4-export-wizard-deny-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g4-export-wizard-deny-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G4 close | Explicitly `g4_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| #136 SurfaceGuard deny leftover | Sibling leftover on `main`. `redo_136=false`. |
| #152 HTTP export wizard | Sibling leftover. `redo_152=false`. |
| #166 complete-deny leftover | Sibling leftover. `redo_166=false`. |
| #190 P1 staging honesty | Sibling leftover. `redo_190=false`. |
| Live JAR / browser / `:5432` / Keycloak on this agent VM | Listeners missing or walk not observed. Honesty SKIP. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / not redone. |
| Docker / Compose | Forbidden. Native PostgreSQL / JAR only when the operator already started them. |

## Honest leftovers (not claimed)

- Live product JAR HTTP begin-then-complete-deny with issued context
- Playwright click of `/p3/exports/wizard` deny
- Live `:5432` JDBC deny pair + `verifyChain()`
- Live Keycloak issued-context for the wizard
- Switching P1 `GET /tenants/{id}/audit-events` off staging
- Product export UI / report BI / product G4 close
