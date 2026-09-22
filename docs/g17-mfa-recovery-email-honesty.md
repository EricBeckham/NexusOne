# G17 leftover — admin MFA recovery-code / email delivery honesty beyond #154

S0+S1 remainder after the #154 request contract ([g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md)). Rebased onto `origin/main` `3cdccca` (after #165 claim-list tip align). **G18 stays closed.** This leftover does **not** redo #96 `AdminMfaPolicy`, #118 HTTP TOTP, #133 enrollment UX, #154 local recovery codes / email request contract, #158 Vue otp write-back, or #160 Tongsuo handshake. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** write otp / recovery-authn-codes from Vue. It does **not** claim 等保 / 密评 / 完整 G17. G17 close is **NOT_CLAIMED**.

Keep **both** leftover rows in [docs/README.md](./README.md): `g17-admin-mfa-recovery-email` (#154) and this file. Also keep newly landed sibling leftovers `g15-wx-dt-prod-gap-honesty` (#167), `g4-export-wizard-deny` (#166), and `g10-file-reconcile-honesty` (#169).

#154 already issues local one-time recovery codes and an email MFA **request** contract that SKIPs when SMTP is off. The leftover here is the **honesty gate** for live delivery: Keycloak `recovery-authn-codes` and SMTP / email OTP are **PASS** only when they were actually observed. Missing Keycloak recovery-authn-codes or SMTP is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Surface | Behavior |
|---|---|
| #154 local contract | `RecoveryCodes` issue / consume / reuse and `requestEmailOtp` SMTP-off SKIP stay always-green. Not rewritten. |
| Email delivery honesty | `probeEmailDeliveryHonesty`. `PASS` only with SMTP on + host + reachable issuer + mailbox observed. Default `nexus.iam.smtp.enabled=false` → `SKIP` `P2.IAM.SMTP_DISABLED`. |
| Recovery-authn-codes honesty | `probeRecoveryAuthnHonesty`. `PASS` only with reachable issuer + OIDC discovery + `CONFIGURE_RECOVERY_AUTHN_CODES` enrolled + a live recovery-authn-code consumed. Otherwise `SKIP`. |
| Honesty gate | `--verify-report` rejects fake `email_delivery=PASS` / `recovery_live=PASS` without those proofs, and rejects 等保 / 密评 / HSM / G17-close / G18-reopen / #96/#118/#133/#154/#158/#160 redo claims. |
| Sibling leftovers | `/p2/mfa/recovery` + `/p2/mfa/email` (#154) and `/p2/mfa/enroll` write-back (#158) stay. |

Product enforcement stays the P2 session gate. Vue never POSTs otp / recovery secrets to Keycloak. This leftover does not send mail.

## How to run

```bash
# Always-green #154 leftover + honesty probe (honest SKIP when SMTP / Keycloak unavailable)
./scripts/g17-mfa-recovery-email-honesty.sh

# Honesty gate only
./scripts/g17-mfa-recovery-email-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-email-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-mfa-recovery-email-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-email-honesty/fixtures/fake-delivery-pass.env
./scripts/g17-mfa-recovery-email-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-email-honesty/fixtures/fake-g17-close.env
```

Equivalent always-green:

```bash
cd web
npm install
npm run test:g17-mfa-recovery-email
npm run assert:g17-mfa-recovery-email
npm run test:g17-mfa-recovery-email-honesty
npm run assert:g17-mfa-recovery-email-honesty
```

`PASS` for live email requires all of: `nexus.iam.smtp.enabled=true`, SMTP host, reachable Keycloak issuer, and a mailbox that actually received an OTP. `PASS` for live recovery requires all of: reachable issuer, OIDC discovery, enrolled `CONFIGURE_RECOVERY_AUTHN_CODES`, and a consumed live recovery-authn-code. This leftover does not start Keycloak, SMTP, or an MTA.

The #154 request-contract leftover is unchanged as a sibling:

```bash
./scripts/g17/run-mfa-recovery-email.sh
```

See [engineering/g17-mfa-recovery-email-honesty-EVIDENCE.md](./engineering/g17-mfa-recovery-email-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g17-mfa-recovery-email-honesty/`.

## Evidence

| Item | Value |
|---|---|
| JDK / Boot | OpenJDK **21** / Spring Boot **4.1.1** |
| Node | **22** |
| Keycloak SMTP / recovery-authn-codes | probe only. This VM: **not delivered** |
| Default stub login | unchanged (`acr=mfa`) — G18 stays closed |
| `#96` / `#118` / `#133` / `#154` / `#158` / `#160` | **not redone** |
| `AuditPort` | **untouched** |
| Vue → Keycloak otp / recovery-authn-codes | **no** |
| Docker | no |

| Check | Result |
|---|---|
| #154 recovery issue / consume / reuse | always-green |
| #154 email request without SMTP | `SKIP` `P2.IAM.SMTP_DISABLED` |
| Live email OTP mailbox | honest `SKIP` unless mailbox observed |
| Live Keycloak recovery-authn-codes | honest `SKIP` unless enrolled + consumed |
| Fake `PASS` / 等保 / 密评 / G17-close fixtures | rejected |
| Product G17 / 等保 / 密评 | **NOT_CLAIMED** |

## What this does not claim

| Leftover | Why |
|---|---|
| Full G17 / 等保 / 密评 | Explicitly `g17_close=NOT_CLAIMED`, `dengbao_claimed=false`, `mipin_claimed=false`. |
| #154 request contract | `redo_154=false`. Local codes + SMTP-off SKIP stay on that leftover. |
| Writing otp / recovery-authn-codes from Vue | #118 remains the credential walk. #158 is the write-back honesty leftover. |
| Email OTP actually delivered to an inbox | Product SMTP is invite / reset only and defaults off. |
| Keycloak `CONFIGURE_RECOVERY_AUTHN_CODES` | Driver can classify the form. This leftover does not enroll it. |
| SMS MFA / WebAuthn / passkeys | Not implemented. |
| #160 Tongsuo/Nginx handshake | Sibling leftover. `redo_160=false`. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |
