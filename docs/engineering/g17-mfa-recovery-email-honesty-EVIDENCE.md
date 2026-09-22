# G17 leftover: admin MFA recovery-code / email delivery honesty evidence

See [docs/g17-mfa-recovery-email-honesty.md](../g17-mfa-recovery-email-honesty.md). Keep **both** leftover rows: [g17-admin-mfa-recovery-email.md](../g17-admin-mfa-recovery-email.md) (#154) and this leftover. Keep [g15-wx-dt-prod-gap-honesty.md](../g15-wx-dt-prod-gap-honesty.md) (#167), [g4-export-wizard-deny.md](../g4-export-wizard-deny.md) (#166), and [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169).

Recorded on this agent VM after rebase onto `origin/main` `3cdccca` (Merge #165). **Not** a full G17 close. **Not** 等保 / 密评. **Not** a G18 reopen. `AuditPort` same-TX rules were not changed. Vue does **not** write otp / recovery-authn-codes into Keycloak. `#96` / `#118` / `#133` / `#154` / `#158` / `#160` were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x** when present | filled after the test run |
| Spring Boot parent | **4.1.1** | product BOM |
| Node | **22** | Vue leftover |
| Keycloak SMTP / recovery-authn-codes | not delivered | honest SKIP |
| Runtime | `./scripts/g17-mfa-recovery-email-honesty.sh` | No Docker / Compose |

## Commands

```bash
./scripts/g17-mfa-recovery-email-honesty.sh
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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g17-mfa-recovery-email-honesty/`. A sanitized copy is [`g17-mfa-recovery-email-honesty/agent-vm.env`](./g17-mfa-recovery-email-honesty/agent-vm.env) once recorded. Walk JSON: [`g17-mfa-recovery-email-honesty/walk.json`](../g17-mfa-recovery-email-honesty/walk.json).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | pending | fake delivery PASS / G17-close rejected |
| #154 local recovery codes | pending | 8 hashed codes; consume once |
| #154 email request without SMTP | pending | `P2.IAM.SMTP_DISABLED` |
| Live email OTP mailbox | **SKIP** | SMTP off; no mailbox observed |
| Live Keycloak recovery-authn-codes | **SKIP** | issuer unreachable / not enrolled |
| Product G17 / 等保 / 密评 / Vue→Keycloak otp | **NOT_CLAIMED** | leftovers stay open |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- A mailbox actually receiving an email OTP
- Keycloak `CONFIGURE_RECOVERY_AUTHN_CODES` required action
- Writing otp / recovery-authn-codes from the Vue pages
- SMS MFA / WebAuthn
- Full G17 / 等保 / 密评 / 密码机
