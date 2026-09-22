# G10 leftover — identity drift-fix apply / dry-run honesty beyond #141

S0+S1 remainder after the identity dry-run leftover ([g10-identity-drift-dry-run.md](./g10-identity-drift-dry-run.md) / #141). Rebased onto `origin/main` `25b7d85` (after #181 G15 native-bridge honesty; includes #177/#180/#171). **G18 stays closed.** This slice does **not** redo the identity apply product path ([g10-identity-reconcile.md](./g10-identity-reconcile.md)), does **not** redo #141 or #169, and does **not** claim product G10 / SAML / LDAP / SMS MFA.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g10-identity-drift-dry-run` (#141), `g10-file-reconcile-honesty` (#169), `g5-browser-failed-publish` (#171), `s0-secret-getenv-snapshot-honesty` (#180), `g4-pkce-spa-verifier-honesty` (#177), and `g15-native-bridge-honesty` (#181). Keep this file.

#141 already lists proposed `repairs` on preview (`GET` / `apply=false`) and refuses `NEXUS_G10_APPLY=1`. The leftover here is the **honesty harness**: live Keycloak **8080** dry-run is **PASS** only when the issuer is up **and** a dry-run walk was observed. Missing listener / OIDC discovery is honest **SKIP**. It never fabricates `PASS`. Live apply stays **SKIP** — apply remains on `nexus-p2-iam/scripts/run-g10-identity-reconcile.sh`. This leftover does not start Keycloak.

## What this leftover covers

| Path | Behavior |
|---|---|
| Dry-run default | Detect + proposed fix. `dry_run_default_no_write=true`. `NEXUS_G10_APPLY=1` is refused (exit 2). |
| Live Keycloak 8080 | Probe loopback `:8080` + `NEXUS_KEYCLOAK_ISSUER` OIDC discovery (default `http://127.0.0.1:8080/realms/nexus`). |
| Honest SKIP | Down listener, missing discovery, or no observed dry-run walk stays `SKIP` with a reason. |
| Live apply | Always `SKIP`. Product apply is not redone. |
| Honesty gate | `--verify-report` rejects `live_keycloak_8080=PASS` without present + observed dry-run, rejects `live_apply=PASS`, rejects `g10_close=CLOSED`, and rejects #141 / #169 / apply / SAML / LDAP / SMS MFA claims. |

`path` remains `identity.reconcile`. Client `X-Tenant-Id` is still rejected. No Docker.

## How to run

```bash
# Always-green honesty probe (honest SKIP when Keycloak 8080 is down)
./scripts/g10-identity-drift-honesty.sh

# Honesty gate only
./scripts/g10-identity-drift-honesty.sh --verify-report \
  scripts/g10-identity-drift-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-identity-drift-honesty.sh --verify-report \
  scripts/g10-identity-drift-honesty/fixtures/fake-live-keycloak-pass.env
./scripts/g10-identity-drift-honesty.sh --verify-report \
  scripts/g10-identity-drift-honesty/fixtures/fake-live-apply-pass.env
./scripts/g10-identity-drift-honesty.sh --verify-report \
  scripts/g10-identity-drift-honesty/fixtures/fake-g10-close.env
```

Equivalent tests:

```bash
mvn -pl nexus-p2-iam -am test \
  -Dtest=G10IdentityDriftHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Sibling leftovers stay as reproduction commands. This leftover does **not** invoke them:

```bash
# do not run for this leftover
# ./scripts/g10-identity-drift-dry-run.sh
# ./nexus-p2-iam/scripts/run-g10-identity-reconcile.sh
# ./scripts/g10-file-reconcile-honesty.sh
```

`PASS` for live Keycloak 8080 requires all of: listener on `:8080`, OIDC discovery, and an observed dry-run walk (`applied=false`, `repaired=0`). This leftover does not start Keycloak and does not call apply.

See [engineering/g10-identity-drift-honesty-EVIDENCE.md](./engineering/g10-identity-drift-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g10-identity-drift-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G10 close | Explicitly `g10_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Identity apply product path | `identity_apply_product_redone=false`. Apply stays on [g10-identity-reconcile.md](./g10-identity-reconcile.md). |
| #141 drift-fix dry-run | Sibling leftover. `identity_drift_141_redone=false`. |
| #169 file reconcile honesty | Sibling leftover. `file_reconcile_169_redone=false`. Keep that README row. |
| SAML / LDAP / SMS MFA | Explicitly `*_claimed=false`. Not implemented. |
| Live Keycloak 8080 on this agent VM | Nothing on `:8080`. Honesty SKIP. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Docker / Compose | Forbidden. Native `kc.sh` only when the operator already started it. |

## Honest leftovers (not claimed)

- Live Keycloak 26.5.2 `:8080` detect + proposed-fix dry-run on a host that actually has that process
- Product identity apply walk (`POST {"apply":true}`)
- Product G10 close
- SAML / LDAP / Active Directory / SMS MFA / Keycloak Organizations
- File metadata ↔ object store (#169 leftover stays sibling)
