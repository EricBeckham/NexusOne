# G17 leftover: admin MFA recovery codes / email MFA evidence

See [docs/g17-admin-mfa-recovery-email.md](../g17-admin-mfa-recovery-email.md).

Recorded on this agent VM after rebase onto `origin/main` `6ae90d0` (Merge #147). **Not** a full G17 close. **Not** 等保 / 密评. **Not** a G18 reopen. `AuditPort` same-TX rules were not changed. Vue does **not** write otp secrets into Keycloak. Leftover catalog rows `g4-pkce-spa-verifier` (#147) and `g17-admin-mfa-recovery-email` are both kept.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x** | filled after the test run |
| Spring Boot parent | **4.1.1** | product BOM |
| Node | **22** | Vue leftover |
| Keycloak SMTP / email OTP | not delivered | honest SKIP |
| Runtime | local `mvn test` + Vitest | No Docker / Compose |

## Commands

```bash
./scripts/g17/run-mfa-recovery-email.sh
```

## Results (agent VM)

Filled after the leftover harness on this VM.

| Check | Result | Notes |
|---|---|---|
| `RecoveryCodesTest` | pending | 8 hashed codes; consume once |
| `EmailMfaDeliveryProbeTest` | pending | SMTP off / no host / no Keycloak → SKIP |
| `MfaClaimsTest` email/mca/recovery | pending | recognized if an IdP sent them |
| `AuthorizationCodeDriverTest` recovery/email fixtures | pending | does not redo TOTP enroll walk |
| Vue recovery / email leftover | pending | Vitest + assert |
| #133 TOTP leftover still green | pending | `assert:g17-totp-enroll` |
| Email delivery | **SKIP** | `P2.IAM.SMTP_DISABLED` unless a live SMTP host is proven |
| Live Keycloak recovery-authn-codes | **SKIP** | not enrolled on this leftover |
| Product G17 / 等保 / 密评 / Vue→Keycloak otp | **NOT_CLAIMED** | leftovers stay open |

## Honest leftovers

- Writing otp / recovery-authn-codes from the Vue pages
- A mailbox actually receiving an email OTP
- SMS MFA / WebAuthn
- Full G17 / 等保 / 密评 / 密码机
