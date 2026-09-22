# G2 leftover — H5 every-page / form.write / report physical-dump remainder honesty beyond #232/#221/#204

S0+S1 remainder after the #232 H5 every-page / form.write remainder honesty leftover ([g2-h5-every-page-form-write-remainder-honesty.md](./g2-h5-every-page-form-write-remainder-honesty.md)), the #221 report query/export + physical dump honesty leftover ([g2-report-physical-dump-honesty.md](./g2-report-physical-dump-honesty.md)), and the #204 H5 every-page / form.write honesty leftover ([g2-h5-every-page-form-write-honesty.md](./g2-h5-every-page-form-write-honesty.md)). Rebased onto `origin/main` `971b648` (Merge #236 Linux/ARM profile leftover honesty after #251). **This does not close product G2.** **G18 stays closed.** This leftover does **not** redo #112 / #116 / #122 / #124 / #137 / #144 / #150 / #155 / #163 / #173 / #204 / #221 / #232 product code. It does **not** redo #204. It does **not** redo #221. It does **not** redo #232. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0 / M0. `g2_close=NOT_CLAIMED`.

#232 already inventories the remainder honesty gate beyond #204 as always-green **PASS** and is explicitly **not** a live JAR / live H5 walk. #221 already inventories report/dump SurfaceGuard harness as always-green **PASS** and is explicitly **not** a live report / dump walk. #204 already inventories the combined H5 every-page / form.write honesty gate as always-green **PASS**. Those harness PASS cells are **not** a live JAR walk, **not** a live H5 every-page walk, **not** a live report/dump walk, **not** putting SurfaceGuard on OPEN H5 pages, and **not** a product G2 close. The leftover here is the **honesty gate beyond those three**: the #232 remainder contract stays **PASS** and is explicitly **not** a live JAR / live H5 / live report / live dump walk (`honesty_232_is_not_live_jar=true`). The #221 report/dump harness stays **PASS** and is explicitly **not** a live report / dump / H5 / form.write walk (`honesty_221_is_not_live_report=true`). The #204 harness stays **PASS** and is explicitly **not** a live JAR / live H5 walk (`harness_204_is_not_live_jar=true`). Combining #232+#221+#204 harness PASS is **not** product G2 (`combined_232_221_204_is_not_product_g2=true`). Live product-JAR + issued-context HTTP is **PASS** only when a healthy JAR, issued context, and observed submissions / submit-start / H5 / report / dump walks exist. Missing JAR/context is an honest **SKIP**. It never fabricates `PASS`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g2-h5-every-page-form-write-remainder-honesty` (#232), `g2-report-physical-dump-honesty` (#221), `g2-h5-every-page-form-write-honesty` (#204), `g2-h5-every-page` (#163), `g2-form-write-honesty` (#173), `linux-arm-profile-remainder-honesty` (#236), `linux-arm-profile-leftover-honesty` (#193), `g4-hot-purge-staging-pkce-remainder-honesty` (#251), `g10-file-object-reconcile-remainder-honesty` (#243), `g10-leftover-honesty-remainder` (#228), `s0-secret-rotate-getenv-leftover-honesty` (#241), `s0-secret-rotate-getenv-remainder-honesty` (#223), `g5-publishhook-ddl-remainder-honesty` (#235), `g5-publishhook-ddl-honesty` (#217), `g17-mfa-recovery-vue-otp-remainder-honesty` (#233), `g1-fieldpolicy-mask-second-auth-remainder-honesty` (#238), `g7-query-expense-travel-honesty-remainder` (#239), `g9-redis-down-invalidate-remainder-honesty` (#237), `g3-outbox-consume-idempotent-honesty` (#226), `g9-live-multi-jvm-replica-remainder-honesty` (#229), `g4-hot-purge-staging-leftover-honesty` (#230), `g7-withdraw-browser-evidence-honesty` (#212), `g6-recovery-port-dry-run-remainder-honesty` (#225), `g4-pkce-spa-verifier-remainder-honesty` (#222), `g17-separation-leftover-honesty` (#216), `g15-wx-dt-native-bridge-remainder` (#220), `g1-queryast-export-honesty` (#215), `g17-gm-tls-nginx-handshake-honesty` (#210), `g2-ops-pg-dump-honesty` (#188), `g17-mfa-recovery-vue-otp-honesty` (#205), `g9-redis-down-invalidate-remainder` (#197), `g9-sentinel-cluster-remainder-honesty` (#209), `g4-export-wizard-deny-honesty` (#203), `g10-leftover-honesty` (#202), `g5-mid-publish-failure-honesty` (#196), `g3-outbox-consume-crash-honesty` (#195), `g1-queryast-boundsql-honesty` (#191), `g15-wx-dt-native-bridge-honesty` (#198), `s0-secret-rotate-without-restart-honesty` (#200), `linux-arm-profile-leftover-honesty` (#193), `g7-query-expense-travel-honesty` (#192), `g4-p1-staging-audit-events-honesty` (#190), `s0s1-live-redis-minio-isolation-honesty` (#185), `g17-separation-honesty` (#187), `g4-hot-purge-90d-honesty` (#183), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g4-pkce-spa-verifier-honesty` (#177), `s0-secret-getenv-snapshot-honesty` (#180), `g7-withdraw-browser-honesty` (#175), `g9-redis-down-invalidate-honesty` (#174), `g10-file-reconcile-honesty` (#169), `g5-mid-publish-failure` (#145), `g5-browser-failed-publish` (#171), `g2-form-submission-write`, `g2-submit-start-form-write`, `g2-h5-permission`, `g2-ops-pg-dump`, `g2-todo-http-surface`, `g2-tenant-only-catalog`, `g2-physical-dump-surface`, and `g2-report-surface-guard`. Keep this file.

Baseline: Java 21, Spring Boot **4.1.1**. No Docker.

## What this leftover covers

| Path | Behavior |
|---|---|
| #232 H5 / form.write remainder honesty | Already on `main`. Remainder harness stays **PASS**. Do **not** redo. That PASS is **not** a live JAR / live H5 / live report / live dump walk / product G2 close. |
| #221 report / physical dump honesty | Already on `main`. Report/dump harness stays **PASS**. Do **not** redo. That PASS is **not** a live report / dump / H5 / form.write walk. |
| #204 H5 every-page / form.write honesty | Already on `main`. Combined harness stays **PASS**. Do **not** redo. That PASS is **not** a live JAR / live H5 walk / H5-open-covered / product G2 close. |
| #232 remainder vs live | `honesty_232_is_not_live_jar=true`, `honesty_232_is_not_live_h5=true`, `honesty_232_is_not_live_report=true`, `honesty_232_is_not_live_dump=true`. |
| #221 harness vs live | `honesty_221_is_not_live_report=true`, `honesty_221_is_not_live_dump=true`, `honesty_221_is_not_live_h5=true`, `honesty_221_is_not_live_form_write=true`. |
| #204 harness vs live | `harness_204_is_not_live_jar=true`, `harness_204_is_not_live_h5=true`, `harness_204_is_not_h5_open_covered=true`. |
| Combined harness vs product G2 | `combined_232_221_204_is_not_product_g2=true`. |
| Report vs dump | `report_is_physical_dump=false`. Dump is not report Task submit (`dump_is_report_task_submit=false`). |
| Honest live JAR SKIP | `live_jar_walk` is **PASS** only when a live JAR was present **and** submissions + submit-start were observed. Missing `NEXUS_BASE_URL` / issued context is honest **SKIP**. |
| Honest live H5 SKIP | `live_h5_walk` is **PASS** only when a live JAR was present **and** an `/m/*` walk was observed. Missing JAR is honest **SKIP**. |
| Honest live report / dump SKIP | `live_report_walk` / `live_dump_walk` are **PASS** only when a live JAR was present **and** those walks were observed. Missing JAR is honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects treating #232/#221/#204 harness as a live walk, rejects `g2_close=CLOSED`, rejects #112/#116/#122/#124/#137/#144/#150/#155/#163/#173/#204/#221/#232 redo flags, and requires sibling leftover rows kept (including #232, #221, #204, #236 Linux/ARM remainder, #251 G4 hot-purge/staging/PKCE remainder, #243 G10 file/object remainder, #241 SecretPort leftover, #235 G5 PublishHook remainder, and every sibling). |

No Docker. No product-JAR Playwright driver. Parent leftovers stay siblings.

## How to run

```bash
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-g2-close.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-live-jar-pass.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-live-h5-pass.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-live-report-pass.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-live-dump-pass.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-232-as-live.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-221-as-live.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-redone.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-report-is-dump.env fail
```

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g2-h5-every-page.sh
# ./scripts/g2-form-write-honesty.sh
# ./scripts/g2-h5-every-page-form-write-honesty.sh
# ./scripts/g2-h5-every-page-form-write-remainder-honesty.sh
# ./scripts/g2-report-physical-dump-honesty.sh
```

`PASS` for live JAR requires all of: reachable product JAR, issued context, observed `POST /api/v1/p3/forms/{id}/submissions`, and observed submit-start dual-check. Live H5 every-page `PASS` additionally requires an observed `/m/*` walk. Live report `PASS` requires observed report query/export. Live dump `PASS` requires observed dump export. This leftover does not start the JAR. `#232` / `#221` / `#204` harness PASS is still not that walk.

See [engineering/g2-h5-every-page-form-write-report-remainder-honesty-EVIDENCE.md](./engineering/g2-h5-every-page-form-write-report-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g2-h5-every-page-form-write-report-remainder-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product-wide G2 | Explicitly `g2_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. Combining #232+#221+#204 harness PASS is not product G2 (`combined_232_221_204_is_not_product_g2=true`). |
| #232 remainder as live JAR / H5 / report / dump | `honesty_232_is_not_live_jar=true`. Remainder honesty is not a live walk. |
| #221 harness as live report / dump / H5 | `honesty_221_is_not_live_report=true`. |
| #204 harness as live JAR / live H5 | `harness_204_is_not_live_jar=true`. |
| Putting SurfaceGuard on H5 home / files / notify / login / host | Still OPEN. `harness_204_is_not_h5_open_covered=true`. |
| Report export is a physical dump | `report_is_physical_dump=false`. |
| Dump is report Task submit | `dump_is_report_task_submit=false`. |
| Redo of #163 / #173 / #204 / #221 / #232 | `redone_163=false`, `redone_173=false`, `redone_204=false`, `redone_221=false`, `redone_232=false`. |
| Live JAR + issued-context HTTP on this agent VM | Base URL unset; nothing on `:8080` / `:8088`. Honesty SKIP. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product Linux/ARM Profile remainder close (#236/#193/#157) / product G4 remainder close (#251/#230/#222) / product G10 remainder close (#243/#228) / product G5 remainder close (#235) / product S0 leftover (#241) / product G17 leftover (#233) / product G1 leftover (#238) / product G7 leftover (#239) / product G9 leftover (#237) / product G3 leftover (#226) / product G6 leftover (#225) | Sibling leftovers. Not rewritten. Not claimed. |
| Docker / Compose | Forbidden. Native JAR only when the operator already started it. |

## Honest leftovers (not claimed)

- Live product-JAR + issued-context POST `/submissions` and submit-start dual-check walk
- Live H5 every-page SurfaceGuard browser walk
- Live product-JAR + issued-context report query/export walk
- Live product-JAR + issued-context physical dump / raw export walk
- Product-wide G2 close
- Putting SurfaceGuard on H5 home / files / notify / login / host
- Treating #232 / #221 / #204 harness PASS as a live walk
- Ops-level `pg_dump` through SurfaceGuard
- Redo of #163 / #173 / #204 / #221 / #232
