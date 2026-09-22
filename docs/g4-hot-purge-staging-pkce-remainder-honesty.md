# G4 leftover — 90-day hot-purge / P1 staging / PKCE SPA remainder honesty beyond #230/#222

S0+S1 remainder **after** the #230 90-day hot-purge / P1 staging leftover honesty ([g4-hot-purge-staging-leftover-honesty.md](./g4-hot-purge-staging-leftover-honesty.md)) and the #222 live Keycloak CA / SPA PKCE remainder leftover ([g4-pkce-spa-verifier-remainder-honesty.md](./g4-pkce-spa-verifier-remainder-honesty.md)). Rebased onto `origin/main` `3081fa7` (Merge #243 G10 file/object reconcile leftover honesty after #241 SecretPort rotate/getenv leftover honesty / #235 G5 PublishHook/DDL leftover honesty remainder / #233 G17 MFA/OTP leftover honesty remainder / #238 G1 FieldPolicy mask leftover honesty / #239 G7 expense/travel query leftover honesty remainder / #237 G9 Redis-down leftover honesty remainder / #232 G2 H5/form.write leftover honesty remainder / #212 G7 withdraw browser-evidence leftover honesty / #230 G4 hot-purge/P1 staging leftover honesty / #222 G4 SPA PKCE remainder leftover honesty). **G18 stays closed.** This leftover does **not** redo #123 / #147 / #156 / #177 / #183 / #190 / #222 / #230 product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** DROP `platform.audit_record`. It does **not** switch `GET /tenants/{id}/audit-events` off staging. It does **not** start `kc.sh`. It does **not** redo P0 / M0. It does **not** claim product G4 close. `g4_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g4-hot-purge-90d](./g4-hot-purge-90d.md) (#123), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md) (#183), [g4-p1-staging-audit-events](./g4-p1-staging-audit-events.md) (#156), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md) (#190), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), this file, [g4-pkce-spa-verifier](./g4-pkce-spa-verifier.md) (#147), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md) (#177), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g5-publishhook-ddl-remainder-honesty](./g5-publishhook-ddl-remainder-honesty.md) (#235), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g10-file-object-reconcile-remainder-honesty](./g10-file-object-reconcile-remainder-honesty.md) (#243), [g17-mfa-recovery-vue-otp-remainder-honesty](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md) (#195), [g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty](./g17-separation-honesty.md) (#187), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md) (#175), [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md) (#169), [g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md) (#191), [g1-queryast-boundsql-guard](./g1-queryast-boundsql-guard.md) (#146), [g1-fieldpolicy-mask-second-auth](./g1-fieldpolicy-mask-second-auth.md) (#162), [g2-h5-every-page](./g2-h5-every-page.md) (#163), [g2-form-write-honesty](./g2-form-write-honesty.md) (#173), [g9-live-multi-jvm-replica](./g9-live-multi-jvm-replica.md) (#161), and [g9-sentinel-cluster-honesty](./g9-sentinel-cluster-honesty.md) (#148). Keep this file.

#230 already inventories window policy / staging-like as always-green **PASS** and live `:5432` / JAR / Quartz / MinIO as honest **SKIP**. #222 already inventories SPA mint / #177 harness as always-green **PASS** and live Keycloak CA / token exchange as honest **SKIP**. The leftover here is the **honesty remainder beyond those two**: those cells stay honest; parent leftover honesty fixtures stay **PASS**. Live purge / staging / JAR / Keycloak / CA / laptop click are **PASS** only when a live path is present **and** the walk was observed. Missing live path is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #230 leftover cells | Window policy **PASS**, staging-like **PASS**, live purge / Quartz / MinIO / staging / JAR **SKIP**, `g4_close=NOT_CLAIMED`, `hot_table_dropped=false`, `switched_off_staging=false`. `leftover_230_cells_honest=PASS`. `leftover_230_honesty_gate=PASS`. Do not redo. |
| #222 leftover cells | SPA mint **PASS**, #177 harness **PASS**, live Keycloak / CA / laptop click **SKIP**, `g4_close=NOT_CLAIMED`. `leftover_222_cells_honest=PASS`. `leftover_222_honesty_gate=PASS`. Do not redo. |
| #183 / #190 / #177 leftover cells | Parent leftover cells stay honest. `leftover_183_cells_honest=PASS`. `leftover_190_cells_honest=PASS`. `leftover_177_cells_honest=PASS`. Do not redo. |
| Live `:5432` / JAR 90-day prefix DELETE | `live_purge_window=PASS` only when live PG + product JAR + observed prefix DELETE. Missing live path is honest **SKIP**. |
| Live Quartz `audit.hot.purge` | `live_quartz=PASS` only when a 03:00 UTC walk was observed. Schedule defaults off. Honest **SKIP**. |
| Live MinIO cold drill | `live_minio_cold=PASS` only when live object-store + observed cold copy. Fake / counting storage is **SKIP**. |
| Live `:5432` staging query+export | `live_staging_5432=PASS` only when listener + observed query **and** export. Missing live DB is honest **SKIP**. |
| Live JAR / issued context | `live_jar_context_walk=PASS` only when listener + observed walk. Unavailable is honest **SKIP**. |
| Live Keycloak SPA PKCE | `live_keycloak_pkce=PASS` only with `token_exchange_observed=true`. Discovery-only / #222 collect is **SKIP**. |
| Live CA trust / laptop click | **PASS** only when those walks were observed. Notebook-only stays **SKIP**. |
| Switching P1 GET off staging | Still **OPEN**. `switched_off_staging=false`. |
| Product G4 close | **NOT_CLAIMED**. |
| Sibling leftover rows | `docs/README.md` keeps #230 / #222 and this leftover, plus #243 `g10-file-object-reconcile-remainder-honesty`, #241 `s0-secret-rotate-getenv-leftover-honesty`, #235 `g5-publishhook-ddl-remainder-honesty`, #233 `g17-mfa-recovery-vue-otp-remainder-honesty`, #238 `g1-fieldpolicy-mask-second-auth-remainder-honesty`, #239 `g7-query-expense-travel-honesty-remainder`, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g4_close=CLOSED`, rejects `hot_table_dropped=true`, rejects `switched_off_staging=true`, rejects #230/#222/#183/#190/#177/#147 redo flags, and requires leftover rows kept (including #243 / #241 / #235 / #233 / #238 / #239). |

No Docker. No product-JAR start. No `kc.sh`. `#230` / `#222` stay the sibling reproduction commands. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G4 close | Explicitly `g4_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live `:5432` / JAR 90-day prefix DELETE | Honest SKIP when live path is missing. #230 collect is not a live walk. |
| Live Quartz scheduled purge | Schedule stays opt-in / default off. |
| Live MinIO cold drill | Fake / counting storage is not live S3. |
| Switching P1 GET off staging | `switched_off_staging=false`. Still open. |
| Live Keycloak token exchange / trusted laptop CA click | Honest SKIP when unobserved. |
| #123 / #147 / #156 / #177 / #183 / #190 / #222 / #230 | Sibling leftovers on `main`. `redo_230=false`. `redo_222=false`. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

```bash
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-live-purge-pass.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-live-staging-pass.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-live-pkce-pass.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-g4-close.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-redone.env
```

#230 / #222 stay the sibling reproduction commands. This leftover does **not** invoke them:

```bash
# do not run for this leftover
# ./scripts/g4-hot-purge-staging-leftover-honesty.sh
# ./scripts/g4-pkce-spa-verifier-remainder-honesty.sh
```

Parent `--verify-report` on already-committed #230 / #222 honesty fixtures is harness evidence.

Java 21 / Boot **4.1.1**. No Docker. Live product-JAR / `:5432` / Keycloak are **not** required.

See [engineering/g4-hot-purge-staging-pkce-remainder-honesty-EVIDENCE.md](./engineering/g4-hot-purge-staging-pkce-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g4-hot-purge-staging-pkce-remainder-honesty/`.

## Honest leftovers (not claimed)

- Live product `:5432` 90-day prefix DELETE + suffix `verifyChain()`
- Live Quartz `audit.hot.purge` at 03:00 UTC
- Live MinIO / object-store cold drill as a purge prerequisite
- Switching P1 GET off staging onto M0-11
- Live `:5432` query+export on a host that actually has `platform.audit_event`
- Live Keycloak Authorization Code + PKCE with an SPA-held verifier
- Human laptop click that trusts the loopback CA
- Product-wide G4 close
- Redo of #230 / #222 / #183 / #190 / #177 / #147
