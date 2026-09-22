# G17 leftover: MFA recovery email / Vue OTP writeback remainder honesty evidence beyond #205/#172/#158

See [docs/g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g17-mfa-recovery-email-honesty.md](../g17-mfa-recovery-email-honesty.md) (#172), [g17-vue-otp-writeback.md](../g17-vue-otp-writeback.md) (#158), [g17-admin-mfa-recovery-email.md](../g17-admin-mfa-recovery-email.md) (#154), and this leftover. Also keep [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), and the other leftover rows already on `main`.

Recorded on this agent VM after rebase onto `origin/main` `f8060f2` (Merge #238; includes #239 / #237 / #232 / #212 / #230 / #231 / #229 / #226 / #228 / #221 / #225 / #224 / #223 / #222 / #216 / #220 / #215 / #210 / #217 / #209). **Not** a full G17 close. **Not** 等保 / 密评. **Not** a G18 reopen. `AuditPort` same-TX rules were not changed. Vue does **not** write otp / recovery-authn-codes into Keycloak. `#96` / `#118` / `#133` / `#154` / `#158` / `#172` / `#205` were not redone. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x** when present | leftover-off; collect `--skip-tests` when mvn is missing |
| Spring Boot parent | **4.1.1** | product BOM |
| Node | **22** | parent leftover harness verify-report |
| Keycloak SMTP / recovery-authn-codes / Vue write-back | not delivered | honest SKIP |
| Runtime | `./scripts/g17-mfa-recovery-vue-otp-remainder-honesty.sh` | No Docker / Compose |

## Commands

```bash
./scripts/g17-mfa-recovery-vue-otp-remainder-honesty.sh
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

Do **not** treat `./scripts/g17-mfa-recovery-vue-otp-honesty.sh`, `./scripts/g17-mfa-recovery-email-honesty.sh`, or `./scripts/g17-vue-otp-writeback.sh` as this leftover. Those scripts are the #205 / #172 / #158 contracts.

## Results (agent VM)

Filled after the leftover honesty remainder script on this VM at `2026-09-22T22:17:33Z`. Live output is gitignored under `.local/g17-mfa-recovery-vue-otp-remainder-honesty/`. A sanitized copy is [`g17-mfa-recovery-vue-otp-remainder-honesty/agent-vm.env`](./g17-mfa-recovery-vue-otp-remainder-honesty/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake delivery / write-back PASS / fake G17 close / redo rejected |
| #205 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #172 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #158 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| Live email OTP mailbox | **SKIP** | SMTP off; no mailbox observed |
| Live Keycloak recovery-authn-codes | **SKIP** | issuer unreachable / not enrolled |
| Live Vue → Keycloak otp write-back | **SKIP** | issuer unreachable and product endpoint absent |
| Product G17 / 等保 / 密评 / Vue→Keycloak otp | **NOT_CLAIMED** | leftovers stay open |
| Leftover rows kept | **true** | #205 + #172 + #158 + ALL sibling leftover rows including #238 / #239 / #237 / #232 / #212 / #230 / #231 / #229 / #226 / #228 / #221 / #225 / #223 / #222 / #216 / #220 / #215 / #210 / #217 / #209 |
| #96 / #118 / #133 / #154 / #158 / #172 / #205 | unchanged | not redone |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- A mailbox actually receiving an email OTP
- Keycloak `CONFIGURE_RECOVERY_AUTHN_CODES` required action
- Product Vue write-back endpoint that stores a Keycloak otp credential
- Writing otp / recovery-authn-codes from the Vue pages
- SMS MFA / WebAuthn
- Full G17 / 等保 / 密评 / 密码机
