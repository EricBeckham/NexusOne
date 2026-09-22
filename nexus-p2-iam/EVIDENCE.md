# M0-04 follow-up evidence (Boot 4.1.1 + PostgreSQL 15.x)

Recorded on the agent VM after clearing two coordinator conditions. **Live Keycloak smoke was not requested and was not run.**

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10 | `java -version` |
| Maven | 3.8.7 | |
| Spring Boot parent | **4.1.1** | `mvn help:evaluate -Dexpression=project.parent.version` — matches M0-01 PR #46 |
| Flyway | **13.7.0** | `flyway-core-13.7.0.jar`; same P0 pin as M0-01 |
| PostgreSQL | **15.19** (`Ubuntu 15.19-1.pgdg24.04+2`) | `select version()` on `nexus_p2_iam_test`. **Not** 16.15. Not a target Linux/ARM Profile. |
| Runtime | local `mvn test` | No Docker / Compose |

`select version()`:

```
PostgreSQL 15.19 (Ubuntu 15.19-1.pgdg24.04+2) on x86_64-pc-linux-gnu, compiled by gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0, 64-bit
```

## Tests

```bash
mvn -pl nexus-p2-iam -am clean test
```

Result: **13 tests, 0 failures, 0 skipped**.

| Class | What it proved |
|---|---|
| `SchemaContractTest` | V200 lives in schema `iam`; tenant tables have `tenant_id NOT NULL` |
| `IdentityApiTest` | Boot 4.1.1 MockMvc path; `X-Tenant-Id` rejected; issued context lists only current tenant |
| `JdbcDualTenantIsolationTest` | Flyway `V200` on **PostgreSQL 15.19**; dual-tenant person listing does not leak. Aborts if PG is down; **fails** if the server is not 15.x |
| `DualTenantIsolationTest` | In-memory dual membership, switch, context binding |

Flyway history on `nexus_p2_iam_test`:

```
 version | description  | success
 200     | iam identity | t
```

No PostgreSQL 15.x SQL incompatibilities were found in `V200__iam_identity.sql` (UUID, TIMESTAMPTZ, partial unique index).

## Remaining gaps (honest)

- **Live Keycloak 26.5.2 smoke is still pending** (authorization-code exchange, JWKS validation, logout, execute-actions-email against a real process). Stub IdP covers the product session/membership logic only.
- SAML, LDAP/AD, SMS MFA, Keycloak Organizations, browser PKCE — not this round.
- Keycloak upgrade/rollback matrices — single version 26.5.2 only.
- Target Linux/ARM / offline package Profile — not claimed.
- Parent is **`one.nexus:nexusone` 0.1.0-SNAPSHOT** (Boot **4.1.1** BOM + `nexus-common`). Duplicate UUIDv7 helper removed.
- Permission engine (M0-05) and SQL Guard (M0-02) are out of scope.
