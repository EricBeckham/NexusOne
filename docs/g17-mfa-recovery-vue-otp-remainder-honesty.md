# G17 leftover — MFA recovery email / Vue OTP writeback remainder honesty beyond #205/#172/#158

S0+S1 remainder **after** the #205 combined recovery-email / Vue otp honesty leftover ([g17-mfa-recovery-vue-otp-honesty.md](./g17-mfa-recovery-vue-otp-honesty.md)), the #172 recovery-email honesty leftover ([g17-mfa-recovery-email-honesty.md](./g17-mfa-recovery-email-honesty.md)), and the #158 Vue otp write-back leftover ([g17-vue-otp-writeback.md](./g17-vue-otp-writeback.md)). Rebased onto `origin/main` `f8060f2` (Merge #238 G1 FieldPolicy mask leftover honesty after #239 G7 expense/travel query leftover honesty / #237 G9 Redis-down/invalidate leftover honesty / #232 G2 H5/form.write leftover honesty / #212 G7 withdraw browser-evidence honesty / #230 G4 hot-purge/staging leftover honesty / #231 S0+S1 claim-list tip-sync / #229 G9 live multi-JVM replica remainder honesty / #226 G3 Outbox leftover honesty). **G18 stays closed.** This leftover does **not** redo #96 `AdminMfaPolicy`, #118 HTTP TOTP, #133 enrollment UX, #154 local recovery codes / email request contract, #158 Vue otp write-back product code, #172 recovery-email honesty product code, or #205 combined honesty leftover product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** write otp / recovery-authn-codes from Vue. It does **not** claim 等保 / 密评 / 完整 G17. G17 close is **NOT_CLAIMED**. P0/M0 are **not** redone.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g17-mfa-recovery-vue-otp-honesty` (#205), `g17-mfa-recovery-email-honesty` (#172), `g17-vue-otp-writeback` (#158), `g17-admin-mfa-recovery-email` (#154), `g1-fieldpolicy-mask-second-auth-remainder-honesty` (#238), `g7-query-expense-travel-honesty-remainder` (#239), `g9-redis-down-invalidate-remainder-honesty` (#237), `g2-h5-every-page-form-write-remainder-honesty` (#232), `g7-withdraw-browser-evidence-honesty` (#212), `g4-hot-purge-staging-leftover-honesty` (#230), `g9-live-multi-jvm-replica-remainder-honesty` (#229), `g3-outbox-consume-idempotent-honesty` (#226), `g10-leftover-honesty-remainder` (#228), `g2-report-physical-dump-honesty` (#221), `g6-recovery-port-dry-run-remainder-honesty` (#225), `s0-secret-rotate-getenv-remainder-honesty` (#223), `g4-pkce-spa-verifier-remainder-honesty` (#222), `g17-separation-leftover-honesty` (#216), `g15-wx-dt-native-bridge-remainder` (#220), `g1-queryast-export-honesty` (#215), `g17-gm-tls-nginx-handshake-honesty` (#210), `g5-publishhook-ddl-honesty` (#217), `g9-sentinel-cluster-remainder-honesty` (#209), `g17-mfa-recovery-vue-otp-honesty` (#205), `g2-ops-pg-dump-honesty` (#188), `g2-h5-every-page-form-write-honesty` (#204), `g4-export-wizard-deny-honesty` (#203), `g10-leftover-honesty` (#202), `g5-mid-publish-failure-honesty` (#196), `g3-outbox-consume-crash-honesty` (#195), `g1-queryast-boundsql-honesty` (#191), `g15-wx-dt-native-bridge-honesty` (#198), `s0-secret-rotate-without-restart-honesty` (#200), `g9-redis-down-invalidate-remainder` (#197), `linux-arm-profile-leftover-honesty` (#193), `g7-query-expense-travel-honesty` (#192), `g4-p1-staging-audit-events-honesty` (#190), `g6-recovery-port-dry-run-honesty` (#186), `s0s1-live-redis-minio-isolation-honesty` (#185), `g17-separation-honesty` (#187), `g4-hot-purge-90d-honesty` (#183), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g4-pkce-spa-verifier-honesty` (#177), `g5-browser-failed-publish` (#171), `g7-withdraw-browser-honesty` (#175), `g2-form-write-honesty` (#173), `g9-redis-down-invalidate-honesty` (#174), and `g10-file-reconcile-honesty` (#169). Keep this file.

#205 already is the combined honesty leftover beyond #172/#158: leftover harness contracts stay always-green **PASS**; live email delivery, live recovery-authn-codes, and live Vue otp write-back are honest **SKIP** unless observed. The leftover here is the **honesty remainder beyond all three**: parent leftover harness fixtures stay always-green **PASS**. Live email delivery, live recovery-authn-codes, and live Vue otp write-back are **PASS** only when they were actually observed. Missing live IdP / SMTP is honest **SKIP**. It never fabricates `PASS`. A listed observation env flag is not observation.

## What this leftover covers

| Surface | Behavior |
|---|---|
| #205 combined honesty | Already on `main`. Parent `--verify-report` fixtures stay **PASS**. Do **not** redo the #205 leftover or its product code. `leftover_205_harness=PASS`. `leftover_205_cells_honest=PASS`. |
| #172 recovery-email honesty | Already on `main`. Local recovery codes + email request contract stay always-green. Live SMTP / recovery-authn-codes SKIP unless observed. Do **not** redo. `leftover_172_harness=PASS`. |
| #158 Vue otp write-back | Already on `main`. Vue confirm stays local. Product write-back endpoint stays absent. Live IdP SKIP unless observed. Do **not** redo. `leftover_158_harness=PASS`. |
| Email delivery honesty | `email_delivery=PASS` only with SMTP on + host + reachable issuer + mailbox observed. Default SMTP off → `SKIP` `P2.IAM.SMTP_DISABLED`. |
| Recovery-authn-codes honesty | `recovery_live=PASS` only with reachable issuer + OIDC discovery + enrolled `CONFIGURE_RECOVERY_AUTHN_CODES` + a consumed live recovery-authn-code. Otherwise `SKIP`. |
| Vue otp write-back honesty | `vue_otp_writeback=PASS` only with reachable issuer + OIDC discovery + product Vue write-back endpoint + a stored otp credential. Otherwise `SKIP`. |
| Honesty gate | `--verify-report` rejects fake `email_delivery=PASS` / `recovery_live=PASS` / `vue_otp_writeback=PASS` without those proofs, and rejects 等保 / 密评 / HSM / G17-close / G18-reopen / #96/#118/#133/#154/#158/#172/#205 redo claims. Requires leftover rows kept. |
| Leftover rows | `docs/README.md` keeps #205 / #172 / #158 plus this remainder and **ALL** sibling leftover rows, including #230 / #231 / #229 / #226 / #228 / #221 / #225 / #223 / #224 / #222 / #216 / #220 / #215 / #210 / #217 / #209. |

Product enforcement stays the P2 session gate. Vue never POSTs otp / recovery secrets to Keycloak. This leftover does not send mail. It does not start Keycloak, SMTP, or an MTA. Do **not** run `./scripts/g17-mfa-recovery-vue-otp-honesty.sh`, `./scripts/g17-mfa-recovery-email-honesty.sh`, or `./scripts/g17-vue-otp-writeback.sh` as this leftover (parent `--verify-report` on honest fixtures is allowed as harness evidence).

## How to run (no Docker)

```bash
# Always-green parent harness + honest live IdP/SMTP SKIP
./scripts/g17-mfa-recovery-vue-otp-remainder-honesty.sh

# Honesty gate only
./scripts/g17-mfa-recovery-vue-otp-remainder-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-mfa-recovery-vue-otp-remainder-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-remainder-honesty/fixtures/fake-delivery-pass.env
./scripts/g17-mfa-recovery-vue-otp-remainder-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-remainder-honesty/fixtures/fake-writeback-pass.env
./scripts/g17-mfa-recovery-vue-otp-remainder-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-remainder-honesty/fixtures/fake-g17-close.env
./scripts/g17-mfa-recovery-vue-otp-remainder-honesty.sh --verify-report \
  scripts/g17-mfa-recovery-vue-otp-remainder-honesty/fixtures/fake-redone.env
```

Equivalent always-green Java (does **not** redo #205 / #172 / #158 suites):

```bash
mvn -pl nexus-p2-iam -am test \
  -Dtest=G17MfaRecoveryVueOtpRemainderHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The existing leftover scripts stay as reproduction commands:

```bash
# do not run for this leftover
# ./scripts/g17-mfa-recovery-vue-otp-honesty.sh
# ./scripts/g17-mfa-recovery-email-honesty.sh
# ./scripts/g17-vue-otp-writeback.sh
```

`PASS` for live email requires all of: `nexus.iam.smtp.enabled=true`, SMTP host, reachable Keycloak issuer, and a mailbox that actually received an OTP. `PASS` for live recovery requires all of: reachable issuer, OIDC discovery, enrolled `CONFIGURE_RECOVERY_AUTHN_CODES`, and a consumed live recovery-authn-code. `PASS` for Vue write-back requires all of: reachable issuer, OIDC discovery, a product Vue write-back endpoint, and a visible stored otp credential. This leftover does not add that product endpoint.

See [engineering/g17-mfa-recovery-vue-otp-remainder-honesty-EVIDENCE.md](./engineering/g17-mfa-recovery-vue-otp-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g17-mfa-recovery-vue-otp-remainder-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G17 / 等保 / 密评 | Explicitly `g17_close=NOT_CLAIMED`, `dengbao_claimed=false`, `mipin_claimed=false`. Honesty gate rejects `CLOSED`. |
| Redo of #205 / #172 / #158 | Parent leftover honesty packages stay siblings. `redo_205=false`, `redo_172=false`, `redo_158=false`. |
| #154 request contract | `redo_154=false`. Local codes + SMTP-off SKIP stay on that leftover. |
| Writing otp / recovery-authn-codes from Vue | #118 remains the credential walk. #158 remains the write-back honesty leftover. #205 remains the combined leftover. |
| Email OTP actually delivered to an inbox | Product SMTP is invite / reset only and defaults off. |
| Keycloak `CONFIGURE_RECOVERY_AUTHN_CODES` | This leftover does not enroll it. |
| SMS MFA / WebAuthn / passkeys | Not implemented. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- A mailbox actually receiving an email OTP
- Keycloak `CONFIGURE_RECOVERY_AUTHN_CODES` required action
- Product Vue write-back endpoint that stores a Keycloak otp credential
- Writing otp / recovery-authn-codes from the Vue pages
- SMS MFA / WebAuthn
- Full G17 / 等保 / 密评 / 密码机
- Redo of #96 / #118 / #133 / #154 / #158 / #172 / #205
