# G17 leftover — admin MFA recovery codes / email MFA path

S0+S1 remainder after admin MFA (#96), native Keycloak TOTP (#118), and in-app TOTP UX (#133). **G18 stays closed.** This slice **extends** those leftovers. It does **not** rewrite `AdminMfaPolicy`, the #118 HTTP driver TOTP walk, or the #133 6-digit enrollment page. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** write an otp secret back into Keycloak from Vue. It does **not** claim 等保 / 密评 / 完整 G17.

#133 still proves in-app TOTP copy / error paths. #118 still proves the live Keycloak `CONFIGURE_TOTP` HTTP walk. The leftover here is **recovery codes** (local one-time codes) plus an **email MFA request contract** that **honestly SKIP**s when this VM cannot deliver mail or Keycloak recovery-authn-codes.

## What this leftover covers

| Surface | Behavior |
|---|---|
| `amr` recognition | `MfaClaims` now treats `email` / `mca` / `recovery` / `recovery-authn-code(s)` as MFA-satisfied **if an IdP sent them**. No SMS/email SPI. Same pattern as existing `amr=sms`. |
| Recovery codes (local) | `RecoveryCodes` + `web/src/auth/recoveryCodes.js`. 8× `XXXX-XXXX`, SHA-256 hashed, one-time consume. Vue `/p2/mfa/recovery`. |
| Email MFA request | `web/src/auth/emailMfa.js` + `/p2/mfa/email`. Default `nexus.iam.smtp.enabled=false` → `P2.IAM.SMTP_DISABLED` **SKIP**. Does not send mail. |
| HTTP driver kinds | `AuthorizationCodeDriver` classifies Keycloak recovery-code / email-OTP **fixtures**. Live delivery is not enabled here. |
| Delivery probe | `EmailMfaDeliveryProbe` + `./scripts/g17/run-mfa-recovery-email.sh`. Keycloak SMTP / `CONFIGURE_RECOVERY_AUTHN_CODES` / local MTA → **SKIP** with a reason when they cannot run. |
| Sibling leftovers | `/p2/mfa/enroll` (#133) and `stub-login` / `pkce-login` stay. Login panel still routes `P2.IAM.MFA_REQUIRED` to TOTP enroll. |

Product enforcement stays the P2 session gate. Vue never POSTs otp / recovery secrets to Keycloak.

## How to run

```bash
# Always-green Java + Vue leftover (no JAR Keycloak write)
cd web && npm install
npm run test:g17-mfa-recovery-email
npm run assert:g17-mfa-recovery-email

# Harness: Java + Vue + delivery probe + optional Playwright
./scripts/g17/run-mfa-recovery-email.sh
```

If Playwright or Chrome is missing, the browser walk writes `result=SKIP` and the harness still exits 0 after always-green tests. Email / live recovery delivery is **SKIP** unless a real SMTP host **and** Keycloak email OTP / recovery-authn-codes are proven — this leftover does not turn that into a PASS.

## Evidence

Fill from `./scripts/g17/run-mfa-recovery-email.sh`. Artifacts: [g17-admin-mfa-recovery-email/walk.json](./g17-admin-mfa-recovery-email/walk.json), [engineering/g17-admin-mfa-recovery-email-EVIDENCE.md](./engineering/g17-admin-mfa-recovery-email-EVIDENCE.md).

| Item | Value |
|---|---|
| JDK / Boot | OpenJDK **21** / Spring Boot **4.1.1** |
| Node | **22** |
| Browser | system Chrome + `playwright-core` when present (no Docker) |
| Default stub login | unchanged (`acr=mfa`) — G18 stays closed |
| `AuditPort` | **untouched** |
| Vue → Keycloak otp write | **no** |
| Docker | no |

| Check | Result |
|---|---|
| Recovery issue / format / consume / reuse | always-green (see command log) |
| `amr=email` / `mca` / `recovery-authn-code` recognized | always-green |
| Email request without SMTP | `SKIP` `P2.IAM.SMTP_DISABLED` |
| Live Keycloak recovery-authn-codes / email OTP delivery | **SKIP** unless proven; default probe records a reason |
| #133 TOTP enroll leftover still present | `assert:g17-totp-enroll` |

## Honest leftovers (not claimed)

| Still open | Why |
|---|---|
| Writing otp / recovery-authn-codes into Keycloak from Vue | Explicitly out of scope. #118 remains the credential walk. |
| Email OTP actually delivered to an inbox | Product SMTP is invite / reset only and defaults off. No MTA / Keycloak SMTP on this leftover. |
| Keycloak `CONFIGURE_RECOVERY_AUTHN_CODES` required action | Driver can classify the form. This VM does not enroll it. |
| SMS MFA / WebAuthn / passkeys | Not implemented. |
| Forced OTP at the Keycloak flow layer | Same as #118: product gate is P2. |
| Full G17 / 等保 / 密评 / 密码机 / 真实 Tongsuo 握手 | **Not claimed.** Handshake honesty leftover is [g17-gm-tls-nginx-handshake.md](./g17-gm-tls-nginx-handshake.md). Dual-stack leftover is [g17-gm-tls-nginx-dual-stack.md](./g17-gm-tls-nginx-dual-stack.md). |
| G18 reopen | **No.** Default stub token still fabricates MFA. |
| P0/M0 redo, Docker, AuditPort same-TX | Out of scope |
