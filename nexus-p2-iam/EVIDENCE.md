# M0-04 evidence (Boot 4.1.1 + PostgreSQL 15.x + live Keycloak 26.5.2)

Recorded on the agent VM. **Not** a target Profile / Linux-ARM / offline-package acceptance. Secrets were supplied via environment (and `/tmp/keycloak-smoke.env` mode `0600`); none are committed.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10 | `java -version` |
| Maven | 3.8.7 | |
| Spring Boot parent | **4.1.1** | matches M0-01 PR #46 |
| Flyway | **13.7.0** | window V200–V299, schema `iam` |
| PostgreSQL (business test DB) | **15.19** (`Ubuntu 15.19-1.pgdg24.04+2`) | `nexus_p2_iam_test` |
| PostgreSQL (Keycloak DB) | **15.19** same cluster, database **`keycloak`** | separate from business DB |
| Keycloak | **26.5.2** | native process, single version, no upgrade matrix |
| Quarkus (bundled with KC) | 3.27.2 | from KC startup log |
| App path | local Spring Boot test process on `:18088` | not Docker; not `nexus-bootstrap.jar` |

`kc.sh --version`:

```
Keycloak 26.5.2
JVM: 21.0.10 (Ubuntu OpenJDK 64-Bit Server VM 21.0.10+7-Ubuntu-124.04)
OS: Linux 6.12.94+ amd64
```

Startup log: `Keycloak 26.5.2 on JVM (powered by Quarkus 3.27.2) started in 11.456s. Listening on: http://0.0.0.0:8180. Management interface listening on http://0.0.0.0:9000.`

## How Keycloak was started (this VM)

**Native**, not Docker. Official distribution extracted at `/home/ubuntu/keycloak-26.5.2`. First `kc.sh start-dev --hostname-port=...` failed (`Unknown option: '--hostname-port'` on 26.5.2). Actual start was a **tmux** session `keycloak-26-5-2` wrapping:

```bash
set -a && source /tmp/keycloak-smoke.env && set +a
./bin/kc.sh start-dev \
  --http-port=8180 \
  --http-enabled=true \
  --hostname=127.0.0.1 \
  --hostname-strict=false \
  --health-enabled=true \
  --http-management-port=9000 \
  --db=postgres \
  --db-url=jdbc:postgresql://127.0.0.1:5432/keycloak \
  --db-username=ubuntu \
  --db-password="$KC_DB_PASSWORD" \
  --import-realm
```

Profile: `dev` (`start-dev`). Health: `GET http://127.0.0.1:9000/health/ready` → `{"status":"UP"}`.

The repeatable script `nexus-p2-iam/scripts/run-live-keycloak-smoke.sh` uses the same flags under `nohup` when nothing is already listening. This run **attached** to the already-running tmux process.

## Realm / client (no secrets)

| Item | Value |
|---|---|
| Issuer | `http://127.0.0.1:8180/realms/nexus` |
| Realm name | `nexus` |
| Realm id | `830a15a9-2761-49e2-9c28-7e1a02afed38` |
| `sslRequired` (after kcadm) | `none` (loopback HTTP) |
| Client id | `nexus-platform` |
| Client UUID | `022f83dc-ce63-4c5c-8739-9ceca636fe72` |
| `publicClient` | `false` (confidential) |
| `standardFlowEnabled` | `true` |
| `directAccessGrantsEnabled` | `false` (no p0-smoke direct-grant) |
| `serviceAccountsEnabled` | `true` (Admin API `ensureUser`) |
| Default client scopes | `service_account`, `web-origins`, `acr`, `profile`, `roles`, **`basic`**, `email` |
| Smoke user | username `carol`, Keycloak user id `cf1f7048-93e4-4765-b69f-bb52f1f6eab5` |
| Redirect URI used | `http://127.0.0.1:18088/api/v1/auth/callback` |

`basic` is required on Keycloak 26 so the **access token** includes `sub`. Importing a non-existent `openid` scope name dropped `basic` and login then failed with `identity subject is required`.

Client secret, admin password, and smoke password lived only in `/tmp/keycloak-smoke.env` (`0600`).

## Live smoke (Stub IdP off)

```bash
export NEXUS_KEYCLOAK_SMOKE=1
# plus issuer / client id / client secret / smoke password from env
mvn -pl nexus-p2-iam -am test \
  -Dtest=LiveKeycloakSmokeTest,AuthorizationCodeDriverTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Result (2026-09-22T10:10:08Z): **BUILD SUCCESS**. `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.

| Check | Result |
|---|---|
| `nexus.iam.idp=keycloak` (not stub) | yes |
| Authorization code (confidential client, no PKCE, no direct-grant) | yes — Keycloak login form over HTTP |
| Token exchange + JWKS RS256 validate | yes — access token `sub` present after `basic` scope |
| Server-issued tenant context (`X-Nexus-Context-Id`) | yes |
| Protected `GET /api/v1/persons` with real Bearer token | yes |
| Bare `X-Tenant-Id` → `P2.IAM.CLIENT_TENANT_REJECTED` | yes |
| Dual-tenant membership (Carol A in A, Carol B in B; listings do not leak) | yes |
| Logout against live token endpoint | called (`204`) |

`AuthorizationCodeDriver` is not a browser. Keycloak 26 sets `AUTH_SESSION_ID` / `KC_RESTART` as `Secure; SameSite=None` even on `http://127.0.0.1`. The JDK `CookieManager` will not send those cookies on HTTP (`LOGIN_ERROR` / `cookie_not_found`). The driver stores and returns them on loopback HTTP only.

## Default `mvn test` (Keycloak process still up)

```bash
mvn -pl nexus-p2-iam -am test
```

`nexus-p2-iam`: **Tests run: 16, Failures: 0, Errors: 0, Skipped: 1** (`LiveKeycloakSmokeTest` skipped unless `NEXUS_KEYCLOAK_SMOKE=1`).

## PostgreSQL 15.x (unchanged from prior closeout)

`JdbcDualTenantIsolationTest` still ran against **PostgreSQL 15.19**. Flyway `V200` in schema `iam`.

## G10 identity half (this PR)

Person ↔ Keycloak match + repair on M0-04 IAM. File/object reconcile is not in this evidence. Recorded **2026-09-22T15:31:58Z** on the agent VM. Secrets stayed in `/tmp/keycloak-smoke.env` (`0600`); none committed.

### Default Stub IdP

```bash
mvn -pl nexus-p2-iam -am test
```

`nexus-p2-iam`: **Tests run: 25, Failures: 0, Errors: 0, Skipped: 3** (`LiveKeycloakSmokeTest`, `LiveKeycloakPersonReconcileTest`, `JdbcDualTenantIsolationTest` — PostgreSQL 15 was not listening). `PersonIdentityReconcileTest` **8/8** (match, dry-run, email repair, rebind, provision, unmatched-IdP report-only, tenant-scoped repair, collision). `IdentityApiTest` **4/4** includes HTTP preview + tenant apply.

### Live Keycloak 26.5.2 on :8080

This VM had no PostgreSQL listener, so Keycloak was started **native** `kc.sh start-dev` with the bundled **H2** (not the product Keycloak-on-PG path). HTTP **8080**, management **9000**. Realm import `nexus`. Confidential client `nexus-platform`. Service-account roles `manage-users` / `view-users` / `query-users`.

```
Keycloak 26.5.2 on JVM (powered by Quarkus 3.27.2) started in 7.341s.
Listening on: http://0.0.0.0:8080. Management interface listening on http://0.0.0.0:9000.
GET http://127.0.0.1:9000/health/ready → UP
issuer http://127.0.0.1:8080/realms/nexus
```

```bash
export NEXUS_KEYCLOAK_SMOKE=1
export NEXUS_KEYCLOAK_ISSUER=http://127.0.0.1:8080/realms/nexus
mvn -pl nexus-p2-iam -am test -Dtest=LiveKeycloakPersonReconcileTest
```

Result: **Tests run: 1, Failures: 0, Errors: 0, Skipped: 0**. `nexus.iam.idp=keycloak`. Provisioned person **MATCHED**; drifted email dry-run left stale; apply **SYNC_P2** restored `g10-admin@example.com`; extra Keycloak user `g10-orphan` stayed **UNMATCHED_IDP** (no P2 person created).

| Check | Result |
|---|---|
| Persons match Keycloak users | yes |
| Inconsistency repair (email drift) | yes |
| Unmatched IdP user not created as Person | yes |
| SAML / LDAP / SMS MFA / browser PKCE | not run (excluded) |
| File metadata ↔ object | not this PR |

## G17 admin MFA slice (this PR)

Product policy `nexus.iam.mfa.admin-required=true` (default). Session gate on the existing M0-04 login path. Same Keycloak 26.5.2 client; no second IdP. **Not** a full G17 close. **G18 stays closed.**

Recorded **2026-09-22T16:45:36Z** (targeted) and **2026-09-22T16:45:52Z** (full module) on the agent VM. OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**. No Docker. No live PostgreSQL / Keycloak required.

```bash
mvn -pl nexus-p2-iam -am test \
  -Dtest=AdminMfaSessionTest,AdminMfaApiTest,MfaClaimsTest,SessionLoginAuditTest,IdentityApiTest,DualTenantIsolationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

**BUILD SUCCESS**. Tests run: **23**, Failures: **0**, Errors: **0**, Skipped: **0**.

`AdminMfaSessionTest` **7/7**, `AdminMfaApiTest` **1/1**, `MfaClaimsTest` **2/2**, `SessionLoginAuditTest` **1/1**, `IdentityApiTest` **4/4**, `DualTenantIsolationTest` **8/8**.

```bash
mvn -pl nexus-p2-iam -am test
```

**BUILD SUCCESS** at **2026-09-22T16:45:52Z**. `nexus-p2-iam`: Tests run: **36**, Failures: **0**, Errors: **0**, Skipped: **3** (`LiveKeycloakSmokeTest`, `LiveKeycloakPersonReconcileTest`, `JdbcDualTenantIsolationTest` — Keycloak / PostgreSQL 15 not listening). Permission admin-login regression (`TenantMemberGrantSeedApiTest`, `PermissionApiTest`, `G2RoleMatrixApiTest`, `G9PolicySnapshotApiTest`): **5/5** at **2026-09-22T16:46:03Z**.

| Check | Result |
|---|---|
| Policy disabled + admin password-only token issues context (failing-before) | PASS |
| Policy forced + same token → `P2.IAM.MFA_REQUIRED`, no context (passing-after) | PASS |
| Admin MFA token issues context | PASS |
| Non-admin password-only still issues context | PASS |
| HTTP `POST /api/v1/auth/login` admin without MFA → 403 | PASS |
| `GET /api/v1/auth/authorize-url?acr=mfa` includes `acr_values=mfa` | PASS |
| SAML / SMS MFA / hardware / browser PKCE | not claimed |
| Live Keycloak admin OTP walk | not run |
| G18 reopen | **no** |

## G10 leftover — Keycloak 8080 drift-fix dry-run (this PR)

Detect + proposed `repairs` without writes. Existing apply walk (#76 / `LiveKeycloakPersonReconcileTest`) is not redone here. File reconcile is not this PR. Recorded after rebase onto `origin/main` `23656f3`. See [docs/g10-identity-drift-dry-run.md](../docs/g10-identity-drift-dry-run.md).

```bash
./scripts/g10-identity-drift-dry-run.sh
```

Default `NEXUS_G10_APPLY=1` is refused. Stub suites: `PersonIdentityDriftDryRunTest`, `PersonIdentityReconcileTest`, `IdentityApiTest`. Live: `LiveKeycloakIdentityDriftDryRunTest` against `http://127.0.0.1:8080/realms/nexus` when `kc.sh` / OIDC discovery is present.

| Check | Result |
|---|---|
| Dry-run proposes `SYNC_P2` / `REBIND` / `PROVISION_IDP` | pending this run |
| Dry-run leaves P2 / IdP rows unchanged | pending this run |
| Live Keycloak 8080 dry-run | pending this run |
| SAML / LDAP / SMS MFA | not claimed |
| File metadata ↔ object | not this PR |
| G18 reopen | **no** |

## Honest gaps (not verified here)

- **`nexus-bootstrap` packaged JAR** was not the process under test; the live app was `SpringBootTest` `DEFINED_PORT` `:18088`.
- **Keycloak SMTP / `execute-actions-email`** was not exercised (`nexus.iam.smtp.enabled=false`). Invite/reset still unproven against a real mail server.
- No browser, no PKCE, no SAML, no LDAP/AD, no SMS MFA, no Keycloak Organizations product path.
- No Keycloak upgrade/rollback matrix (26.5.2 only).
- HTTPS / production `start` (non-`start-dev`) / hostname v2 strict / reverse proxy was not run.
- Target Linux/ARM Profile and offline package: **not claimed**.
