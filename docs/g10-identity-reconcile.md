# G10 identity half: P2 persons ↔ Keycloak

S0+S1 gate **G10 身份与文件对账** is split. This package is the **identity half only**: P2 personnel match Keycloak users, and inconsistencies can be repaired. File metadata ↔ object store is [g10-file-reconcile.md](./g10-file-reconcile.md).

Reuse: M0-04 `nexus-p2-iam` (Boot **4.1.1**, Java **21**, PostgreSQL **15**, Keycloak **26.5.2**). P0/M0 login, org, and session paths are not rewritten.

Product Keycloak issuer remains `http://127.0.0.1:8080/realms/nexus`.

## Path

| Call | Who | Effect |
|---|---|---|
| `GET /api/v1/internal/identity/reconcile` | `X-Nexus-Bootstrap-Token` | Platform preview (match, no writes) |
| `POST /api/v1/internal/identity/reconcile` `{"apply":true}` | bootstrap token | Platform repair |
| `GET /api/v1/identity/reconcile` | Bearer + `X-Nexus-Context-Id` | Current-tenant preview |
| `POST /api/v1/identity/reconcile` `{"apply":true}` | issued context | Current-tenant repair |

`path` on the report is `identity.reconcile`. Client `X-Tenant-Id` is still rejected.

Match order: Keycloak `sub` on `iam_global_account.keycloak_user_id`, then email. Repair:

| Finding | Repair |
|---|---|
| `MATCHED` | none |
| `DRIFT` | `SYNC_P2` — account + person **email/phone** from Keycloak; tenant `displayName` is kept |
| `STALE_BINDING` | `REBIND` — set `keycloak_user_id` from the email match, then sync |
| `MISSING_IDP` | `PROVISION_IDP` — `ensureUser` then bind |
| `UNMATCHED_IDP` | report only — P2 is org master, no person is created |
| `CONFLICT` | no write (email points at a user already bound to another account) |

Keycloak internal tables are never read. Admin API `GET /users` only.

## Exclusions (this half)

- SAML
- LDAP / Active Directory federation
- SMS MFA (and TOTP/email MFA product acceptance)
- Browser Authorization Code + PKCE
- Keycloak Organizations as Nexus org master data, and Employment → Organization membership sync
- File metadata ↔ object store reconcile ([g10-file-reconcile.md](./g10-file-reconcile.md))
- Keycloak upgrade/rollback matrix (26.5.2 only)
- Docker / Compose as the app or Keycloak run path

## Evidence

Default (Stub IdP, no Keycloak process):

```bash
mvn -pl nexus-p2-iam -am test
```

Live Keycloak 26.5.2 on **8080** (opt-in, native process, not Docker):

```bash
export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_KEYCLOAK_ISSUER=http://127.0.0.1:8080/realms/nexus
# client secret from the native start env file, never committed
./nexus-p2-iam/scripts/run-g10-identity-reconcile.sh
```

Command log and honest gaps: `nexus-p2-iam/EVIDENCE.md`.

Recorded **2026-09-22T15:31:58Z**: Stub `mvn -pl nexus-p2-iam -am test` → 25 run / 0 fail / 3 skip. Live `LiveKeycloakPersonReconcileTest` against Keycloak **26.5.2** `http://127.0.0.1:8080/realms/nexus` → 1/1 match + email repair + unmatched-IdP report-only. That live IdP used native `start-dev` H2 because PostgreSQL was not listening on this VM.

Drift-fix **dry-run leftover** (detect + proposed `repairs` without writes by default): [g10-identity-drift-dry-run.md](./g10-identity-drift-dry-run.md). That leftover does **not** redo this apply walk and does **not** redo file reconcile.
