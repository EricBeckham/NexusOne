# G10 leftover — file-reconcile / object-reconcile PASS/SKIP honesty remainder beyond #228/#202/#169/#179/#141 — evidence

Recorded on this branch after rebase onto `origin/main` `d4252cc` (Merge #241 SecretPort rotate/getenv leftover honesty after #235 G5 PublishHook/DDL leftover honesty). **Not** a product G10 close. **Not** a redo of #141 / #169 / #179 / #202 / #228. **Not** identity apply. **Not** SAML / LDAP / SMS MFA. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone.

See [docs/g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md), including [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), and every leftover-style sibling already on `origin/main`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| MinIO | not listening on `127.0.0.1:19000` / `:9000` | `S3_*` unset |
| Keycloak | not listening on `127.0.0.1:8080` | OIDC discovery not reachable |
| Runtime | leftover honesty remainder inventory only | No Docker / Compose. `#141` / `#169` / `#179` / `#202` / `#228` scripts not re-run |

## Commands

```bash
./scripts/g10-file-object-reconcile-remainder-honesty.sh
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-live-minio-pass.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-live-keycloak-pass.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-live-apply-pass.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-g10-close.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-redone.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-harness-as-live.env fail
```

Do **not** treat `./scripts/g10-leftover-honesty-remainder.sh` or `./scripts/g10-leftover-honesty.sh` as this leftover. Those scripts are the #228 / #202 contracts. Parent `--verify-report` on honest fixtures is harness evidence. `#228` harness PASS is **not** a live MinIO / object-store walk.

## Results (agent VM)

Filled after the leftover honesty remainder script on this VM at `2026-09-22T22:31:48Z`. Live output is gitignored under `.local/g10-file-object-reconcile-remainder-honesty/`. A sanitized copy is [`g10-file-object-reconcile-remainder-honesty/agent-vm.env`](./g10-file-object-reconcile-remainder-honesty/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live MinIO / object / Keycloak / apply PASS, fake `g10_close=CLOSED`, fake #228 redo, and fake #228-harness-as-live rejected (`2026-09-22T22:31:48Z`) |
| leftover #169 cells still honest | **PASS** | counting preview/apply + tenant-prefix stay PASS; committed live MinIO SKIP |
| leftover #179 cells still honest | **PASS** | dry-run default no-write; committed live Keycloak / apply SKIP |
| leftover #202 cells still honest | **PASS** | parent leftover honesty fixtures stay PASS; not a live MinIO walk |
| leftover #228 cells still honest | **PASS** | parent remainder honesty fixtures stay PASS; not a live MinIO / object walk |
| Counting preview vs apply | **PASS** | from committed #169 artifact; this leftover does not re-run it |
| Tenant-prefix guard | **PASS** | from committed #169 artifact |
| Live MinIO / object store | **SKIP** (expected) | `S3_ENDPOINT` unset; nothing on `:19000` / `:9000` |
| Live object reconcile | **SKIP** (expected) | same walk; #228 harness PASS is not that walk |
| Live Keycloak 8080 dry-run | **SKIP** (expected) | nothing on `:8080`; OIDC discovery not reachable |
| live_preview_observed / live_dry_run_observed | **false** | honesty gate requires `false` on SKIP |
| Live apply | **SKIP** | product apply not redone |
| Sibling leftover rows | kept | #141 / #169 / #179 / #202 / #228 + this leftover + #232 / #237 / #239 / #238 / #233 / #235 / #241 / #212 / #230 / #229 / #226 siblings |
| #141/#169/#179/#202/#228 redo | **false** | sibling leftovers |
| SAML / LDAP / SMS MFA | **false** | honesty gate |
| Product G10 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live MinIO / object-store preview + apply + tenant-prefix walk
- Treating #228 / #202 harness PASS as a live MinIO / object-store walk
- Live Keycloak 26.5.2 `:8080` detect + proposed-fix dry-run
- Product identity apply (`POST {"apply":true}`)
- Product G10 close
- SAML / LDAP / AD / SMS MFA / Keycloak Organizations
- Redo of #141 / #169 / #179 / #202 / #228
