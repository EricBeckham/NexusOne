# G5 leftover — PublishHook / real SchemaService DDL publish remainder honesty beyond #217

S0+S1 remainder after the #217 PublishHook / real SchemaService DDL abort honesty leftover ([g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md)), the #196 mid-publish honesty leftover ([g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md)), the #171 browser failed-publish leftover ([g5-browser-failed-publish.md](./g5-browser-failed-publish.md)), and the #145 mid-publish failure leftover ([g5-mid-publish-failure.md](./g5-mid-publish-failure.md)). Rebased onto `origin/main` `2fe75d5` (Merge #233 g17-mfa-recovery-vue-otp-remainder-honesty after #238). **G18 stays closed.** This leftover does **not** redo #94 evolve DDL abort. It does **not** redo #145 Zonky / HTTP / memory ITs. It does **not** redo #171 leftover. It does **not** redo #196 leftover. It does **not** redo #217 leftover. It does **not** change `FormService` / `SchemaService` abort semantics already on `main`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim product G5 close. P0/M0 are **not** redone. `g5_close=NOT_CLAIMED`.

#217 already inventories the in-memory abort-after-SchemaService harness as always-green **PASS** and is explicitly **not** real SchemaService DDL. A `ConfirmThenAbortEvolve` wrapper is **not** real DDL. Live CREATE `42P07` / evolve `23502` abort is honest **SKIP** unless observed. The leftover here is the **honesty gate beyond that abort inventory**: the #217 abort gate stays **PASS** and is explicitly **not** a live real-DDL **publish** (`honesty_217_abort_skip_is_not_live_publish=true`). Memory / stub harness stays **PASS** and is **not** real DDL publish (`memory_harness_is_not_real_ddl_publish=true`). Confirm-only (G18 no-drift leave publish) is **not** real DDL publish (`confirm_only_is_not_real_ddl_publish=true`). Zonky CREATE/evolve success ITs stay siblings and are **not** this leftover's live PASS (`zonky_create_evolve_success_is_not_live_publish=true`). Live product `SchemaServicePublishHook` → `JdbcSchemaService` CREATE/evolve **publish** is **PASS** only when live PG 15 was present **and** that publish walk was observed. Missing live PG 15 / unobserved walk is honest **SKIP**. It never fabricates `PASS`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g5-publishhook-ddl-honesty` (#217), `g5-mid-publish-failure-honesty` (#196), `g5-mid-publish-failure` (#145), `g5-browser-failed-publish` (#171), `g10-leftover-honesty-remainder` (#228), `g3-outbox-consume-idempotent-honesty` (#226), `g9-live-multi-jvm-replica-remainder-honesty` (#229), `g4-hot-purge-staging-leftover-honesty` (#230), `g7-withdraw-browser-evidence-honesty` (#212), `g2-h5-every-page-form-write-remainder-honesty` (#232), `g9-redis-down-invalidate-remainder-honesty` (#237), `g7-query-expense-travel-honesty-remainder` (#239), `g1-fieldpolicy-mask-second-auth-remainder-honesty` (#238), `g17-mfa-recovery-vue-otp-remainder-honesty` (#233), #231 S0+S1 claim-list tip-sync, `g2-report-physical-dump-honesty` (#221), `g6-recovery-port-dry-run-remainder-honesty` (#225), `s0-secret-rotate-getenv-remainder-honesty` (#223), `g4-pkce-spa-verifier-remainder-honesty` (#222), `g17-separation-leftover-honesty` (#216), `g15-wx-dt-native-bridge-remainder` (#220), `g1-queryast-export-honesty` (#215), `g17-gm-tls-nginx-handshake-honesty` (#210), `g9-sentinel-cluster-remainder-honesty` (#209), `g2-ops-pg-dump-honesty` (#188), `g17-mfa-recovery-vue-otp-honesty` (#205), `g2-h5-every-page-form-write-honesty` (#204), `g9-redis-down-invalidate-remainder` (#197), `g10-leftover-honesty` (#202), `g4-export-wizard-deny-honesty` (#203), `g3-outbox-consume-crash-honesty` (#195), `g1-queryast-boundsql-honesty` (#191), `g15-wx-dt-native-bridge-honesty` (#198), `s0-secret-rotate-without-restart-honesty` (#200), `linux-arm-profile-leftover-honesty` (#193), `g7-query-expense-travel-honesty` (#192), `g4-p1-staging-audit-events-honesty` (#190), `g6-recovery-port-dry-run-honesty` (#186), `s0s1-live-redis-minio-isolation-honesty` (#185), `g17-separation-honesty` (#187), `g4-hot-purge-90d-honesty` (#183), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g4-pkce-spa-verifier-honesty` (#177), `s0-secret-getenv-snapshot-honesty` (#180), `g7-withdraw-browser-honesty` (#175), `g9-redis-down-invalidate-honesty` (#174), and `g10-file-reconcile-honesty` (#169). Keep this file.

## What this leftover covers

| Path | Behavior |
|---|---|
| #217 abort honesty | Already on `main`. Memory harness **PASS** and **not** real DDL. Live abort **SKIP** unless observed. Do **not** redo. That abort SKIP is **not** this leftover's live publish PASS. |
| #196 / #171 / #145 | Already on `main`. Do **not** redo. |
| Stub / memory harness | Always-green `honesty_217_contract=PASS` **and** `memory_harness_is_not_real_ddl_publish=true`. |
| Confirm-only vs publish | G18 no-drift confirm is **not** real DDL publish. `confirm_only_is_not_real_ddl_publish=true`. `ConfirmThenAbortEvolve` is **not** real DDL publish. |
| Zonky CREATE/evolve success | Sibling ITs stay siblings. `zonky_create_evolve_success_is_not_live_publish=true`. |
| Live real-DDL publish | `live_real_ddl_publish=PASS` only with live PG 15 **and** observed product `SchemaServicePublishHook` → `JdbcSchemaService` CREATE/evolve. Missing listener / unobserved walk is honest **SKIP**. |
| Live PublishHook publish | `live_publishhook_publish=PASS` only when a product JAR publish through `SchemaServicePublishHook.beforeActivate` → `ensurePhysicalTable` was observed. Otherwise honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake live real-DDL publish PASS, rejects treating #217 abort SKIP as live publish, rejects treating confirm-only / memory harness / `ConfirmThenAbortEvolve` as real DDL publish, rejects `g5_close=CLOSED`, rejects #94/#145/#171/#196/#217 redo flags, and requires sibling leftover rows kept (including #217, #228, #226, #229, #230, #212, #232, #237, #239, #238, #233, #231 tip-sync, #221, and #225). |

No Docker. Live PG 15 / JAR / IdP / browser are **not** required. Do **not** run `./scripts/g5-form-publish-rollback.sh`, `./scripts/g5-browser-failed-publish.sh`, `./scripts/g5-mid-publish-failure-honesty.sh`, or `./scripts/g5-publishhook-ddl-honesty.sh` as this leftover.

## How to run

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

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g5-form-publish-rollback.sh
# ./scripts/g5-browser-failed-publish.sh
# ./scripts/g5-mid-publish-failure-honesty.sh
# ./scripts/g5-publishhook-ddl-honesty.sh
```

Java **21** / Boot **4.1.1**. No Docker. Live PG 15 / JAR / IdP / browser are **not** required.

See [engineering/g5-publishhook-ddl-remainder-honesty-EVIDENCE.md](./engineering/g5-publishhook-ddl-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g5-publishhook-ddl-remainder-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G5 close | Explicitly `g5_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| #217 abort SKIP as live publish | `honesty_217_abort_skip_is_not_live_publish=true`. |
| Memory / stub harness as real DDL publish | `memory_harness_is_not_real_ddl_publish=true`. |
| Confirm-only as real DDL publish | `confirm_only_is_not_real_ddl_publish=true`. G18 no-drift leave publish is confirm, not CREATE/evolve. |
| `ConfirmThenAbortEvolve` as real DDL publish | `confirm_then_abort_wrapper_is_not_real_ddl_publish=true`. |
| Zonky CREATE/evolve success as this leftover PASS | `zonky_create_evolve_success_is_not_live_publish=true`. Those suites stay siblings. |
| Live SchemaService CREATE/evolve publish | Missing live PG 15 / unobserved walk → honest SKIP. |
| Live product JAR PublishHook publish | Unobserved → honest SKIP. |
| Redo of #94 / #145 / #171 / #196 / #217 | `redone_94=false`, `redone_145=false`, `redone_171=false`, `redone_196=false`, `redone_217=false`. |
| P4 / app-package publish / form designer | Out of this leftover. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product G10 close (#228) / product G3 close (#226) / product G9 close (#229/#237) / product G4 close (#230) / product G7 close (#212/#239) / product G2 close (#232/#221) / product G6 close (#225) / product G1 close (#238) / product G17 close (#233) | Sibling leftovers. Not rewritten. Not claimed. |

## Honest leftovers (not claimed)

- Live PG 15 + product `JdbcSchemaService` CREATE / evolve **publish** observed through `SchemaServicePublishHook`
- Live product JAR publish that actually hits PublishHook → real SchemaService DDL (not confirm-only, not a memory stub, not `ConfirmThenAbortEvolve`)
- A live JAR + IdP + browser click of `data-testid=form-publish` that hits PublishHook → real DDL
- Product G5 close
- P4 / app-package publish
- Form designer product
- Redo of #94 / #145 / #171 / #196 / #217
