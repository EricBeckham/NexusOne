# G4 leftover after #108: HTTPS loopback PKCE (cookie-preserving)

S0+S1 PostgreSQL slice. Not an M0 redo. **Product G18 stays closed.** This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules and does **not** claim full G4 or G17.

#108 ([g4-pkce-login-audit.md](./g4-pkce-login-audit.md)) already put Authorization Code + PKCE `LOGIN` on the hash chain with a native Keycloak **HTTP** form driver. That driver **ignores** the `Secure` flag because Keycloak 26 sets `AUTH_SESSION_ID` / `KC_RESTART` as `Secure; SameSite=None` even on `http://127.0.0.1`. A real browser drops those cookies (`LOGIN_ERROR` / `cookie_not_found`).

This leftover is **local self-signed HTTPS** for Keycloak + product `authorize-url` / `login`, plus a cookie-honoring HTTP/2 driver (JDK `CookieManager`, does **not** ignore `Secure`) that completes PKCE and lands `p2.auth.login.accepted` on the existing JDBC chain.

## What this slice covers

1. `scripts/g4-pkce-https-certs.sh` writes a 127.0.0.1 SAN cert + PKCS12 under `.local/g4-https/` (gitignored). Password is `/tmp/g4-https.env` mode `0600`.
2. `nexus-p2-iam/scripts/start-native-keycloak-https.sh` starts native Keycloak **26.5.2** (`kc.sh start-dev`, no Docker) on `https://127.0.0.1:8543` with an isolated data dir so an HTTP smoke on `:8180` can stay up. Admin/kcadm stays on loopback HTTP `:8181`. Realm `sslRequired=external`.
3. Product IT enables `server.ssl` + HTTP/2 on `https://127.0.0.1:18443` using the same PKCS12.
4. `GET /api/v1/auth/authorize-url?pkce=s256` still returns BFF `code_verifier`. `POST /api/v1/auth/login` sends it. `GET /callback` still does not carry the verifier.
5. `AuthorizationCodeDriver.httpsHonoringSecureCookies` uses JDK `CookieManager` over HTTP/2. Contrast test: the same jar **refuses** Secure cookies on HTTP (the #108 leftover).
6. Optional Chromium CDP (`NEXUS_G4_HTTPS_CHROME=1`) is attempted when Chrome is installed. It is **not** required for LOGIN evidence.

G18 stub IdP + default authorize URL (no PKCE) stay unchanged.

## Linked traces

| Trace | Role |
|---|---|
| `0199a1c0-0000-7000-8000-00000000c217` | HTTPS HTTP/2 PKCE → JDBC `platform.audit_record` LOGIN (`G4PkceHttpsBrowserIT`) |
| `0199a1c0-0000-7000-8000-00000000c218` | Optional Chrome CDP walk (skip / abort if Chrome is missing or blocked) |

## How to run

```bash
# Always-green (no Keycloak): Secure cookies on HTTPS, drop on HTTP
mvn -pl nexus-p2-iam -am test \
  -Dtest=AuthorizationCodeHttpsDriverTest,AuthorizationCodeDriverTest,PkceTest,AuthPkceApiTest,SessionPkceLoginAuditTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# Native Keycloak HTTPS + product SSL + LOGIN on chain
# KEYCLOAK_HOME defaults to /home/ubuntu/keycloak-26.5.2
# Secrets: /tmp/g4-https.env and /tmp/keycloak-https.env (0600, not git)
./scripts/g4-pkce-https-browser.sh
```

Insecure-origin fallback (not the primary path): Chromium
`--unsafely-treat-insecure-origin-as-secure=http://127.0.0.1:8180`. Prefer trusting `.local/g4-https/server.crt` and using HTTPS.

## Evidence

Recorded **pending-this-run** on this agent VM after the live walk. Default `mvn test` stays green without Keycloak (live class skips unless `NEXUS_KEYCLOAK_HTTPS=1`).

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL (product IT) | Zonky **15.18** |
| Keycloak | **26.5.2** native `kc.sh start-dev` HTTPS (no Docker) |
| Walk | HTTP/2 + JDK CookieManager (honors Secure). Optional Chrome CDP. |

```
./scripts/g4-pkce-https-browser.sh
# pending
```

| Suite | Result | What it proved |
|---|---|---|
| `AuthorizationCodeHttpsDriverTest` | pending | Secure cookies sent on HTTPS; same jar drops them on HTTP |
| `G4PkceHttpsBrowserIT` HTTP/2 | pending | HTTPS PKCE → `p2.auth.login.accepted` on JDBC chain `c217` |
| `G4PkceHttpsBrowserIT` Chrome | pending | Optional; skip/abort is not a walk failure |
| `SessionPkceLoginAuditTest` | pending | G18 stub + verifier still lands LOGIN (unchanged) |
| `JdbcAuditChainTransactionTest` | not re-run here | Same-TX `append` rules unchanged on `main` |

## Honest leftover G4 rows

This is **not** a product-wide G4 close and **not** a human laptop UI acceptance.

| Still open | Why |
|---|---|
| Human browser click on a laptop | Desktop Playwright leftover (stub IdP) is [g4-pkce-browser-click.md](./g4-pkce-browser-click.md). Live Keycloak still needs `.local/g4-https/server.crt` (or the insecure-origin flag). This package is the loopback HTTPS + scripted driver. |
| SPA-generated verifier | BFF still mints `code_verifier` on `authorize-url?pkce=s256`. A production public-client page should generate it in the browser. |
| Full G4 product close | HTTP export wizard, switching P1 tenant audit-events off staging. 90-day hot purge leftover is [g4-hot-purge-90d.md](./g4-hot-purge-90d.md). |
| G17 / G6 | Not this package. Local GM TLS dual-cert is [g17-gm-tls-dual-cert.md](./g17-gm-tls-dual-cert.md) and is a different leftover. |
| G18 reopen | **No.** Stub IdP + fabricated admin MFA token is unchanged. |

Same-TX `append` joining is unchanged. Default authorize URL still has no `code_challenge` (M0-04 confidential smoke).
