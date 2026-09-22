# G4 leftover — 90-day hot-purge / P1 staging audit-events remainder honesty beyond #183/#190

S0+S1 remainder **after** the #123 90-day hot-prefix purge ([g4-hot-purge-90d.md](./g4-hot-purge-90d.md)), the #183 hot-purge window honesty leftover ([g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md)), the #156 P1 staging audit-events leftover ([g4-p1-staging-audit-events.md](./g4-p1-staging-audit-events.md)), and the #190 staging-events honesty leftover ([g4-p1-staging-audit-events-honesty.md](./g4-p1-staging-audit-events-honesty.md)). Rebased onto `origin/main` `97a2adc` (Merge #231 S0+S1 claim-list tip-sync after #229 G9 live multi-JVM / replica remainder leftover honesty / #226 G3 Outbox consume-idempotent leftover honesty / #228 G10 leftover honesty remainder / #221 G2 report/physical-dump leftover honesty / #225 G6 RecoveryPort dry-run / non-product-HA remainder leftover honesty / #224 S0+S1 claim-list tip-sync / #223 S0 rotate/getenv remainder leftover honesty / #222 G4 live Keycloak CA / SPA PKCE remainder leftover honesty / #216 G17 SoD admin-force leftover honesty / #218 S0+S1 claim-list tip-sync / #220 G15 wx-dt/native-bridge remainder leftover honesty). **G18 stays closed.** This leftover does **not** redo #123 / #136 / #147 / #152 / #156 / #166 / #177 / #183 / #190 / #203 / #222 / #223 / #225 / #221 / #228 / #226 / #229 product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** DROP `platform.audit_record`. It does **not** switch `GET /tenants/{id}/audit-events` off staging. It does **not** redo P0 / M0. It does **not** claim product G4 close. `g4_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g4-hot-purge-90d](./g4-hot-purge-90d.md) (#123), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md) (#183), [g4-p1-staging-audit-events](./g4-p1-staging-audit-events.md) (#156), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md) (#190), this file, [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md) (#203), [g4-export-wizard-deny](./g4-export-wizard-deny.md) (#166), [g4-export-wizard](./g4-export-wizard.md) (#152), [g4-guard-deny-audit](./g4-guard-deny-audit.md) (#136), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md) (#177), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md) (#197), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty](./g17-separation-honesty.md) (#187), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171), and [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md) (#175). Keep this file.

#183 already inventories the in-memory 90-day / 365-day window policy as always-green **PASS** and live `:5432` / JAR / Quartz / MinIO as honest **SKIP**. #190 already inventories staging-like query/export as always-green **PASS** and live `:5432` `platform.audit_event` as honest **SKIP**. The leftover here is the **remainder honesty gate** beyond those two:

| Remaining path | Honesty |
|---|---|
| #183 leftover cells | Window policy **PASS**, live purge / Quartz / MinIO **SKIP**, `g4_close=NOT_CLAIMED`, `hot_table_dropped=false`. `leftover_183_cells_honest=PASS`. |
| #190 leftover cells | Staging-like **PASS**, live `:5432` **SKIP**, `switched_off_staging=false`, `g4_close=NOT_CLAIMED`. `leftover_190_cells_honest=PASS`. |
| Window policy (90d / 365d, no DROP) | Always-green **PASS**. Not a #123 Job / Zonky redo. |
| Staging-like query/export | Always-green **PASS**. GET still on staging. Not a #156 Zonky redo. |
| Live `:5432` / JAR 90-day prefix DELETE | **PASS** only when live PG + product JAR + observed prefix DELETE. Missing live path is honest **SKIP**. |
| Live Quartz `audit.hot.purge` | **PASS** only when a 03:00 UTC walk was observed. Schedule defaults off. Honest **SKIP**. |
| Live MinIO cold drill | **PASS** only when live object-store + observed cold copy. Fake / counting storage is **SKIP**. |
| Live `:5432` staging query+export | **PASS** only when listener + credential + observed walk. Missing live DB / JAR / context is honest **SKIP**. |
| Live JAR / issued context | **PASS** only when `NEXUS_BASE_URL` (or leftover live base) + issued context + listener. Unavailable is honest **SKIP**. |
| Switching P1 GET off staging | Still **OPEN**. `switched_off_staging=false`. |
| Product G4 close | **NOT_CLAIMED**. |

Missing live JAR / context / `:5432` is an honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Surface | Behavior |
|---|---|
| #183 leftover cells | Committed #183 honesty fixture stays PASS / live SKIP. Do not redo. |
| #190 leftover cells | Committed #190 agent-vm artifact stays staging-like PASS / live SKIP. Do not redo. |
| Remainder honesty gate | `--verify-report` rejects fake live purge / staging / JAR PASS, rejects `g4_close=CLOSED`, rejects `hot_table_dropped=true`, rejects `switched_off_staging=true`, and rejects #123/#156/#183/#190/#222/#223/#225/#221/#228/#226/#229 redo flags. Requires leftover rows kept (including #183 / #190 / #222 / #223 / #225 / #221 / #228 / #226 / #229 / #216 / #220 / #203). |
| Live walks | `live_purge_window` / `live_staging_5432` / `live_jar_context_walk` are **PASS** only with reachable live path + observed walk. |

This leftover does **not** start PostgreSQL, the product JAR, Quartz, or MinIO. Parent leftover scripts stay siblings.

## How to run

```bash
./scripts/g4-hot-purge-staging-leftover-honesty.sh
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-live-purge-pass.env fail
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-live-staging-pass.env fail
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-g4-close.env fail
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-switched-off-staging.env fail
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-redo.env fail
```

#123 / #156 / #183 / #190 stay the sibling reproduction commands:

```bash
# do not run for this leftover
# ./scripts/g4-hot-purge-90d.sh
# ./scripts/g4-hot-purge-90d-honesty.sh
# ./scripts/g4-p1-staging-audit-events.sh
# ./scripts/g4-p1-staging-audit-events-honesty.sh
```

Parent `--verify-report` on honest fixtures is allowed as leftover-cell evidence.

See [engineering/g4-hot-purge-staging-leftover-honesty-EVIDENCE.md](./engineering/g4-hot-purge-staging-leftover-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g4-hot-purge-staging-leftover-honesty/`. Walk JSON: [g4-hot-purge-staging-leftover-honesty/walk.json](./g4-hot-purge-staging-leftover-honesty/walk.json).

## What this does not claim

| Leftover | Why |
|---|---|
| Product G4 close | Explicitly `g4_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live `:5432` / JAR 90-day prefix DELETE | Honest SKIP when live path is missing. Zonky #123 is not a live walk. |
| Live Quartz scheduled purge | Schedule stays opt-in / default off. |
| Live MinIO cold drill | Fake / counting storage is not live S3. |
| Switching P1 GET off staging | `switched_off_staging=false`. Still open. |
| Live `:5432` staging query+export | Honest SKIP when credential / listener / JAR / context is missing. |
| #123 Job / Zonky IT redo | Sibling leftover. `redo_123=false`. |
| #156 staging harness | Sibling leftover. `redo_156=false`. |
| #183 / #190 honesty leftovers | Sibling leftovers. `redo_183=false` / `redo_190=false`. |
| #222 SPA PKCE remainder leftover | Sibling leftover. `redo_222=false`. |
| #223 S0 rotate/getenv remainder leftover | Sibling leftover. `redo_223=false`. |
| #225 G6 RecoveryPort remainder leftover | Sibling leftover. `redo_225=false`. |
| #221 G2 report/physical-dump leftover | Sibling leftover. `redo_221=false`. |
| #228 G10 leftover honesty remainder | Sibling leftover. `redo_228=false`. |
| #226 G3 Outbox consume-idempotent leftover | Sibling leftover. `redo_226=false`. |
| #229 G9 live multi-JVM replica remainder leftover | Sibling leftover. `redo_229=false`. |
| `DROP TABLE platform.audit_record` | Forbidden. `hot_table_dropped=false`. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |
