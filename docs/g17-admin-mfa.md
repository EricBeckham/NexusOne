# G17 slice: platform / tenant admin forced MFA

S0+S1 PostgreSQL release gate for **ARCH-CMP-001**, **P1-M05-G01-F006**, **P2-M09-G04-F005**. This is the admin-MFA policy + session gate only. **G18 stays closed** and is not reopened. This is **not** a full G17 close (三权分立, 国密 TLS, classified delivery).

Reuse: M0-04 `nexus-p2-iam` session path and Keycloak **26.5.2**. No second IdP. No Docker. Passwords stay in the environment.

## Product policy (default forced)

`nexus.iam.mfa.admin-required=true` (override `NEXUS_IAM_MFA_ADMIN_REQUIRED`).

Admin positions (case-insensitive): `TENANT_ADMIN`, `PLATFORM_ADMIN`, `SYSTEM_ADMIN`, `SECURITY_ADMIN`, `AUDIT_ADMIN`. Platform tenant id defaults to the P1 reserved id `01900000-0000-7000-8000-000000000001`.

The same Keycloak 26.5.2 confidential client is used. Realm import `nexus-realm.json` now sets TOTP policy defaults and names the `TENANT_ADMIN` / `PLATFORM_ADMIN` realm roles. Product enforcement is the P2 session gate, not a second authenticator stack.

`GET /api/v1/auth/authorize-url?acr=mfa` adds `acr_values=mfa` on the existing authorize URL (Keycloak step-up hint).

## Session gate

`SessionService.issue` / `switchTenant` and `loadContext(identity)` reject a privileged session when the account holds an admin position and the access token does not satisfy MFA (`acr` / `amr`).

| Token | Actor | Privileged context |
|---|---|---|
| Password-only (no `acr=mfa` / OTP `amr`) | Tenant or platform admin | **Rejected** `P2.IAM.MFA_REQUIRED` (403). No `TenantContext` issued. |
| MFA-satisfied (`acr=mfa` / `amr` contains `otp`) | Admin | Issued |
| Password-only | Non-admin member | Issued (MFA not forced) |

Login reject is audited as `p2.auth.login.rejected` with `details.code=P2.IAM.MFA_REQUIRED`.

## Stub IdP leftover (G18 walk stays closed)

Default `StubIdentityProvider.token(...)` and the Vue stub login fabricate `acr=mfa` / `amr=otp`. That is **not** a TOTP challenge. It exists so the already-closed G18 stub-IdP admin walk (`docs/g18-business-path.md`) still issues a context.

`StubIdentityProvider.tokenWithoutMfa(...)` is the honest password-only token used by the evidence tests. Non-admin demo seeds are not forced through MFA; that skip is only for non-admin members.

## Evidence

```bash
mvn -pl nexus-p2-iam -am test \
  -Dtest=AdminMfaSessionTest,AdminMfaApiTest,MfaClaimsTest,SessionLoginAuditTest,IdentityApiTest,DualTenantIsolationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Recorded **2026-09-22T16:45:36Z** on the agent VM (OpenJDK 21.0.10 / Maven 3.8.7 / Boot 4.1.1): **BUILD SUCCESS**, 23 run / 0 fail. Full `mvn -pl nexus-p2-iam -am test` at **2026-09-22T16:45:52Z**: **36** run / **0** fail / **3** skip. Command log: `nexus-p2-iam/EVIDENCE.md`.

Failing-before / passing-after is `AdminMfaSessionTest`:

1. `AdminMfaPolicy.disabled()` + admin `tokenWithoutMfa` → context issued (pre-gate).
2. `AdminMfaPolicy.forced()` + same token → `P2.IAM.MFA_REQUIRED`, no context.
3. Forced + MFA token → context issued.
4. Forced + non-admin password-only → context issued.

## Not claimed

| Item | Status |
|---|---|
| Full G17 (三权分立, 国密 TLS 双证书, 密码机, classified GM delivery) | Not this slice |
| G18 reopen | **No.** Product G18 remains closed. |
| SAML | Not run |
| SMS MFA / hardware token / WebAuthn | Not implemented. `amr=sms` is recognized if an IdP sent it; no SMS SPI. |
| TOTP enrollment UX / recovery codes / email MFA product acceptance | Not this slice |
| Browser Authorization Code + PKCE full walk | Not done. Product login stays the confidential client. |
| Live Keycloak admin OTP challenge (carol smoke is password-only, not admin) | Not run |
| LDAP / AD, Keycloak Organizations, Docker | Out of scope |
| P0/M0 redo | Not redone |
