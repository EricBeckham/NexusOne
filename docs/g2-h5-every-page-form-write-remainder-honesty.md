# G2 leftover — H5 every-page / form.write remainder honesty beyond #204/#163/#173

S0+S1 remainder after the #204 H5 every-page / form.write honesty leftover ([g2-h5-every-page-form-write-honesty.md](./g2-h5-every-page-form-write-honesty.md)), the #163 H5 every-page inventory leftover ([g2-h5-every-page.md](./g2-h5-every-page.md)), and the #173 form.write / submit-start dual-check honesty leftover ([g2-form-write-honesty.md](./g2-form-write-honesty.md)). Rebased onto `origin/main` `c3ffd50` (Merge #212 G7 withdraw browser-evidence leftover honesty after #230). **This does not close product G2.** **G18 stays closed.** This leftover does **not** redo #112 / #116 / #137 / #144 / #150 / #155 / #163 / #173 / #204 product code. It does **not** redo #204. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0 / M0. `g2_close=NOT_CLAIMED`.

#204 already inventories the combined honesty gate as always-green **PASS** (H5 every-page harness + form.write / submit-start dual-check) and live JAR / live H5 as honest **SKIP**. That #204 harness PASS is **not** a live JAR walk, **not** a live H5 every-page walk, **not** putting SurfaceGuard on OPEN H5 pages, and **not** a product G2 close. The leftover here is the **honesty gate beyond those three**: the #204 harness contract stays **PASS** and is explicitly **not** a live JAR walk (`harness_204_is_not_live_jar=true`) and **not** a live H5 walk (`harness_204_is_not_live_h5=true`). #204 COVERED inventory is **not** OPEN pages (`harness_204_is_not_h5_open_covered=true`). Live product-JAR + issued-context HTTP is **PASS** only when a healthy JAR, issued context, and observed submissions / submit-start / H5 walks exist. Missing JAR/context is an honest **SKIP**. It never fabricates `PASS`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g2-h5-every-page` (#163), `g2-form-write-honesty` (#173), `g2-h5-every-page-form-write-honesty` (#204), `g2-report-physical-dump-honesty` (#221), `g10-leftover-honesty-remainder` (#228), `g3-outbox-consume-idempotent-honesty` (#226), `g9-live-multi-jvm-replica-remainder-honesty` (#229), `g4-hot-purge-staging-leftover-honesty` (#230), `g7-withdraw-browser-evidence-honesty` (#212), `g6-recovery-port-dry-run-remainder-honesty` (#225), `s0-secret-rotate-getenv-remainder-honesty` (#223), `g4-pkce-spa-verifier-remainder-honesty` (#222), `g17-separation-leftover-honesty` (#216), `g15-wx-dt-native-bridge-remainder` (#220), `g1-queryast-export-honesty` (#215), `g17-gm-tls-nginx-handshake-honesty` (#210), `g5-publishhook-ddl-honesty` (#217), `g2-ops-pg-dump-honesty` (#188), `g17-mfa-recovery-vue-otp-honesty` (#205), `g9-redis-down-invalidate-remainder` (#197), `g9-sentinel-cluster-remainder-honesty` (#209), `g4-export-wizard-deny-honesty` (#203), `g10-leftover-honesty` (#202), `g5-mid-publish-failure-honesty` (#196), `g3-outbox-consume-crash-honesty` (#195), `g1-queryast-boundsql-honesty` (#191), `g15-wx-dt-native-bridge-honesty` (#198), `s0-secret-rotate-without-restart-honesty` (#200), `linux-arm-profile-leftover-honesty` (#193), `g7-query-expense-travel-honesty` (#192), `g4-p1-staging-audit-events-honesty` (#190), `s0s1-live-redis-minio-isolation-honesty` (#185), `g17-separation-honesty` (#187), `g4-hot-purge-90d-honesty` (#183), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g4-pkce-spa-verifier-honesty` (#177), `s0-secret-getenv-snapshot-honesty` (#180), `g7-withdraw-browser-honesty` (#175), `g9-redis-down-invalidate-honesty` (#174), `g10-file-reconcile-honesty` (#169), `g5-mid-publish-failure` (#145), `g5-browser-failed-publish` (#171), `g2-form-submission-write`, `g2-submit-start-form-write`, `g2-h5-permission`, `g2-ops-pg-dump`, `g2-todo-http-surface`, `g2-tenant-only-catalog`, and `g2-physical-dump-surface`. Keep this file.

Baseline: Java 21, Spring Boot **4.1.1**. No Docker.

## What this leftover covers

| Path | Behavior |
|---|---|
| #204 H5 every-page / form.write honesty | Already on `main`. Combined harness stays **PASS**. Do **not** redo. That PASS is **not** a live JAR walk / live H5 walk / H5-open-covered / product G2 close. |
| #163 H5 every-page harness | Already on `main`. COVERED list/detail/form/todo/query. OPEN home/files/notify/login/host. Do **not** rewrite `h5PageInventory` / `H5PageSurfaceCatalog`. |
| #173 form.write honesty | Already on `main`. FormSurfaceGuard `form.write` + submit-start dual-check **PASS**. Do **not** rewrite `G2FormWriteHonesty` / `FormSurfaceGuard`. |
| #204 harness vs live JAR | Always-green `harness_204_contract=PASS` **and** `harness_204_is_not_live_jar=true`. `#204` harness is **not** a live JAR walk. |
| #204 harness vs live H5 | `harness_204_is_not_live_h5=true`. `#204` harness is **not** a live H5 every-page walk. |
| #204 COVERED vs OPEN pages | `harness_204_is_not_h5_open_covered=true` and `h5_open_pages_still_open=true`. COVERED inventory is **not** home/files/notify/login/host. |
| Honest live JAR SKIP | `live_jar_walk` is **PASS** only when a live JAR was present **and** submissions + submit-start were observed. Missing `NEXUS_BASE_URL` / issued context is honest **SKIP**. |
| Honest live H5 SKIP | `live_h5_walk` is **PASS** only when a live JAR was present **and** an `/m/*` walk was observed. Missing JAR is honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects treating #204 harness as a live JAR / live H5 walk / H5-open-covered, rejects `g2_close=CLOSED`, rejects #112/#116/#137/#144/#150/#155/#163/#173/#204 redo flags, and requires sibling leftover rows kept (including #204, #221, #228, #226, #229, #230, #212, #225, #223, #222, #216, #220, #215, #210, and #217). |

No Docker. No product-JAR Playwright driver. Parent leftovers stay siblings.

## How to run

```bash
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-g2-close.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-live-jar-pass.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-live-h5-pass.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-harness-as-live.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-redone.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-h5-open-closed.env fail
```

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g2-h5-every-page.sh
# ./scripts/g2-form-write-honesty.sh
# ./scripts/g2-h5-every-page-form-write-honesty.sh
```

`PASS` for live JAR requires all of: reachable product JAR, issued context, observed `POST /api/v1/p3/forms/{id}/submissions`, and observed submit-start dual-check. Live H5 every-page `PASS` additionally requires an observed `/m/*` walk. This leftover does not start the JAR. `#204` harness PASS is still not that walk (`harness_204_is_not_live_jar=true`).

See [engineering/g2-h5-every-page-form-write-remainder-honesty-EVIDENCE.md](./engineering/g2-h5-every-page-form-write-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g2-h5-every-page-form-write-remainder-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product-wide G2 | Explicitly `g2_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. `#204` harness PASS is not product G2 (`harness_204_is_not_product_g2=true`). |
| #204 harness as live JAR | `harness_204_is_not_live_jar=true`. Combined honesty gate is not a live walk. |
| #204 harness as live H5 | `harness_204_is_not_live_h5=true`. |
| Putting SurfaceGuard on H5 home / files / notify / login / host | Still OPEN. `harness_204_is_not_h5_open_covered=true`. |
| submit-start is `form.write` only | Dual-check. Process start stays required. |
| Redo of #163 / #173 / #204 | `redone_163=false`, `redone_173=false`, `redone_204=false`. |
| Live JAR + issued-context HTTP on this agent VM | Base URL unset; nothing on `:8080` / `:8088`. Honesty SKIP. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product G6 remainder close (#225) / product G2 report-dump leftover (#221) / product G10 remainder leftover (#228) / product G3 leftover (#226) / product G9 remainder leftover (#229) / product G4 leftover (#230) / product G7 leftover (#212) / product G1 close (#215) / product G4 PKCE close (#222) | Sibling leftovers. Not rewritten. Not claimed. |
| Docker / Compose | Forbidden. Native JAR only when the operator already started it. |

## Honest leftovers (not claimed)

- Live product-JAR + issued-context POST `/submissions` and submit-start dual-check walk
- Live H5 every-page SurfaceGuard browser walk
- Product-wide G2 close
- Putting SurfaceGuard on H5 home / files / notify / login / host
- Treating #204 harness PASS as a live JAR / live H5 walk
- Ops-level `pg_dump` through SurfaceGuard
- Redo of #163 / #173 / #204
