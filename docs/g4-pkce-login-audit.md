# G4 leftover: Authorization Code + PKCE login on the hash chain

S0+S1 PostgreSQL slice. Not an M0 redo. **Product G18 stays closed.** This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules and does **not** claim full G4 or G17.

The same-trace walk on `main` ([g4-same-trace-walk.md](./g4-same-trace-walk.md)) already put stub-IdP `LOGIN` on one `WALK_TRACE`. The leftover here is **real IdP Authorization Code + PKCE** (or public-client PKCE) landing `p2.auth.login.accepted` on the existing hash chain, correlatable by `X-Trace-Id`.

## What this close covers

Opt-in PKCE on the existing M0-04 login path, plus a native Keycloak **26.5.2** walk (no Docker):

1. `GET /api/v1/auth/authorize-url` stays confidential-client **without** PKCE (M0-04 live smoke unchanged).
2. `GET /api/v1/auth/authorize-url?pkce=s256` adds RFC 7636 `code_challenge` / `S256` and returns `code_verifier` for the BFF/HTTP driver to POST back.
3. `POST /api/v1/auth/login` accepts `code_verifier` / `codeVerifier` and sends it on the Keycloak token exchange.
4. Stub IdP ignores PKCE, so the closed G18 stub-admin walk still issues a context.
5. Native Keycloak login-form HTTP driver (not Chromium) completes Authorization Code + PKCE. Keycloak 26 `Secure; SameSite=None` loopback cookies are returned the same way as M0-04. Realm `basic` scope stays attached so access-token `sub` is present.
6. That login writes `p2.auth.login.accepted` on `AuditPort` / `platform.audit_record` under a fixed `X-Trace-Id`. `audits.verifyChain()` stays intact.

Public client `nexus-web` is in `nexus-realm.json` (`publicClient=true`, `pkce.code.challenge.method=S256`, no direct-grant). The product Spring bean still uses confidential `nexus-platform`; the live walk also tries a public-client token exchange when that client exists.

This is **not** a headless browser UI walk and **not** a product-wide G4 close.

## Linked traces

| Trace | Role |
|---|---|
| `0199a1c0-0000-7000-8000-00000000c214` | Stub `loginWithCode` + verifier → in-memory chain (`SessionPkceLoginAuditTest`) |
| `0199a1c0-0000-7000-8000-00000000c215` | Live Keycloak PKCE → in-memory `AuditPort` (`LiveKeycloakPkceLoginAuditTest`) |
| `0199a1c0-0000-7000-8000-00000000c216` | Live Keycloak PKCE → JDBC `platform.audit_record` (`G4PkceLoginAuditIT`) |

## How to run

```bash
# Always-green (no Keycloak)
mvn -pl nexus-p2-iam -am test \
  -Dtest=PkceTest,AuthPkceApiTest,SessionPkceLoginAuditTest,AuthorizationCodeDriverTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# Native Keycloak 26.5.2 + live PKCE + LOGIN on chain
# KEYCLOAK_HOME defaults to /home/ubuntu/keycloak-26.5.2
# Secrets: env or /tmp/keycloak-smoke.env (0600, not git)
./scripts/g4-pkce-login-audit.sh
```

`start-native-keycloak.sh` uses PostgreSQL when `127.0.0.1:5432` is up, otherwise Keycloak `start-dev` bundled **H2** (same honest fallback as the G10 native walk). `--hostname-port` is not a 26.5.2 flag.

## Evidence

Recorded on this agent VM after the live walk. Fill the table from the script output before merge; default `mvn test` stays green without Keycloak (live classes skip unless `NEXUS_KEYCLOAK_SMOKE=1`).

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL (product IT) | Zonky **15.18** |
| Keycloak | **26.5.2** native `kc.sh start-dev` (no Docker) |
| Walk | HTTP form driver + PKCE S256, not Chromium |

## Honest leftover G4 rows

| Still open | Why |
|---|---|
| Human browser click / Chromium UI | Desktop Playwright leftover (stub IdP, not product close) is [g4-pkce-browser-click.md](./g4-pkce-browser-click.md). HTTPS loopback driver is [g4-pkce-https-browser.md](./g4-pkce-https-browser.md). A laptop + live Keycloak click still needs to trust that CA. `GET /api/v1/auth/callback` still does not carry the verifier. |
| SPA-generated verifier | Evidence path leftover is [g4-pkce-spa-verifier.md](./g4-pkce-spa-verifier.md) (live Keycloak SKIP if unreachable). This #108 walk stays BFF-shaped. |
| Full G4 product close | HTTP export wizard, switching P1 tenant audit-events off staging. 90-day hot purge leftover is [g4-hot-purge-90d.md](./g4-hot-purge-90d.md). Object-store cold archive is on `main` via #104. |
| G17 / G6 | Not this package |
| G18 reopen | **No.** Stub IdP + fabricated admin MFA token is unchanged |

Same-TX `append` joining is unchanged. Default authorize URL still has no `code_challenge` (M0-04 confidential smoke).
