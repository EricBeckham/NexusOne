# Keycloak realm import (M0-04)

Import `nexus-realm.json` into a **native** Keycloak **26.5.2** process. See `nexus-p2-iam/scripts/run-live-keycloak-smoke.sh` and `nexus-p2-iam/README.md`.

The committed JSON uses a placeholder client secret. The smoke script substitutes a runtime secret from `NEXUS_KEYCLOAK_CLIENT_SECRET` into a **local** import file under `data/import/` (not committed). Direct-grant / `p0-smoke` clients are not part of this realm.

`sslRequired` is `none` so loopback HTTP (`http://127.0.0.1:8180`) is allowed. Keycloak 26 still sets `AUTH_SESSION_ID` / `KC_RESTART` as `Secure; SameSite=None`; the live smoke HTTP driver sends those cookies on loopback despite the Secure flag. Do not copy that cookie handling into a browser product path. The G4 leftover after #108 is local self-signed HTTPS (`scripts/g4-pkce-https-browser.sh`, [docs/g4-pkce-https-browser.md](../../../docs/g4-pkce-https-browser.md)) so a cookie-honoring client can complete PKCE without dropping Secure cookies.

Redirect URIs are loopback-only for agent/local JAR smoke (`18088` live test, `8088` standalone, `8080` bootstrap) plus HTTPS loopback (`18443` / `8443`) for the G4 leftover after #108. Default client scopes include Keycloak 26 `basic` (access-token `sub`); do not replace them with a non-existent `openid` scope name. Local self-signed HTTPS is `docs/g4-pkce-https-browser.md`.

Realm import also includes public client `nexus-web` (`pkce.code.challenge.method=S256`, no direct-grant) for the G4 PKCE leftover. Product login still uses confidential `nexus-platform` unless an operator points `NEXUS_KEYCLOAK_CLIENT_ID` at `nexus-web`.

Realm import now includes TOTP policy defaults, realm roles `TENANT_ADMIN` / `PLATFORM_ADMIN`, and `oidc-amr-mapper` so an OTP completion can appear as `amr=otp`. Product MFA enforcement for those admin positions is the P2 session gate (`docs/g17-admin-mfa.md`). The native admin TOTP enrollment / OTP challenge leftover is `docs/g17-admin-totp-enrollment.md`. Binding a Keycloak Conditional OTP authenticator to those roles as a flow-level force remains operator configuration.
