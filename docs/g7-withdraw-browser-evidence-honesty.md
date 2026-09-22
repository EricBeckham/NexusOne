# G7 leftover — browser-visible withdraw evidence honesty beyond #82/#103/#113/#159/#175/#192

S0+S1 remainder after leave withdraw HTTP ([g7-withdraw-evidence.md](./g7-withdraw-evidence.md) #82), expense/travel withdraw HTTP ([g7-withdraw-expense-travel.md](./g7-withdraw-expense-travel.md) #103), Vue `todo-withdraw` click + HTTP reachability ([g7-withdraw-browser.md](./g7-withdraw-browser.md) #113/#159), Vue `todo-withdraw` honesty ([g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) #175), and expense/travel query honesty ([g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md) #192). Rebased onto `origin/main` `72630d4` (Merge #230 G4 hot-purge/P1 staging leftover honesty after #231 claim-list tip-sync after #229 G9 live multi-JVM/replica remainder leftover after #226 G3 consume-idempotent leftover after #228 G10 leftover honesty remainder after #221 G2 report/physical-dump SurfaceGuard leftover; includes #223 S0 rotate/getenv remainder leftover, #222 G4 PKCE SPA verifier remainder leftover, #216 G17 SoD leftover honesty, #218 claim-list tip sync, #220 G15 wx-dt/native-bridge remainder leftover, #215 G1 QueryAst/export leftover honesty, #210 G17 GMTLS handshake leftover, #217 G5 PublishHook DDL leftover, #209 G9 Sentinel/Cluster remainder honesty, #188 G2 ops pg_dump honesty, #205 G17 MFA recovery/OTP honesty, #197 G9 Redis-down remainder, and #204 G2 H5/form.write honesty). **This does not close product G7.** **G18 stays closed.** This leftover does **not** redo #82 / #103 / #113 / #140 / #159 / #175 / #192 product code. It does **not** rewrite `TodoInboxView.vue`, Playwright, or withdraw ITs. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0 / M0. `g7_close=NOT_CLAIMED`.

#82 / #103 already prove scripted HTTP withdraw on leave + expense + travel. #113/#159 already prove the desktop `data-testid=todo-withdraw` control reaches `POST /api/v1/p3/path/instances/{id}/withdraw` (stub login + Vitest). #175 already locks live JAR/IdP browser withdraw as honest **SKIP** unless observed. The leftover here is the **browser-visible evidence honesty gate** beyond those: stub walk.json + screenshots for leave / expense / travel stay always-green **PASS**. That stub walk is **not** a live product-JAR / live IdP browser-visible withdraw. Live JAR/IdP browser-visible withdraw is **PASS** only when a healthy JAR, issued context, live/Keycloak IdP, Chrome, and an observed live `todo-withdraw` click exist. Missing live path is honest **SKIP**. It never fabricates `PASS`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g7-withdraw-evidence](./g7-withdraw-evidence.md) (#82), [g7-withdraw-expense-travel](./g7-withdraw-expense-travel.md) (#103), [g7-withdraw-browser](./g7-withdraw-browser.md) (#113/#159), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md) (#175), [g7-query-expense-travel](./g7-query-expense-travel.md) (#140), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md) (#188), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md) (#197), [g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md) (#174), [g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md) (#200), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171), [g9-live-multi-jvm-replica](./g9-live-multi-jvm-replica.md) (#161), [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md) (#169), [g17-mfa-recovery-email-honesty](./g17-mfa-recovery-email-honesty.md) (#172), and [g3-claim-crash-window](./g3-claim-crash-window.md) (#170). Keep this file.

Baseline: Java 21, Spring Boot **4.1.1**. No Docker.

## What this leftover covers

| Path | Behavior |
|---|---|
| #82 leave withdraw HTTP | Already on `main`. Always-green **PASS** when the leftover doc / IT contract remains. Do **not** rerun `G7LeaveWithdrawIT`. |
| #103 expense/travel withdraw HTTP | Already on `main`. Always-green **PASS** when the leftover doc remains. Do **not** rerun `G7ExpenseTravelWithdrawIT`. |
| #113/#159 Vue `todo-withdraw` | Already on `main`. Control `data-testid=todo-withdraw` still reaches `POST /api/v1/p3/path/instances/{id}/withdraw`. Always-green **PASS**. |
| #175 leftover honesty | Already on `main`. Parent honest-skip fixture still verifies. Do **not** redo the #175 probe. |
| #192 query leftover honesty | Sibling leftover. Query surfaces stay beside this package. `redone_192=false`. |
| Browser-visible stub walk | Committed [walk.json](./g7-withdraw-browser/walk.json) + screenshots for leave / expense / travel `todo-withdraw`. Always-green **PASS**. Stub login is **not** a live walk. |
| Honest live SKIP | `live_jar_idp_browser_withdraw` / `live_browser_visible_withdraw` are **PASS** only when a live JAR, issued context, live/Keycloak IdP, Chrome, and an observed live click exist. Missing path is honest **SKIP**. |
| Live browser query click | Still open. This leftover records `live_browser_query_click=SKIP` and rejects `PASS`. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects stub-as-live PASS, rejects `g7_close=CLOSED`, rejects #82/#103/#113/#140/#159/#175/#192 redo flags, and requires sibling leftover rows kept (including #230 G4 hot-purge/P1 staging leftover, #229 G9 live multi-JVM/replica remainder leftover, #226 G3 consume-idempotent leftover, #228 G10 leftover honesty remainder, #221 G2 report/physical-dump honesty, #225 G6 RecoveryPort dry-run remainder, #223 S0 rotate/getenv remainder, #222 G4 PKCE SPA verifier remainder, #216 G17 SoD leftover honesty, #220 G15 wx-dt/native-bridge remainder, #215 G1 QueryAst/export honesty, #210 G17 GMTLS handshake honesty, #217 G5 PublishHook DDL honesty, #209 G9 Sentinel/Cluster remainder honesty, #188 G2 ops pg_dump honesty, #205 G17 MFA recovery/OTP honesty, #197 G9 Redis-down remainder, and #204 G2 H5/form.write honesty). |

No Docker. No product-JAR Playwright driver. Parent leftovers stay siblings.

## How to run

```bash
./scripts/g7-withdraw-browser-evidence-honesty.sh
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report <out>/report.env
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-live-pass.env fail
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-g7-close.env fail
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-redone.env fail
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-stub-as-live.env fail
```

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g7/run-withdraw-leave.sh
# ./scripts/g7/run-withdraw-expense-travel.sh
# ./scripts/g7/run-withdraw-browser.sh
# ./scripts/g7-withdraw-browser-honesty.sh
# ./scripts/g7-query-expense-travel-honesty.sh
```

`PASS` for live JAR/IdP browser-visible withdraw requires all of: reachable product JAR, issued `NEXUS_G7_LIVE_CONTEXT_ID`, live/Keycloak IdP, Chrome, and `NEXUS_G7_LIVE_WITHDRAW_OBSERVED=true`. This leftover does not start the JAR.

See [engineering/g7-withdraw-browser-evidence-honesty-EVIDENCE.md](./engineering/g7-withdraw-browser-evidence-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g7-withdraw-browser-evidence-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G7 close | Explicitly `g7_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live product-JAR + live IdP browser withdraw | Honest SKIP on this agent VM. Stub login / stub walk.json is not a live walk. |
| Treating stub screenshots as live evidence | Honesty gate rejects `live_browser_visible_withdraw=PASS` without an observed live click. |
| #82 / #103 / #113 / #140 / #159 / #175 / #192 redo | Sibling leftovers. `redone_*=false`. |
| Live browser query click | Still open. Not this package. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product G17 close (#205/#210/#216) / product G5 close (#217) / product G1 close (#215) / product G15 close (#220) / product G4 close (#222/#230) / product S0 close (#223) / product G6 close (#186/#225) / product G9 close (#197/#209/#229) / product G2 close (#204/#188/#221) / product G10 close (#202/#228) / product G3 close (#195/#226) | Sibling leftovers. Not rewritten. Not claimed. |
| Docker / Compose | Forbidden. Native JAR only when the operator already started it. |

## Honest leftovers (not claimed)

- Live product-JAR + live/Keycloak IdP Playwright click of `data-testid=todo-withdraw`
- Live browser-visible withdraw on leave + expense + travel with issued context
- Live browser query click for expense / travel
- Three-type product G7 close
- Redo of #82 / #103 / #113 / #140 / #159 / #175 / #192
