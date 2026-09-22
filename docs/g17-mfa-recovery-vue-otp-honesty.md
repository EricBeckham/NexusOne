# G17 leftover — MFA recovery email / Vue OTP writeback honesty beyond #172/#158

S0+S1 remainder after the #172 recovery-email honesty leftover ([g17-mfa-recovery-email-honesty.md](./g17-mfa-recovery-email-honesty.md)) and the #158 Vue otp write-back leftover ([g17-vue-otp-writeback.md](./g17-vue-otp-writeback.md)). Rebased onto `origin/main` `d3a197a` (Merge #197 G9 Redis-down invalidate leftover honesty; includes #204 G2 H5/form.write leftover honesty, #206 tip-sync, #203 G4 export-wizard deny leftover honesty, #202 G10 file-reconcile/drift leftover honesty, #196 G5 mid-publish/browser leftover honesty, #195 G3 Outbox/claim-crash leftover honesty, #191 G1 BoundSql/COUNT leftover honesty, #198 G15 WeChat/DingTalk + native-bridge leftover honesty, and #200 S0 rotate leftover honesty). **G18 stays closed.** This leftover does **not** redo #96 `AdminMfaPolicy`, #118 HTTP TOTP, #133 enrollment UX, #154 local recovery codes / email request contract, #158 Vue otp write-back product code, or #172 recovery-email honesty product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** write otp / recovery-authn-codes from Vue. It does **not** claim 等保 / 密评 / 完整 G17. G17 close is **NOT_CLAIMED**.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g17-mfa-recovery-email-honesty` (#172), `g17-vue-otp-writeback` (#158), `g17-admin-mfa-recovery-email` (#154), `g9-redis-down-invalidate-remainder` (#197), `g2-h5-every-page-form-write-honesty` (#204), `g4-export-wizard-deny-honesty` (#203), `g10-leftover-honesty` (#202), `g5-mid-publish-failure-honesty` (#196), `g3-outbox-consume-crash-honesty` (#195), `g3-outbox-consume-idempotent` (#139), `g3-claim-crash-window` (#170), `g1-queryast-boundsql-honesty` (#191), `g1-queryast-boundsql-guard` (#146), `g15-wx-dt-native-bridge-honesty` (#198), `s0-secret-rotate-without-restart-honesty` (#200), `g7-query-expense-travel-honesty` (#192), `linux-arm-profile-leftover-honesty` (#193), `g4-p1-staging-audit-events-honesty` (#190), `g6-recovery-port-dry-run-honesty` (#186), `s0s1-live-redis-minio-isolation-honesty` (#185), `g17-separation-honesty` (#187), `g4-hot-purge-90d-honesty` (#183), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g15-wx-dt-prod-gap-honesty` (#167), `g4-pkce-spa-verifier-honesty` (#177), `s0-secret-getenv-snapshot-honesty` (#180), `g5-browser-failed-publish` (#171), `g7-withdraw-browser-honesty` (#175), `g2-form-write-honesty` (#173), `g9-redis-down-invalidate-honesty` (#174), and `g10-file-reconcile-honesty` (#169). Keep this file.

#172 already is the honesty leftover for live Keycloak `recovery-authn-codes` and SMTP / email OTP. #158 already is the honesty leftover for Vue confirm ≠ Keycloak otp write-back. The leftover here is the **combined honesty gate** beyond both: leftover harness contracts stay always-green **PASS**. Live email delivery, live recovery-authn-codes, and live Vue otp write-back are **PASS** only when they were actually observed. Missing live IdP / SMTP is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Surface | Behavior |
|---|---|
| #172 recovery-email honesty | Already on `main`. Local recovery codes + email request contract stay always-green. Live SMTP / recovery-authn-codes SKIP unless observed. Do **not** redo. |
| #158 Vue otp write-back | Already on `main`. Vue confirm stays local. Product write-back endpoint stays absent. Live IdP SKIP unless observed. Do **not** redo. |
| Leftover harness contract | Always-green `leftover_172_harness=PASS` and `leftover_158_harness=PASS` when the parent honesty fixtures still verify. |
| Email delivery honesty | `email_delivery=PASS` only with SMTP on + host + reachable issuer + mailbox observed. Default SMTP off → `SKIP` `P2.IAM.SMTP_DISABLED`. |
| Recovery-authn-codes honesty | `recovery_live=PASS` only with reachable issuer + OIDC discovery + enrolled `CONFIGURE_RECOVERY_AUTHN_CODES` + a consumed live recovery-authn-code. Otherwise `SKIP`. |
| Vue otp write-back honesty | `vue_otp_writeback=PASS` only with reachable issuer + OIDC discovery + product Vue write-back endpoint + a stored otp credential. Otherwise `SKIP`. |
| Honesty gate | `--verify-report` rejects fake `email_delivery=PASS` / `recovery_live=PASS` / `vue_otp_writeback=PASS` without those proofs, and rejects 等保 / 密评 / HSM / G17-close / G18-reopen / #96/#118/#133/#154/#158/#172 redo claims. |
| Sibling leftovers | `/p2/mfa/recovery` + `/p2/mfa/email` (#154/#172) and `/p2/mfa/enroll` write-back (#158) stay. |

Product enforcement stays the P2 session gate. Vue never POSTs otp / recovery secrets to Keycloak. This leftover does not send mail. It does not start Keycloak, SMTP, or an MTA.

## How to run

```bash
# Always-green parent harness + live IdP/SMTP probe (honest SKIP when unavailable)
./scripts/g17-mfa-recovery-vue-otp-honesty.sh

# Honesty gate only
./scripts/g17-mfa-recovery-vue-otp-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-mfa-recovery-vue-otp-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-honesty/fixtures/fake-delivery-pass.env
./scripts/g17-mfa-recovery-vue-otp-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-honesty/fixtures/fake-writeback-pass.env
./scripts/g17-mfa-recovery-vue-otp-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-honesty/fixtures/fake-g17-close.env
./scripts/g17-mfa-recovery-vue-otp-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-honesty/fixtures/fake-redone.env
```

Parent leftover scripts stay as reproduction commands. This leftover does **not** invoke them as a product redo (parent `--verify-report` on honest fixtures is allowed as harness evidence):

```bash
# do not run as this leftover
# ./scripts/g17-mfa-recovery-email-honesty.sh
# ./scripts/g17-vue-otp-writeback.sh
```

`PASS` for live email requires all of: `nexus.iam.smtp.enabled=true`, SMTP host, reachable Keycloak issuer, and a mailbox that actually received an OTP. `PASS` for live recovery requires all of: reachable issuer, OIDC discovery, enrolled `CONFIGURE_RECOVERY_AUTHN_CODES`, and a consumed live recovery-authn-code. `PASS` for Vue write-back requires all of: reachable issuer, OIDC discovery, a product Vue write-back endpoint, and a visible stored otp credential. This leftover does not add that product endpoint.

See [engineering/g17-mfa-recovery-vue-otp-honesty-EVIDENCE.md](./engineering/g17-mfa-recovery-vue-otp-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g17-mfa-recovery-vue-otp-honesty/`.

## Evidence

| Item | Value |
|---|---|
| JDK / Boot | OpenJDK **21** / Spring Boot **4.1.1** |
| Node | **22** |
| Keycloak SMTP / recovery-authn-codes / Vue write-back | probe only. This VM: **not delivered** |
| Default stub login | unchanged (`acr=mfa`) — G18 stays closed |
| `#96` / `#118` / `#133` / `#154` / `#158` / `#172` | **not redone** |
| `AuditPort` | **untouched** |
| Vue → Keycloak otp / recovery-authn-codes | **no** |
| Docker | no |

| Check | Result |
|---|---|
| #172 leftover honesty fixture | always-green **PASS** |
| #158 leftover honesty fixture | always-green **PASS** |
| Live email OTP mailbox | honest `SKIP` unless mailbox observed |
| Live Keycloak recovery-authn-codes | honest `SKIP` unless enrolled + consumed |
| Live Vue → Keycloak otp write-back | honest `SKIP` unless product endpoint + stored credential |
| Fake `PASS` / 等保 / 密评 / G17-close / redo fixtures | rejected |
| Product G17 / 等保 / 密评 | **NOT_CLAIMED** |

## What this does not claim

| Leftover | Why |
|---|---|
| Full G17 / 等保 / 密评 | Explicitly `g17_close=NOT_CLAIMED`, `dengbao_claimed=false`, `mipin_claimed=false`. |
| Redo of #172 / #158 | Parent leftover honesty packages stay siblings. `redo_172=false`, `redo_158=false`. |
| #154 request contract | `redo_154=false`. Local codes + SMTP-off SKIP stay on that leftover. |
| Writing otp / recovery-authn-codes from Vue | #118 remains the credential walk. #158 remains the write-back honesty leftover. |
| Email OTP actually delivered to an inbox | Product SMTP is invite / reset only and defaults off. |
| Keycloak `CONFIGURE_RECOVERY_AUTHN_CODES` | This leftover does not enroll it. |
| SMS MFA / WebAuthn / passkeys | Not implemented. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |
