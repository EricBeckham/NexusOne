# M0-04 P2 身份与组织（`nexus-p2-iam`）

Product-path identity and organization for NexusOne. This module owns Person / global account, department tree, positions, employments, tenant membership, Keycloak OIDC session bridging, and the admin-user initializer expected by M0-03. It consumes a server-issued `TenantContext` and does **not** implement the permission engine (M0-05) or SQL Guard (M0-02).

PoC under `poc/p0-03/` is reference only. Locked IdP version for this round: **Keycloak 26.5.2**. Spring Boot parent: **4.1.1** (same as M0-01 PR #46). Flyway: **13.7.0**, window **V200–V299**, schema **`iam`**.

## What this delivers

| Capability | Where |
|---|---|
| OIDC login / logout / session against Keycloak 26.5.2 (confidential client, authorization code) | `idp/`, `session/`, `POST /api/v1/auth/login` |
| Global account reconciled from IdP `sub` + email | `account/AccountReconciler` |
| Person, department tree, position, employment | `org/OrganizationService` + `/api/v1/persons\|departments\|positions\|employments` |
| Tenant membership and tenant switch | `membership/`, `POST /api/v1/auth/tenant-switch` |
| Server-issued tenant context (persisted, bound to IdP subject) | `iam_issued_context`; header `X-Nexus-Context-Id` |
| Admin-user initializer for M0-03 | `TenantAdminInitializer` and `POST /api/v1/internal/tenants/{id}/admin-users` |
| SMTP hooks for invite / password reset only | Keycloak `execute-actions-email`; disabled unless `nexus.iam.smtp.enabled=true` |
| Dual-tenant membership isolation at the data model | tenant-scoped tables + tests |
| G10 person ↔ Keycloak match + repair | `account/PersonIdentityReconciler`, `GET\|POST /api/v1/identity/reconcile` |
| G10 leftover: Keycloak 8080 drift-fix dry-run | proposed `repairs` on preview; `./scripts/g10-identity-drift-dry-run.sh` (default no writes) |
| Admin MFA policy (platform + tenant admin, default forced) | `session/AdminMfaPolicy`, login / switch / load-context gate; `GET /api/v1/auth/authorize-url?acr=mfa` |

`tenant_id` on the wire is **rejected**. Send `X-Tenant-Id` and the API returns `P2.IAM.CLIENT_TENANT_REJECTED`. Context is issued only after P2 membership is checked.

## Verified in this PR

Runnable without Keycloak (`nexus.iam.persistence=memory`, `nexus.iam.idp=stub`):

```bash
mvn -pl nexus-p2-iam -am test
```

**36 tests, 0 failures, 3 skipped** on OpenJDK 21.0.10 / Spring Boot **4.1.1** / Flyway **13.7.0** (includes G17 admin-MFA slice). Skips: `LiveKeycloakSmokeTest`, `LiveKeycloakPersonReconcileTest` (opt-in `NEXUS_KEYCLOAK_SMOKE=1`), and `JdbcDualTenantIsolationTest` when PostgreSQL 15 is unreachable.

G10 identity half (persons vs Keycloak on **8080**): [docs/g10-identity-reconcile.md](../docs/g10-identity-reconcile.md). Drift-fix dry-run leftover (detect + proposed fix, default no writes): [docs/g10-identity-drift-dry-run.md](../docs/g10-identity-drift-dry-run.md). File/object reconcile is [docs/g10-file-reconcile.md](../docs/g10-file-reconcile.md).

Covered by tests:

- Login reconciles a global account from IdP claims.
- G10 preview matches persons to IdP users and now lists **proposed** repairs (`SYNC_P2` / `REBIND` / `PROVISION_IDP`) without writing. Apply still repairs email drift, stale `keycloak_user_id`, and missing IdP users. Unmatched IdP users are reported only.
- No context is issued until the account has an active tenant membership (auto-issue when there is exactly one).
- Dual-tenant membership: same natural person has distinct Person rows; listing persons in tenant A never returns tenant B rows.
- Tenant switch to a non-member tenant is forbidden.
- Issued context is bound to the IdP subject; another user cannot load it.
- Org lookups (`getPerson`, department tree, positions) are tenant-scoped.
- Missing `TenantContext` rejects org writes.
- `X-Tenant-Id` is rejected on the HTTP path.
- Flyway `V200__iam_identity.sql` in schema `iam`: tenant tables have `tenant_id UUID NOT NULL`; `iam.iam_global_account` is platform-scoped.
- Invite / password-reset hooks record Keycloak execute-actions when SMTP is enabled.
- HTTP login-form helper sends Keycloak 26 `Secure` loopback cookies (`AuthorizationCodeDriverTest`).
- Admin MFA gate: tenant/platform admin without MFA cannot issue a privileged session (`P2.IAM.MFA_REQUIRED`); non-admin password-only still can. See [docs/g17-admin-mfa.md](../docs/g17-admin-mfa.md). Live Keycloak TOTP enrollment / OTP challenge leftover: [docs/g17-admin-totp-enrollment.md](../docs/g17-admin-totp-enrollment.md). In-app enrollment UX leftover: [docs/g17-totp-enrollment-ux.md](../docs/g17-totp-enrollment-ux.md).

`JdbcDualTenantIsolationTest` ran against **PostgreSQL 15.19** (`Ubuntu 15.19-1.pgdg24.04+2`):

```
PostgreSQL 15.19 (Ubuntu 15.19-1.pgdg24.04+2) on x86_64-pc-linux-gnu, compiled by gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0, 64-bit
```

That is PostgreSQL **15.x**, not 16.15. It is **not** a target Linux/ARM Profile acceptance. The JDBC test **fails** if a reachable server is not 15.x and skips only when the database is unreachable. Exact command log: `nexus-p2-iam/EVIDENCE.md`.

**Live Keycloak 26.5.2 smoke** ran on this VM (native `kc.sh start-dev` in tmux, Stub IdP off). Command, realm/client ids, and honest gaps: `nexus-p2-iam/EVIDENCE.md`. Opt-in: `NEXUS_KEYCLOAK_SMOKE=1` + `nexus-p2-iam/scripts/run-live-keycloak-smoke.sh`.

## Explicitly skipped / still pending

These are **not** accepted:

- SAML
- LDAP / Active Directory federation
- SMS MFA, hardware MFA / WebAuthn, recovery codes, email MFA product acceptance (admin *session gate* is [docs/g17-admin-mfa.md](../docs/g17-admin-mfa.md); native Keycloak admin TOTP leftover is [docs/g17-admin-totp-enrollment.md](../docs/g17-admin-totp-enrollment.md); in-app enrollment UX leftover is [docs/g17-totp-enrollment-ux.md](../docs/g17-totp-enrollment-ux.md); not a full G17 close)
- Human-laptop live Keycloak Authorization Code + PKCE as a product G4 close. HTTP-driver PKCE is [docs/g4-pkce-login-audit.md](../docs/g4-pkce-login-audit.md). Local self-signed HTTPS leftover is [docs/g4-pkce-https-browser.md](../docs/g4-pkce-https-browser.md). Desktop Playwright leftover (stub IdP, not a product close) is [docs/g4-pkce-browser-click.md](../docs/g4-pkce-browser-click.md). H5 `/m/*` Playwright/stub leftover is [docs/g15-h5-pkce-browser.md](../docs/g15-h5-pkce-browser.md). Product default authorize URL stays confidential-client without PKCE. `p0-smoke` direct-grant must not be copied into production.
- Keycloak Organizations as Nexus org master data or acceptance evidence (Employment is not synced to Organizations)
- File metadata ↔ object store reconcile (other G10 half; [g10-file-reconcile.md](../docs/g10-file-reconcile.md))
- Keycloak old-version upgrade / rollback matrices (single version 26.5.2 only)
- Target Linux / ARM / offline package / deployment Profile acceptance
- Permission engine, roles, DataScope, FieldPolicy, PolicySnapshot (M0-05)
- Query AST / TableAccessPlan SQL Guard (M0-02 owns that; this module uses explicit JDBC/`tenant_id` predicates)
- Docker / Compose as the app or Keycloak run path

## Native Keycloak 26.5.2 (no Docker)

Do not use `poc/p0-03/compose.yaml` for the product path. P0 notes already lock 26.5.2; run it as a local process against a **separate** PostgreSQL database (not the business database).

1. Download Keycloak `26.5.2` from the official distribution (zip), JDK 21.
2. Create a Keycloak-only database, e.g. `keycloak`, with a least-privilege role. Password via environment, never committed.
3. Copy `src/main/resources/keycloak/nexus-realm.json` to Keycloak `data/import/` (replace the client-secret placeholder out of band). From the Keycloak home:

```bash
export KC_BOOTSTRAP_ADMIN_USERNAME=...
export KC_BOOTSTRAP_ADMIN_PASSWORD=...   # env only
# 26.5.2 has no --hostname-port flag. Health is on the management port.
bin/kc.sh start-dev \
  --http-port=8180 \
  --http-enabled=true \
  --hostname=127.0.0.1 \
  --hostname-strict=false \
  --health-enabled=true \
  --http-management-port=9000 \
  --db=postgres \
  --db-url=jdbc:postgresql://127.0.0.1:5432/keycloak \
  --db-username="$KC_DB_USERNAME" \
  --db-password="$KC_DB_PASSWORD" \
  --import-realm
```

Opt-in live smoke (Stub IdP off, secrets from env, writes `/tmp/keycloak-smoke.env` mode 0600 — not git):

```bash
# KEYCLOAK_HOME defaults to /home/ubuntu/keycloak-26.5.2
./nexus-p2-iam/scripts/run-live-keycloak-smoke.sh
```

4. Point this module at that issuer:

```bash
export NEXUS_IAM_IDP=keycloak
export NEXUS_KEYCLOAK_ISSUER=http://127.0.0.1:8180/realms/nexus
export NEXUS_KEYCLOAK_CLIENT_ID=nexus-platform
export NEXUS_KEYCLOAK_CLIENT_SECRET=...   # env only
# optional product DB
export NEXUS_IAM_PERSISTENCE=jdbc
export NEXUS_DB_URL=jdbc:postgresql://127.0.0.1:5432/nexusone
export NEXUS_DB_USER=...
export NEXUS_DB_PASSWORD=...
mvn -pl nexus-bootstrap -am -q -DskipTests package
java -jar nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar
```

Health: `GET http://127.0.0.1:8080/actuator/health` or `GET /api/v1/health`. Login: `GET /api/v1/auth/authorize-url` then `POST /api/v1/auth/login` with the authorization `code`.

Keycloak SMTP (invite / reset) is configured in the Keycloak realm, not in this app. Enable `NEXUS_IAM_SMTP_ENABLED=true` only after that realm SMTP works.

## Coordination

- **M0-01**: parent is `one.nexus:nexusone` (Spring Boot **4.1.1** BOM). IDs use `nexus-common` `UuidV7`. Flyway **V200** is in schema `iam` on `classpath:db/migration` so bootstrap applies it with V001–V003. Do not make Docker required.
- **M0-02**: consume `one.nexus.p2.kernel.TenantContext` / `TenantContextHolder`. Identity **issues** context after membership checks; isolation kernel **propagates** it to SQL Guard, jobs, cache, and files. Replace the in-module holder with the kernel holder when that module lands. Do not rewrite Guard or quota code here.
- **M0-03**: call `TenantAdminInitializer.initialize(Command)` in-process, or `POST /api/v1/internal/tenants/{tenantId}/admin-users` with `X-Nexus-Bootstrap-Token`. That creates global account, Keycloak user, membership, person, root department `ROOT`, position `TENANT_ADMIN`, and primary employment.

## Persistence notes

Tenant tables (`iam_person`, `iam_department`, `iam_position`, `iam_employment`, `iam_tenant_membership`, `iam_issued_context`) all have `tenant_id NOT NULL`. Queries always include `tenant_id = ?`. `iam_global_account` is the platform-scoped natural-person record mapped to one Keycloak user. Keycloak internal tables are never read.

Default `nexus.iam.persistence=memory` is for local tests and sibling-module development. Product data uses PostgreSQL via `jdbc`.
