# G17 leftover — TOTP enrollment UX / copy / error paths

S0+S1 remainder after admin MFA (#96) and native Keycloak TOTP enrollment (#118). **G18 stays closed.** This slice does **not** redo `AdminMfaPolicy`, 三权分立, Nginx/Tongsuo, or `AuditPort` same-TX rules. It does **not** claim 等保 / 密评 / 完整 G17.

#118 proved a **native Keycloak 26.5.2 HTTP driver** walk (`CONFIGURE_TOTP` + OTP form). That leftover is still [g17-admin-totp-enrollment.md](./g17-admin-totp-enrollment.md). The leftover here is the **in-app authenticator page**: Vue copy, 6-digit confirm, and error paths. It is **not** a second authenticator stack and does **not** write a Keycloak `otp` credential from the browser.

Default Stub login still fabricates `acr=mfa` / `amr=otp` so the closed G18 walk issues a context.

## What this leftover covers

| Surface | Behavior |
|---|---|
| Login copy | `/login` keeps `data-testid=stub-login` (G18) and `data-testid=pkce-login` (#125). New `data-testid=admin-password-login` mints a password-only stub token. |
| `P2.IAM.MFA_REQUIRED` | Login card `data-testid=mfa-required-panel` explains the gate and links to `/p2/mfa/enroll?reason=mfa_required`. |
| Enrollment page | `/p2/mfa/enroll` (`data-testid=totp-start` / `totp-secret` / `totp-otpauth` / `totp-code` / `totp-confirm`). Manual secret + otpauth URI. No QR library. |
| Error paths | Blank / not-6-digits / wrong-or-expired / stale (>10 min) / no pending / already enrolled. Chinese copy in `web/src/auth/totpEnrollment.js`. |
| Confirm | RFC 6238 HmacSHA1 6 digits / 30s (`web/src/auth/totp.js`), same fixture as Java `Totp`. |
| Always-green | Vitest + happy-dom. Playwright + system Chrome against a login stub (no Docker, no Keycloak). |

Product enforcement stays the P2 session gate. #118 remains the live Keycloak credential walk.

## How to run

```bash
# Always-green Vue / copy / RFC 6238 (no JAR, no Keycloak)
cd web && npm install
npm run test:g17-totp-enroll
npm run assert:g17-totp-enroll

# Playwright + system Chrome + stub login (no Docker)
./scripts/g17/run-totp-enrollment-ux.sh
```

If Playwright or Chrome is missing, the walk writes `result=SKIP` and exits 0. Wiring asserts still run.

## Evidence

Fill from `./scripts/g17/run-totp-enrollment-ux.sh`. Artifact: [g17-totp-enrollment-ux/walk.json](./g17-totp-enrollment-ux/walk.json).

| Item | Value |
|---|---|
| JDK / Boot | OpenJDK **21** / Spring Boot **4.1.1** (unchanged; this leftover does not boot the JAR) |
| Node | **22** |
| Browser | system Chrome + `playwright-core` (no Docker, no bundled Chromium download) |
| Default stub login | unchanged (`acr=mfa`) — G18 stays closed |
| Desktop PKCE leftover | unchanged (`data-testid=pkce-login`) |
| `AuditPort` | **untouched** |
| Docker | no |

| Check | Result |
|---|---|
| RFC 6238 helper matches Java 6-digit fixture | see command log |
| Login `admin-password-login` → `P2.IAM.MFA_REQUIRED` panel | see command log |
| Format / wrong-code / success / already-enrolled | see command log |
| Stub `data-testid=stub-login` and `pkce-login` still present | see command log |

## Honest leftovers (not claimed)

| Still open | Why |
|---|---|
| Writing otp into Keycloak from this Vue page | Honesty leftover is [g17-vue-otp-writeback.md](./g17-vue-otp-writeback.md) (probe + honest SKIP). #118 HTTP driver remains the credential walk. |
| Recovery codes | Sibling leftover [g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md) issues local one-time codes. This page still does not write Keycloak recovery-authn-codes. |
| Email MFA product acceptance | Sibling leftover [g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md) is the request contract + honest SKIP. |
| SMS MFA / WebAuthn / passkeys | Not implemented. |
| Forced OTP at the Keycloak flow layer | Same as #118: product gate is P2. |
| Full G17 / 等保 / 密评 / 密码机 / 真实 Tongsuo 握手 | **Not claimed.** 三权 is [g17-separation-of-duties.md](./g17-separation-of-duties.md). Tongsuo dual-stack leftover is [g17-gm-tls-nginx-dual-stack.md](./g17-gm-tls-nginx-dual-stack.md). Handshake honesty leftover is [g17-gm-tls-nginx-handshake.md](./g17-gm-tls-nginx-handshake.md). |
| G18 reopen | **No.** Default stub token still fabricates MFA. |
| P0/M0 redo, Docker | Out of scope |
