# G5 leftover — PublishHook / real SchemaService DDL leftover honesty remainder beyond #235/#217

S0+S1 remainder **after** the #235 PublishHook / real SchemaService DDL **publish** remainder leftover ([g5-publishhook-ddl-remainder-honesty.md](./g5-publishhook-ddl-remainder-honesty.md)) and the #217 PublishHook / real SchemaService DDL abort leftover ([g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md)). Rebased onto `origin/main` `2ac6326` (Merge #263 Linux/ARM leftover honesty remainder after #262 G10 leftover honesty remainder / #259 G9 leftover honesty remainder / #249 G17 SoD/admin-force leftover honesty / #240 G15 H5/PKCE leftover honesty / #254 G2 H5/form.write/report leftover honesty / #236 Linux/ARM profile leftover honesty remainder / #251 G4 hot-purge/staging/PKCE leftover honesty / #243 G10 file/object reconcile leftover honesty / #241 SecretPort rotate/getenv leftover honesty / #235 G5 PublishHook/DDL leftover honesty remainder / #217 G5 PublishHook/DDL abort leftover honesty). **G18 stays closed.** This leftover does **not** redo #235. It does **not** redo #217. It does **not** redo #196 / #171 / #145 / #94 product code. It does **not** change `FormService` / `SchemaService` abort semantics already on `main`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim product G5 close. P0/M0 are **not** redone. `g5_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g5-publishhook-ddl-remainder-honesty](./g5-publishhook-ddl-remainder-honesty.md) (#235), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g5-mid-publish-failure](./g5-mid-publish-failure.md) (#145), [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171), this file, [linux-arm-profile-remainder-honesty](./linux-arm-profile-remainder-honesty.md) (#236), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [linux-arm-profile-honesty](./linux-arm-profile-honesty.md) (#157), [g15-h5-pkce-miniprogram-remainder-honesty](./g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g9-leftover-honesty-remainder](./g9-leftover-honesty-remainder.md) (#259), [g10-file-object-reconcile-leftover-honesty](./g10-file-object-reconcile-leftover-honesty.md) (#262), [linux-arm-profile-leftover-remainder-honesty](./linux-arm-profile-leftover-remainder-honesty.md) (#263), [g17-separation-leftover-honesty-remainder](./g17-separation-leftover-honesty-remainder.md) (#249), [g2-h5-every-page-form-write-report-remainder-honesty](./g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [g4-hot-purge-staging-pkce-remainder-honesty](./g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty](./g10-file-object-reconcile-remainder-honesty.md) (#243), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g17-mfa-recovery-vue-otp-remainder-honesty](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), and every other leftover row already on `main`. Keep this file.

#235 already inventories #217 abort honesty as always-green **PASS** and is explicitly **not** a live real-DDL **publish**. Memory / confirm-only / `ConfirmThenAbortEvolve` / Zonky CREATE-evolve success stay **not** this leftover's live PASS. Live product `SchemaServicePublishHook` → `JdbcSchemaService` CREATE/evolve **publish** is **PASS** only when live PG 15 was present **and** that publish walk was observed. Missing live PG 15 / unobserved walk is honest **SKIP**. It never fabricates `PASS`. `g5_close=NOT_CLAIMED`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #235 leftover cells | Publish remainder honesty **PASS**. Live real-DDL publish **SKIP**. Live PublishHook publish **SKIP**. `g5_close=NOT_CLAIMED`. `leftover_235_cells_honest=PASS`. `leftover_235_honesty_gate=PASS`. Do not redo. That remainder SKIP is **not** this leftover's live publish PASS. |
| #217 leftover cells | Abort honesty **PASS**. Memory harness **not** real DDL. Live abort **SKIP**. `leftover_217_cells_honest=PASS`. `leftover_217_honesty_gate=PASS`. Do not redo. That abort SKIP is **not** this leftover's live publish PASS. |
| #196 leftover cells | Mid-publish honesty stays honest. `leftover_196_cells_honest=PASS`. Do not redo. |
| Stub / memory harness | Always-green `honesty_217_contract=PASS` **and** `honesty_235_contract=PASS` **and** `memory_harness_is_not_real_ddl_publish=true`. |
| Confirm-only vs publish | G18 no-drift confirm is **not** real DDL publish. `confirm_only_is_not_real_ddl_publish=true`. `ConfirmThenAbortEvolve` is **not** real DDL publish. |
| Zonky CREATE/evolve success | Sibling ITs stay siblings. `zonky_create_evolve_success_is_not_live_publish=true`. |
| Live real-DDL publish | `live_real_ddl_publish=PASS` only with live PG 15 **and** observed product `SchemaServicePublishHook` → `JdbcSchemaService` CREATE/evolve. Missing listener / unobserved walk is honest **SKIP**. |
| Live PublishHook publish | `live_publishhook_publish=PASS` only when a product JAR publish through `SchemaServicePublishHook.beforeActivate` → `ensurePhysicalTable` was observed. Otherwise honest **SKIP**. |
| Sibling leftover rows | `docs/README.md` keeps #235 / #217 and this leftover, plus #240 `g15-h5-pkce-miniprogram-remainder-honesty`, #259 `g9-leftover-honesty-remainder`, #262 `g10-file-object-reconcile-leftover-honesty`, #263 `linux-arm-profile-leftover-remainder-honesty`, #249 `g17-separation-leftover-honesty-remainder`, #254 `g2-h5-every-page-form-write-report-remainder-honesty`, #236 `linux-arm-profile-remainder-honesty`, #251 `g4-hot-purge-staging-pkce-remainder-honesty`, #243 `g10-file-object-reconcile-remainder-honesty`, #241 `s0-secret-rotate-getenv-leftover-honesty`, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects fake live real-DDL publish PASS, rejects treating #235 remainder SKIP or #217 abort SKIP as live publish, rejects treating confirm-only / memory harness / `ConfirmThenAbortEvolve` as real DDL publish, rejects `g5_close=CLOSED`, rejects #235/#217/#196/#171/#145/#94 redo flags, and requires leftover rows kept (including #263 / #262 / #259 / #249 / #240 / #254 / #236 / #251 / #243 / #241 / #235 / #217). |

No Docker. Live PG 15 / JAR / IdP / browser are **not** required. Do **not** run `./scripts/g5-form-publish-rollback.sh`, `./scripts/g5-browser-failed-publish.sh`, `./scripts/g5-mid-publish-failure-honesty.sh`, `./scripts/g5-publishhook-ddl-honesty.sh`, or `./scripts/g5-publishhook-ddl-remainder-honesty.sh` as this leftover.

## How to run

```bash
./scripts/g5-publishhook-ddl-leftover-honesty.sh
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-live-ddl-publish-pass.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-publishhook-publish-pass.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-235-as-live-publish.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-217-abort-as-publish.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-g5-close.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-redone.env
```

#235 / #217 stay the sibling reproduction commands. This leftover does **not** invoke them:

```bash
# do not run for this leftover
# ./scripts/g5-publishhook-ddl-remainder-honesty.sh
# ./scripts/g5-publishhook-ddl-honesty.sh
```

Parent leftover honesty fixtures stay as harness evidence. Java 21 / Boot **4.1.1**. No Docker. Live PG 15 / JAR / IdP / browser are **not** required.

See [engineering/g5-publishhook-ddl-leftover-honesty-EVIDENCE.md](./engineering/g5-publishhook-ddl-leftover-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g5-publishhook-ddl-leftover-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G5 close | Explicitly `g5_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| #235 remainder SKIP as live publish | `honesty_235_remainder_skip_is_not_live_publish=true`. |
| #217 abort SKIP as live publish | `honesty_217_abort_skip_is_not_live_publish=true`. |
| Memory / stub harness as real DDL publish | `memory_harness_is_not_real_ddl_publish=true`. |
| Confirm-only as real DDL publish | `confirm_only_is_not_real_ddl_publish=true`. G18 no-drift leave publish is confirm, not CREATE/evolve. |
| `ConfirmThenAbortEvolve` as real DDL publish | `confirm_then_abort_wrapper_is_not_real_ddl_publish=true`. |
| Zonky CREATE/evolve success as this leftover PASS | `zonky_create_evolve_success_is_not_live_publish=true`. Those suites stay siblings. |
| Live SchemaService CREATE/evolve publish | Missing live PG 15 / unobserved walk → honest SKIP. |
| Live product JAR PublishHook publish | Unobserved → honest SKIP. |
| Redo of #94 / #145 / #171 / #196 / #217 / #235 | `redo_94=false`, `redo_145=false`, `redo_171=false`, `redo_196=false`, `redo_217=false`, `redo_235=false`. |
| P4 / app-package publish / form designer | Out of this leftover. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product Profile close (#263) / product G10 close (#262) / product G9 close (#259) / product SoD close (#249) / product Profile remainder close (#236) / product G2 close (#254) / product G4 close (#251) / product G10 remainder close (#243) / product S0 close (#241) | Sibling leftovers. Not rewritten. Not claimed. |

## Honest leftovers (not claimed)

- Live PG 15 + product `JdbcSchemaService` CREATE / evolve **publish** observed through `SchemaServicePublishHook`
- Live product JAR publish that actually hits PublishHook → real SchemaService DDL (not confirm-only, not a memory stub, not `ConfirmThenAbortEvolve`, not #235 remainder collect)
- A live JAR + IdP + browser click of `data-testid=form-publish` that hits PublishHook → real DDL
- Product G5 close
- P4 / app-package publish
- Form designer product
- Redo of #94 / #145 / #171 / #196 / #217 / #235
