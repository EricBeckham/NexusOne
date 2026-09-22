# G17 leftover: MFA recovery email / Vue OTP writeback honesty evidence beyond #172/#158

See [docs/g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g17-mfa-recovery-email-honesty.md](../g17-mfa-recovery-email-honesty.md) (#172), [g17-vue-otp-writeback.md](../g17-vue-otp-writeback.md) (#158), [g17-admin-mfa-recovery-email.md](../g17-admin-mfa-recovery-email.md) (#154), and this leftover. Also keep [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g3-outbox-consume-idempotent.md](../g3-outbox-consume-idempotent.md) (#139), [g3-claim-crash-window.md](../g3-claim-crash-window.md) (#170), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g1-queryast-boundsql-guard.md](../g1-queryast-boundsql-guard.md) (#146), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g15-wx-dt-prod-gap-honesty.md](../g15-wx-dt-prod-gap-honesty.md) (#167), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), and [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169).

Recorded on this agent VM after rebase onto `origin/main` `cb0f339` (Merge #206 tip-sync after #203; includes #202 / #196 / #195 / #191 / #198 / #200). **Not** a full G17 close. **Not** 等保 / 密评. **Not** a G18 reopen. `AuditPort` same-TX rules were not changed. Vue does **not** write otp / recovery-authn-codes into Keycloak. `#96` / `#118` / `#133` / `#154` / `#158` / `#172` were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x** when present | leftover-off; collect `--skip-tests` on this VM |
| Spring Boot parent | **4.1.1** | product BOM |
| Node | **22** | parent leftover harness verify-report |
| Keycloak SMTP / recovery-authn-codes / Vue write-back | not delivered | honest SKIP |
| Runtime | `./scripts/g17-mfa-recovery-vue-otp-honesty.sh` | No Docker / Compose |

## Commands

```bash
./scripts/g17-mfa-recovery-vue-otp-honesty.sh
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

Do **not** treat `./scripts/g17-mfa-recovery-email-honesty.sh` or `./scripts/g17-vue-otp-writeback.sh` as this leftover. Those scripts are the #172 / #158 contracts.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g17-mfa-recovery-vue-otp-honesty/`. A sanitized copy is [`g17-mfa-recovery-vue-otp-honesty/agent-vm.env`](./g17-mfa-recovery-vue-otp-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | `2026-09-22T20:38:18Z`; honest SKIP accepted; fake delivery / write-back PASS / fake G17 close / redo rejected |
| #172 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #158 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| Live email OTP mailbox | **SKIP** | SMTP off; no mailbox observed |
| Live Keycloak recovery-authn-codes | **SKIP** | issuer unreachable / not enrolled |
| Live Vue → Keycloak otp write-back | **SKIP** | issuer unreachable and product endpoint absent |
| Product G17 / 等保 / 密评 / Vue→Keycloak otp | **NOT_CLAIMED** | leftovers stay open |
| Leftover rows kept | **true** | #172 + #158 + ALL sibling leftover rows including #195 / #191 / #198 / #200 |
| #96 / #118 / #133 / #154 / #158 / #172 | unchanged | not redone |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- A mailbox actually receiving an email OTP
- Keycloak `CONFIGURE_RECOVERY_AUTHN_CODES` required action
- Product Vue write-back endpoint that stores a Keycloak otp credential
- Writing otp / recovery-authn-codes from the Vue pages
- SMS MFA / WebAuthn
- Full G17 / 等保 / 密评 / 密码机
