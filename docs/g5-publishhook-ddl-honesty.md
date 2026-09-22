# G5 leftover — PublishHook / real SchemaService DDL honesty beyond #196/#171/#145

S0+S1 remainder after the #196 mid-publish / browser failed-publish honesty leftover ([g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md)), the #171 browser failed-publish leftover ([g5-browser-failed-publish.md](./g5-browser-failed-publish.md)), and the #145 mid-publish failure leftover ([g5-mid-publish-failure.md](./g5-mid-publish-failure.md)). Rebased onto `origin/main` `1c79ef3` (Merge #209 G9 Sentinel/Cluster remainder honesty after #214). **G18 stays closed.** This leftover does **not** redo #94 evolve DDL abort. It does **not** redo #145 Zonky / HTTP / memory ITs. It does **not** redo #171 leftover. It does **not** redo #196 leftover. It does **not** change `FormService` / `SchemaService` / `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim product G5 close. P0/M0 are **not** redone. `g5_close=NOT_CLAIMED`.

#196 already inventories the in-memory abort-after-SchemaService harness as always-green **PASS** and a live browser/IdP/JAR fail-click as honest **SKIP**. That memory harness is **not** a real SchemaService DDL abort. #145 already proved CREATE abort (`42P07`) / SchemaService-then-abort keeps Running pointer + snapshot and writes `ROLLED_BACK`. #94 already proved product evolve abort (`23502`). The leftover here is the **honesty gate** beyond those: the #196 memory harness stays **PASS** and is explicitly **not** real DDL (`memory_harness_is_real_ddl=false`). A `ConfirmThenAbortEvolve` wrapper is **not** real DDL. Zonky #94/#145 stays on those leftovers and is **not** this leftover's PASS. Live `SchemaServicePublishHook` → real `JdbcSchemaService` CREATE/evolve abort is **PASS** only when live PG 15 was present **and** the abort was observed. Missing live PG 15 / unobserved walk is honest **SKIP**. It never fabricates `PASS`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g5-mid-publish-failure` (#145), `g5-browser-failed-publish` (#171), `g5-mid-publish-failure-honesty` (#196), `g9-redis-down-invalidate-remainder` (#197), `g2-h5-every-page-form-write-honesty` (#204), `g17-mfa-recovery-vue-otp-honesty` (#205), `g2-ops-pg-dump-honesty` (#188), `g9-sentinel-cluster-remainder-honesty` (#209), `g2-h5-every-page` (#163), `g2-form-write-honesty` (#173), `g4-export-wizard-deny-honesty` (#203), `g10-leftover-honesty` (#202), `g3-outbox-consume-crash-honesty` (#195), `g1-queryast-boundsql-honesty` (#191), `g15-wx-dt-native-bridge-honesty` (#198), `s0-secret-rotate-without-restart-honesty` (#200), `linux-arm-profile-leftover-honesty` (#193), `g7-query-expense-travel-honesty` (#192), `g4-p1-staging-audit-events-honesty` (#190), `g6-recovery-port-dry-run-honesty` (#186), `s0s1-live-redis-minio-isolation-honesty` (#185), `g17-separation-honesty` (#187), `g4-hot-purge-90d-honesty` (#183), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g4-pkce-spa-verifier-honesty` (#177), `s0-secret-getenv-snapshot-honesty` (#180), `g7-withdraw-browser-honesty` (#175), `g9-redis-down-invalidate-honesty` (#174), and `g10-file-reconcile-honesty` (#169). Keep this file.

## What this leftover covers

| Path | Behavior |
|---|---|
| #196 mid-publish honesty | Already on `main`. In-memory abort-after-SchemaService harness stays **PASS**. Do **not** redo. That harness is **not** real SchemaService DDL. |
| #171 browser leftover | Already on `main`. Do **not** redo. |
| #145 / #94 mid-fail / evolve abort | Already on `main`. Zonky CREATE `42P07` / evolve `23502` stay on those leftovers. Do **not** redo Zonky ITs. `zonky_94_145_is_this_leftover_pass=false`. |
| Memory harness vs real DDL | Always-green `mid_publish_memory_harness=PASS` **and** `memory_harness_is_real_ddl=false`. `ConfirmThenAbortEvolve` is **not** real DDL. |
| Real SchemaService DDL abort | `real_schema_service_ddl_abort=PASS` only with live PG 15 **and** observed `JdbcSchemaService` CREATE `42P07` / evolve `23502`. Missing listener / unobserved walk is honest **SKIP**. |
| PublishHook → real DDL | `publishhook_real_ddl=PASS` only when product `SchemaServicePublishHook.beforeActivate` → `SchemaService.ensurePhysicalTable` (CREATE / evolve / confirm; DDL only by SchemaService) was observed. Otherwise honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake real-DDL PASS, rejects treating the memory harness as real DDL, rejects `g5_close=CLOSED`, rejects #94/#145/#171/#196 redo flags, and requires sibling leftover rows kept (including #197, #204, #205, #188, and #209). |

No Docker. Live PG 15 / JAR / IdP / browser are **not** required. Do **not** run `./scripts/g5-form-publish-rollback.sh`, `./scripts/g5-browser-failed-publish.sh`, or `./scripts/g5-mid-publish-failure-honesty.sh` as this leftover.

## How to run

```bash
./scripts/g5-publishhook-ddl-honesty.sh
./scripts/g5-publishhook-ddl-honesty.sh --verify-report <out>/report.env
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-real-ddl-pass.env fail
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-publishhook-ddl-pass.env fail
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-memory-as-real-ddl.env fail
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-g5-close.env fail
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-redone.env fail
```

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g5-form-publish-rollback.sh
# ./scripts/g5-browser-failed-publish.sh
# ./scripts/g5-mid-publish-failure-honesty.sh
```

Java **21** / Boot **4.1.1**. No Docker. Live PG 15 / JAR / IdP / browser are **not** required.

See [engineering/g5-publishhook-ddl-honesty-EVIDENCE.md](./engineering/g5-publishhook-ddl-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g5-publishhook-ddl-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G5 close | Explicitly `g5_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Memory harness as real SchemaService DDL | `memory_harness_is_real_ddl=false`. #196 PASS is in-memory abort-after-ensure. |
| `ConfirmThenAbortEvolve` as real DDL | `confirm_then_abort_wrapper_is_real_ddl=false`. |
| Zonky #94/#145 as this leftover PASS | `zonky_94_145_is_this_leftover_pass=false`. Those suites stay siblings. |
| Live SchemaService CREATE/evolve abort | Missing live PG 15 / unobserved walk → honest SKIP. |
| Live PublishHook → real DDL | Unobserved → honest SKIP. |
| Redo of #94 / #145 / #171 / #196 | `redone_94=false`, `redone_145=false`, `redone_171=false`, `redone_196=false`. |
| P4 / app-package publish / form designer | Out of this leftover. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product G2 close (#204 / #188) / product G9 close (#197 / #209) / product G17 close (#205) | Sibling leftovers. Not rewritten. Not claimed. |

## Honest leftovers (not claimed)

- Live PG 15 + product `JdbcSchemaService` CREATE `42P07` / evolve `23502` abort observed on this leftover
- Live `SchemaServicePublishHook` → real SchemaService DDL abort (not a memory stub, not `ConfirmThenAbortEvolve`)
- A live JAR + IdP + browser click of `data-testid=form-publish` that hits PublishHook → real DDL abort
- Product G5 close
- P4 / app-package publish
- Form designer product
- Redo of #94 / #145 / #171 / #196
