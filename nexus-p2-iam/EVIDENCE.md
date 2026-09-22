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

Person ↔ Keycloak match + repair on M0-04 IAM. File/object reconcile is not in this evidence.

Default Stub IdP (no Keycloak process):

```bash
mvn -pl nexus-p2-iam -am test
```

Recorded after the test run in this PR. Live Keycloak on **8080** is opt-in (`NEXUS_KEYCLOAK_SMOKE=1`); see [docs/g10-identity-reconcile.md](../docs/g10-identity-reconcile.md).

## Honest gaps (not verified here)

- **`nexus-bootstrap` packaged JAR** was not the process under test; the live app was `SpringBootTest` `DEFINED_PORT` `:18088`.
- **Keycloak SMTP / `execute-actions-email`** was not exercised (`nexus.iam.smtp.enabled=false`). Invite/reset still unproven against a real mail server.
- No browser, no PKCE, no SAML, no LDAP/AD, no SMS MFA, no Keycloak Organizations product path.
- No Keycloak upgrade/rollback matrix (26.5.2 only).
- HTTPS / production `start` (non-`start-dev`) / hostname v2 strict / reverse proxy was not run.
- Target Linux/ARM Profile and offline package: **not claimed**.
