# G2 leftover — H5 every-page / form.write honesty beyond #163/#173

S0+S1 remainder after the #163 H5 every-page inventory leftover ([g2-h5-every-page.md](./g2-h5-every-page.md)) and the #173 form.write / submit-start dual-check honesty leftover ([g2-form-write-honesty.md](./g2-form-write-honesty.md)). Rebased onto `origin/main` `cb0f339` (Merge #206 tip-sync after #203). **This does not close product G2.** **G18 stays closed.** This leftover does **not** redo #112 / #116 / #137 / #144 / #150 / #155 / #163 / #173 product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0 / M0. `g2_close=NOT_CLAIMED`.

#163 already inventories which `/m/*` routes are COVERED (list/detail/form/todo/query) versus still OPEN (home/files/notify/login/host/harness). #173 already asserts FormSurfaceGuard `form.write` + submit-start dual-check **PASS** and live JAR **SKIP**. The leftover here is the **combined honesty gate**: leftover harness contracts stay always-green **PASS**. Live product-JAR + issued-context HTTP is **PASS** only when a healthy JAR, issued context, and observed submissions / submit-start / H5 walks exist. Missing JAR/context is an honest **SKIP**. It never fabricates `PASS`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g2-h5-every-page](./g2-h5-every-page.md) (#163), [g2-form-write-honesty](./g2-form-write-honesty.md) (#173), [g2-form-submission-write](./g2-form-submission-write.md), [g2-submit-start-form-write](./g2-submit-start-form-write.md), [g2-h5-permission](./g2-h5-permission.md), [g2-ops-pg-dump](./g2-ops-pg-dump.md), [g2-todo-http-surface](./g2-todo-http-surface.md), [g2-tenant-only-catalog](./g2-tenant-only-catalog.md), [g2-physical-dump-surface](./g2-physical-dump-surface.md), [g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md) (#191), [g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md) (#195), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md) (#203), [g1-queryast-boundsql-guard](./g1-queryast-boundsql-guard.md), [g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md), [s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md), [g17-separation-honesty](./g17-separation-honesty.md), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md), [g5-browser-failed-publish](./g5-browser-failed-publish.md), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md), [g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md), and [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md). This file is the honesty leftover beside them.

Baseline: Java 21, Spring Boot **4.1.1**. No Docker.

## What this leftover covers

| Path | Behavior |
|---|---|
| #163 H5 every-page harness | Already on `main`. COVERED list/detail/form/todo/query. OPEN home/files/notify/login/host. Do **not** rewrite `h5PageInventory` / `H5PageSurfaceCatalog`. |
| #173 form.write honesty | Already on `main`. FormSurfaceGuard `form.write` + submit-start dual-check **PASS**. Do **not** rewrite `G2FormWriteHonesty` / `FormSurfaceGuard`. |
| Leftover harness contract | Always-green `h5_every_page_harness=PASS` and `form_write_harness=PASS` when the #163 inventory still classifies and the #173 honest fixture still verifies. |
| Honest live JAR SKIP | `live_jar_walk` / `live_h5_walk` are **PASS** only when a live JAR was present **and** the walks were observed. Missing `NEXUS_BASE_URL` / issued context is honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g2_close=CLOSED`, rejects #112/#116/#137/#144/#150/#155/#163/#173 redo flags, rejects claiming H5 open pages covered, and requires sibling leftover rows kept (including #191 BoundSql honesty, #195 G3 crash honesty, #196 G5 mid-publish honesty, #202 G10 leftover honesty, and #203 G4 export-wizard deny honesty). |

No Docker. No product-JAR Playwright driver. Parent leftovers stay siblings.

## How to run

```bash
./scripts/g2-h5-every-page-form-write-honesty.sh
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report <out>/report.env
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-g2-close.env fail
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-live-jar-pass.env fail
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-redone.env fail
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-h5-open-closed.env fail
```

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g2-h5-every-page.sh
# ./scripts/g2-form-write-honesty.sh
```

`PASS` for live JAR requires all of: reachable product JAR, issued context, observed `POST /api/v1/p3/forms/{id}/submissions`, and observed submit-start dual-check. Live H5 every-page `PASS` additionally requires an observed `/m/*` walk. This leftover does not start the JAR.

See [engineering/g2-h5-every-page-form-write-honesty-EVIDENCE.md](./engineering/g2-h5-every-page-form-write-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g2-h5-every-page-form-write-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product-wide G2 | Explicitly `g2_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Redo of #163 / #173 | Parent leftover product modules stay on those leftovers. |
| Putting SurfaceGuard on H5 home / files / notify / login / host | Still OPEN. Honesty gate requires `h5_open_pages_still_open=true`. |
| submit-start is `form.write` only | Dual-check. Process start stays required. |
| Live JAR + issued-context HTTP on this agent VM | Base URL unset; nothing on `:8080` / `:8088`. Honesty SKIP. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product G1 BoundSql close (#191) / product G3 close (#195) / product G5 close (#196) / product G10 close (#202) / product G4 close (#203) | Sibling leftovers. Not rewritten. Not claimed. |
| Docker / Compose | Forbidden. Native JAR only when the operator already started it. |

## Honest leftovers (not claimed)

- Live product-JAR + issued-context POST `/submissions` and submit-start dual-check walk
- Live H5 every-page SurfaceGuard browser walk
- Product-wide G2 close
- Putting SurfaceGuard on H5 home / files / notify / login / host
- Ops-level `pg_dump` through SurfaceGuard
