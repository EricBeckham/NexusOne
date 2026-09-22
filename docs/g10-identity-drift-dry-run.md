# G10 leftover — Keycloak 8080 identity drift-fix dry-run

S0+S1 remainder after the identity half ([g10-identity-reconcile.md](./g10-identity-reconcile.md) / #76). **G18 stays closed.** This slice does **not** redo file metadata ↔ object reconcile ([g10-file-reconcile.md](./g10-file-reconcile.md)). It does **not** claim SAML, LDAP / Active Directory, or SMS MFA.

#76 already matches P2 persons to Keycloak users and can `POST {"apply":true}` to repair. Preview (`GET` / `apply=false`) used to report **findings only** — the `repairs` list stayed empty until a write ran. The leftover here is **detect + proposed fix** against Keycloak **26.5.2** on loopback **:8080**, with **no P2 / IdP writes by default**.

| Call | Who | Effect |
|---|---|---|
| `GET /api/v1/internal/identity/reconcile` | `X-Nexus-Bootstrap-Token` | Platform detect. `applied=false`, `repaired=0`, `proposed` + `repairs` list the would-do actions |
| `GET /api/v1/identity/reconcile` | Bearer + `X-Nexus-Context-Id` | Current-tenant detect + proposed fix. No writes |
| `POST …/identity/reconcile` without `apply:true` | same | Same as GET |
| `POST {"apply":true}` | existing G10 apply | Writes stay on the original identity half. **Not** this leftover script |

`path` remains `identity.reconcile`. Client `X-Tenant-Id` is still rejected.

## Proposed actions (dry-run)

| Finding | Proposed `repairs[].action` | Default dry-run |
|---|---|---|
| `DRIFT` | `SYNC_P2` | Person / account email stays stale |
| `STALE_BINDING` | `REBIND` | `keycloak_user_id` stays stale |
| `MISSING_IDP` | `PROVISION_IDP` | `ensureUser` is **not** called |
| `UNMATCHED_IDP` | none | P2 stays org master; no person is created |
| `CONFLICT` | none | No rebind |

`repaired` is the count of writes that actually ran. On dry-run it is always `0`. `proposed` is `repairs.size()`.

Keycloak internal tables are never read. Admin API `GET /users` only. No Docker.

## How to run

```bash
# Always-green Stub IdP (no Keycloak). Default apply is refused.
./scripts/g10-identity-drift-dry-run.sh

# Live Keycloak 26.5.2 on :8080 (native kc.sh, no Docker)
# KEYCLOAK_HOME defaults to $HOME/keycloak-26.5.2
# Secrets: env or /tmp/keycloak-smoke.env (0600, not git)
export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_KEYCLOAK_ISSUER=http://127.0.0.1:8080/realms/nexus
./scripts/g10-identity-drift-dry-run.sh
```

`NEXUS_G10_APPLY=1` is **refused** (exit 2). Destructive apply stays:

```bash
./nexus-p2-iam/scripts/run-g10-identity-reconcile.sh
```

`start-native-keycloak.sh` uses PostgreSQL when `127.0.0.1:5432` is up, otherwise Keycloak `start-dev` bundled **H2**. `--hostname-port` is not a 26.5.2 flag.

## Evidence

See [engineering/g10-identity-drift-dry-run-EVIDENCE.md](./engineering/g10-identity-drift-dry-run-EVIDENCE.md). Live output is gitignored under `.local/g10-identity-drift-dry-run/`.

## Honest leftovers (not claimed)

| Still open | Why |
|---|---|
| SAML | Not implemented. Not walked. |
| LDAP / Active Directory federation | Not implemented. Not walked. |
| SMS MFA | Not implemented. `amr=sms` is only recognized if an IdP sent it. |
| Product G10 close | Identity apply + file half already exist. This leftover is dry-run evidence only. |
| File metadata ↔ object store | Other G10 half. Not redone. |
| Keycloak Organizations / Employment sync | P2 remains org master. Unmatched IdP users stay report-only. |
| Browser Authorization Code + PKCE / human laptop + live CA | G4 leftovers. Not this slice. |
| Keycloak upgrade / rollback matrix | 26.5.2 only. |
| Docker / Compose | Forbidden. Native `kc.sh` only. |
| G18 reopen | **No.** |

Same-TX `AuditPort` joining is unchanged. Default authorize URL still has no `code_challenge`.
