# G5 leftover: PublishHook / real SchemaService DDL publish remainder honesty evidence

Recorded on this branch after rebase onto `origin/main` `2fe75d5` (Merge #233 g17-mfa-recovery-vue-otp-remainder-honesty after #238). **Not** a product G5 close. **Not** a redo of #94 evolve DDL abort. **Not** a redo of #145 mid-publish failure rollback. **Not** a redo of #171 browser leftover. **Not** a redo of #196 mid-publish honesty leftover. **Not** a redo of #217 PublishHook / real-DDL abort honesty leftover. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. SchemaService abort semantics already on `main` were not rewritten.

See [docs/g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md). Keep **all sibling leftover rows** in [docs/README.md](../README.md), including [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g5-mid-publish-failure.md](../g5-mid-publish-failure.md) (#145), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), #231 S0+S1 claim-list tip-sync, [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), and [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x / 3.9.x** | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Live PG 15 / JAR / IdP / browser | not required | honest SKIP when unavailable |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
./scripts/g5-publishhook-ddl-remainder-honesty.sh
./scripts/g5-publishhook-ddl-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g5-publishhook-ddl-remainder-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g5-publishhook-ddl-remainder-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-live-ddl-publish-pass.env fail
./scripts/g5-publishhook-ddl-remainder-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-publishhook-publish-pass.env fail
./scripts/g5-publishhook-ddl-remainder-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-217-abort-as-publish.env fail
./scripts/g5-publishhook-ddl-remainder-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-confirm-only-as-publish.env fail
./scripts/g5-publishhook-ddl-remainder-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-g5-close.env fail
./scripts/g5-publishhook-ddl-remainder-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-remainder-honesty/fixtures/fake-redone.env fail
```

Do **not** treat `./scripts/g5-form-publish-rollback.sh`, `./scripts/g5-browser-failed-publish.sh`, `./scripts/g5-mid-publish-failure-honesty.sh`, or `./scripts/g5-publishhook-ddl-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g5-publishhook-ddl-remainder-honesty/`. A sanitized copy is [`g5-publishhook-ddl-remainder-honesty/agent-vm.env`](./g5-publishhook-ddl-remainder-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live publish PASS rejected / fake PublishHook publish PASS rejected / fake #217-abort-as-publish rejected / fake confirm-only-as-publish rejected / fake `g5_close=CLOSED` rejected / fake redo rejected | recorded `2026-09-22T22:29:13Z` on `2fe75d5` |
| #217 abort honesty | **PASS** (inventory) | memory harness **not** real DDL; abort SKIP is **not** live publish |
| Memory harness is real DDL publish | **false** | honesty gate |
| Confirm-only is real DDL publish | **false** | G18 no-drift confirm ≠ CREATE/evolve |
| ConfirmThenAbortEvolve is real DDL publish | **false** | wrapper is not SchemaService DDL |
| Zonky CREATE/evolve success as live publish | **false** | sibling leftovers |
| Live real-DDL publish | **SKIP** (expected) | live PG 15 / observed CREATE/evolve publish not present |
| Live PublishHook publish | **SKIP** (expected) | product `SchemaServicePublishHook` → `ensurePhysicalTable` publish not observed |
| Product G5 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #94 / #145 / #171 / #196 / #217 | not redone | `redone_94=false`, `redone_145=false`, `redone_171=false`, `redone_196=false`, `redone_217=false` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |
| Sibling leftover rows | kept | includes #217 + #228 `g10-leftover-honesty-remainder` + #226 `g3-outbox-consume-idempotent-honesty` + #229 `g9-live-multi-jvm-replica-remainder-honesty` + #230 `g4-hot-purge-staging-leftover-honesty` + #212 `g7-withdraw-browser-evidence-honesty` + #232 `g2-h5-every-page-form-write-remainder-honesty` + #237 `g9-redis-down-invalidate-remainder-honesty` + #239 `g7-query-expense-travel-honesty-remainder` + #238 `g1-fieldpolicy-mask-second-auth-remainder-honesty` + #233 `g17-mfa-recovery-vue-otp-remainder-honesty` + #231 tip-sync + #221 `g2-report-physical-dump-honesty` + #225 `g6-recovery-port-dry-run-remainder-honesty` |

## Honest leftovers

- Live PG 15 + product `JdbcSchemaService` CREATE / evolve publish through `SchemaServicePublishHook`
- Live product JAR PublishHook → real SchemaService DDL publish
- Live JAR + IdP + browser fail-click of `data-testid=form-publish`
- Product G5 close
- P4 / app-package publish / form designer
- Redo of #94 / #145 / #171 / #196 / #217
