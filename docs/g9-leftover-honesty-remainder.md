# G9 leftover — Sentinel / multi-JVM / Redis-down invalidate leftover honesty remainder beyond #237/#229/#209/#197

S0+S1 remainder **after** the #237 Redis-down / invalidate remainder honesty leftover ([g9-redis-down-invalidate-remainder-honesty.md](./g9-redis-down-invalidate-remainder-honesty.md)), the #229 live multi-JVM / replica remainder leftover ([g9-live-multi-jvm-replica-remainder-honesty.md](./g9-live-multi-jvm-replica-remainder-honesty.md)), the #209 Sentinel / Cluster remainder leftover ([g9-sentinel-cluster-remainder-honesty.md](./g9-sentinel-cluster-remainder-honesty.md)), and the #197 Redis-down / invalidate remainder leftover ([g9-redis-down-invalidate-remainder.md](./g9-redis-down-invalidate-remainder.md)). Rebased onto `origin/main` `b05cdf7` (Merge #249 G17 SoD/admin-force leftover honesty after #240 G15 H5/PKCE leftover honesty / #254 G2 H5/form.write/report leftover honesty / #236 Linux/ARM profile leftover honesty / #251 G4 hot-purge/staging/PKCE leftover honesty / #243 G10 file/object reconcile leftover honesty / #241 SecretPort rotate/getenv leftover honesty / #235 G5 PublishHook/DDL leftover honesty). **G18 stays closed.** This leftover does **not** redo #237. It does **not** redo #229. It does **not** redo #209. It does **not** redo #197. It does **not** redo #83 / #100 / #121 / #128 / #148 / #161 / #174 product code. It does **not** rebuild `PolicySnapshot`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0/M0. It does **not** claim a product G9 close. `g9_close=NOT_CLAIMED`. `sentinel_cluster_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md) (#197), [g17-separation-leftover-honesty-remainder](./g17-separation-leftover-honesty-remainder.md) (#249), [g15-h5-pkce-miniprogram-remainder-honesty](./g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty](./g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty](./linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty](./g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty](./g10-file-object-reconcile-remainder-honesty.md) (#243), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g5-publishhook-ddl-remainder-honesty](./g5-publishhook-ddl-remainder-honesty.md) (#235), [g17-mfa-recovery-vue-otp-remainder-honesty](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md) (#195), [g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md) (#175), [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md) (#169), [g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md) (#174), [g9-policysnapshot-invalidate](./g9-policysnapshot-invalidate.md) (#83), and [g9-failover-remainder](./g9-failover-remainder.md) (#121). Keep this file.

#237 already locks parent #197/#174/#83/#121 leftover cells as always-green **PASS** and live Redis-down / live invalidate as honest **SKIP**. #229 already locks inventory harness as always-green **PASS** and replica promotion / live multi-JVM as honest **SKIP**. #209 already locks inventory harness as always-green **PASS** and Sentinel / Cluster failover as honest **SKIP**. #197 already proves dead-client harness **PASS** and live Redis-down / live invalidate **SKIP**. The leftover here is the **honesty remainder beyond those four**: parent leftover harness fixtures stay always-green **PASS**. Live Sentinel / Cluster failover, replica promotion, multi-JVM partition, Redis process-down, and live invalidate are **PASS** only when present **and** observed. Missing topology / unobserved walk is honest **SKIP**. It never fabricates `PASS`. A listed observation env flag is not observation.

## What this leftover covers

| Surface | Behavior |
|---|---|
| #237 leftover harness | Parent `--verify-report` fixtures stay **PASS**. `leftover_237_harness=PASS`. `leftover_237_cells_honest=PASS`. Does **not** redo #237. |
| #229 leftover harness | Parent `--verify-report` fixtures stay **PASS**. `leftover_229_harness=PASS`. `leftover_229_cells_honest=PASS`. Does **not** redo #229. |
| #209 leftover harness | Parent `--verify-report` fixtures stay **PASS**. `leftover_209_harness=PASS`. `leftover_209_cells_honest=PASS`. Does **not** redo #209. |
| #197 leftover harness | Parent `--verify-report` fixtures stay **PASS**. `leftover_197_harness=PASS`. `leftover_197_cells_honest=PASS`. Dead client `:1` is **not** live Redis-down. Does **not** redo #197. |
| Live Redis-down | `live_redis_down=PASS` only with `redis_present=true` **and** `redis_down_observed=true`. Otherwise honest **SKIP**. |
| Live invalidate | `live_invalidate=PASS` only with `redis_present=true` **and** `live_invalidate_observed=true`. Otherwise honest **SKIP**. |
| Sentinel failover | `sentinel_failover=PASS` only with `sentinel_present=true` **and** `sentinel_failover_observed=true`. Otherwise honest **SKIP**. |
| Cluster failover | `cluster_failover=PASS` only with `cluster_enabled=true` **and** `cluster_failover_observed=true`. Otherwise honest **SKIP**. |
| Replica promotion | `replica_promotion=PASS` only with `replica_present=true` **and** `promotion_observed=true`. Otherwise honest **SKIP**. |
| Live multi-JVM | `live_multi_jvm=PASS` only with `second_jvm_present=true` **and** `multi_jvm_observed=true`. Surefire parent/fork is not a live partition. Otherwise honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g9_close=CLOSED` / `sentinel_cluster_close=CLOSED`, rejects #237/#229/#209/#197 redo flags, and requires leftover rows kept. |
| Leftover rows | `docs/README.md` keeps #237 / #229 / #209 / #197 plus this remainder and **ALL** sibling leftover rows, including #249 `g17-separation-leftover-honesty-remainder`, #240 `g15-h5-pkce-miniprogram-remainder-honesty`, #254 `g2-h5-every-page-form-write-report-remainder-honesty`, #236 `linux-arm-profile-remainder-honesty`, #251 `g4-hot-purge-staging-pkce-remainder-honesty`, #243 `g10-file-object-reconcile-remainder-honesty`, #241 `s0-secret-rotate-getenv-leftover-honesty`, #235 `g5-publishhook-ddl-remainder-honesty`, #233 `g17-mfa-recovery-vue-otp-remainder-honesty`. |

No Docker. No iptables. No Redis start/stop. `Redis32Client` stays standalone RESP2. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence. Do **not** run `./scripts/g9-redis-down-invalidate-remainder-honesty.sh`, `./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh`, `./scripts/g9-sentinel-cluster-remainder-honesty.sh`, or `./scripts/g9-redis-down-invalidate-remainder.sh` as this leftover (parent `--verify-report` on honest fixtures is allowed).

## How to run (no Docker)

```bash
# Always-green parent harness + honest live SKIP
./scripts/g9-leftover-honesty-remainder.sh

# Honesty gate only
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-live-redis-down-pass.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-live-invalidate-pass.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-sentinel-pass.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-multi-jvm-pass.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-g9-close.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-redone.env
```

Equivalent always-green Java (does **not** redo #237 / #229 / #209 / #197 suites):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9LeftoverHonestyRemainderTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The existing leftover scripts stay as reproduction commands:

```bash
# do not run for this leftover
# ./scripts/g9-redis-down-invalidate-remainder-honesty.sh
# ./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh
# ./scripts/g9-sentinel-cluster-remainder-honesty.sh
# ./scripts/g9-redis-down-invalidate-remainder.sh
```

See [engineering/g9-leftover-honesty-remainder-EVIDENCE.md](./engineering/g9-leftover-honesty-remainder-EVIDENCE.md). Live `report.env` is gitignored under `.local/g9-leftover-honesty-remainder/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G9 close | Explicitly `g9_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Sentinel / Cluster product close | Explicitly `sentinel_cluster_close=NOT_CLAIMED`. |
| Live Redis process-down | Dead-client harness PASS is not a live crash. No Redis was stopped on this VM. |
| Live PolicySnapshot invalidate | No observed PUBLISH/SUBSCRIBE on `:6379`. |
| Live Sentinel / Cluster failover | Inventory / SENTINEL MASTERS is not a failover. |
| Live replica promotion / multi-JVM | Surefire parent/fork is not a live partition. |
| #237 / #229 / #209 / #197 redo | Sibling leftovers. `redo_237=false`. `redo_229=false`. `redo_209=false`. `redo_197=false`. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## Honest leftovers (not claimed)

- Live Redis 3.2 process-down mid-flight on a host that actually has Redis
- Live PolicySnapshot PUBLISH/SUBSCRIBE invalidate walk
- Redis Sentinel / Cluster failover
- Replica promotion / live multi-JVM network partition
- Product G9 close
- Redo of #83 / #100 / #121 / #128 / #148 / #161 / #174 / #197 / #209 / #229 / #237
