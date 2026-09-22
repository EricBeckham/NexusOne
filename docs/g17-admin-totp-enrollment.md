# G17 leftover — admin TOTP enrollment / live Keycloak OTP challenge

S0+S1 PostgreSQL remainder after the admin MFA session gate ([g17-admin-mfa.md](./g17-admin-mfa.md) / #96). **G18 stays closed.** This slice does **not** redo 三权分立, 国密 TLS, or `AuditPort.append` / `appendNewTransaction` same-TX rules.

#96 proved the P2 session gate with stub tokens. The leftover here is a **native Keycloak 26.5.2** walk that enrolls TOTP on a real `TENANT_ADMIN` and completes the OTP form once:

| Token / walk | Actor | Privileged context |
|---|---|---|
| Password-only authorization code (no OTP credential yet) | `TENANT_ADMIN` | **Rejected** `P2.IAM.MFA_REQUIRED` (403). No `TenantContext`. |
| `CONFIGURE_TOTP` required action + setup form | same admin | Shared secret stored as a Keycloak `otp` credential. |
| Password only after enrollment | same admin | Keycloak OTP form. No authorization code. |
| Password + TOTP | same admin | Issued. Access token `amr` contains `otp`. |

Product enforcement is still the P2 session gate (`AdminMfaPolicy` / `MfaClaims`). Keycloak is configured so an OTP completion is visible on the token: `oidc-amr-mapper` plus authenticator reference `otp` / `pwd` (`default.reference.value`, `default.reference.maxAge=3600`). That is not a second authenticator stack.

## What this slice delivers

| Piece | Behaviour |
|---|---|
| RFC 6238 helper | `Totp` (HmacSHA1, 6 digits, 30s) drives Keycloak forms. Not a product MFA UI. |
| HTTP driver | `AuthorizationCodeDriver` now classifies password / OTP / TOTP-setup pages and can enroll or submit OTP. Carol password-only smoke is unchanged. |
| Realm import | `nexus-realm.json` includes `oidc-amr-mapper` on `nexus-platform` / `nexus-web`. TOTP policy defaults were already on #96. |
| Native configure | `nexus-p2-iam/scripts/configure-keycloak-admin-totp.sh` attaches the mapper and reference values on an already-running realm. |
| Live IT | `LiveKeycloakAdminTotpTest` (opt-in `NEXUS_KEYCLOAK_SMOKE=1`). Skips on default `mvn test`. |
| Stub G18 path | `G18StubAdminLoginTest` + existing `AdminMfaApiTest`. Default stub tokens still fabricate `acr=mfa` / `amr=otp` so the closed G18 walk issues a context. |

No Docker. Secrets stay in the environment or `/tmp/keycloak-smoke.env` (mode 0600, gitignored).

## How to run

```bash
# Always-green (no Keycloak)
mvn -pl nexus-p2-iam -am test \
  -Dtest=TotpTest,AuthorizationCodeDriverTest,KeycloakAdminSupportTest,G18StubAdminLoginTest,AdminMfaApiTest,AdminMfaSessionTest,MfaClaimsTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# Native Keycloak 26.5.2 + TENANT_ADMIN TOTP walk
# KEYCLOAK_HOME defaults to $HOME/keycloak-26.5.2
# Secrets: env or /tmp/keycloak-smoke.env (0600, not git)
./scripts/g17-admin-totp-enrollment.sh
```

`start-native-keycloak.sh` uses PostgreSQL when `127.0.0.1:5432` is up, otherwise Keycloak `start-dev` bundled **H2**. `--hostname-port` is not a 26.5.2 flag.

## Evidence

Recorded on this agent VM after the live walk. Default `mvn test` stays green without Keycloak (live class skips unless `NEXUS_KEYCLOAK_SMOKE=1`).

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| Keycloak | **26.5.2** native `kc.sh start-dev` (no Docker) |
| Walk | HTTP form driver, not Chromium |

| Check | Result |
|---|---|
| RFC 6238 TOTP helper + driver OTP / setup fixtures | see command log |
| Stub `TENANT_ADMIN` token still issues context (G18 path) | see command log |
| Live password-only admin token → `P2.IAM.MFA_REQUIRED` | see command log |
| Live `CONFIGURE_TOTP` enrollment stores Keycloak `otp` credential | see command log |
| Live password-only after enrollment stops on OTP form | see command log |
| Live password + TOTP issues `TenantContext`; `amr` contains `otp` | see command log |

Fill from `G17_ADMIN_TOTP_*` lines in the Surefire output after `./scripts/g17-admin-totp-enrollment.sh`.

## Honest leftovers (not claimed)

| Still open | Why |
|---|---|
| SMS MFA | `amr=sms` is recognized if an IdP sent it. No SMS SPI, no SMS OTP provider, no phone-number enrollment. |
| WebAuthn / hardware token / passkeys | Not implemented. `amr=hwk` is recognized only if an IdP sent it. No WebAuthn policy, no resident-key UX. |
| TOTP enrollment UX / Vue Keycloak otp write-back / recovery codes / email MFA | In-app UX leftover is [g17-totp-enrollment-ux.md](./g17-totp-enrollment-ux.md) (copy / error paths only). Vue write-back honesty leftover is [g17-vue-otp-writeback.md](./g17-vue-otp-writeback.md) (probe + honest SKIP). Recovery codes / email MFA leftover is [g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md). This walk remains the HTTP driver + required action. |
| Human browser click / Chromium | Same loopback `Secure; SameSite=None` cookie limit as the G4 PKCE leftover. |
| Forced OTP for every admin at the Keycloak flow layer | Product enforcement is the P2 gate. Conditional OTP runs only after a user has an OTP credential. Realm roles `TENANT_ADMIN` / `PLATFORM_ADMIN` are named, not bound as a Conditional OTP role rule. |
| Full G17 (密码机, classified GM delivery) | Not this slice. 三权分立 is [g17-separation-of-duties.md](./g17-separation-of-duties.md). Local 国密 TLS leftover is [g17-gm-tls-dual-cert.md](./g17-gm-tls-dual-cert.md). |
| G18 reopen | **No.** Stub IdP + fabricated admin MFA token is unchanged. |
| P0/M0 redo, Docker, SAML, LDAP/AD, Keycloak Organizations | Out of scope |

Same-TX `AuditPort` joining is unchanged. Default authorize URL still has no `code_challenge` (M0-04 confidential smoke).
