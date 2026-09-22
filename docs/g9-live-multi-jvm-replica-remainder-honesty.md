# G9 leftover — live multi-JVM / replica promotion remainder honesty beyond #209/#161/#148

S0+S1 remainder after the #148 Sentinel / Cluster honesty leftover ([g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md)), the #161 live multi-JVM / replica leftover ([g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md)), and the #209 Sentinel / Cluster remainder leftover ([g9-sentinel-cluster-remainder-honesty.md](./g9-sentinel-cluster-remainder-honesty.md)). Rebased onto `origin/main` `323b22d` (Merge #226 G3 Outbox consume-idempotent leftover honesty after #228 G10 leftover honesty remainder / #221 G2 report/physical-dump leftover honesty / #225 G6 RecoveryPort dry-run remainder leftover honesty / #224 S0+S1 claim-list tip-sync / #223 S0 rotate/getenv remainder leftover honesty / #222 G4 SPA verifier remainder leftover honesty / #216 G17 SoD leftover honesty / #218 S0+S1 claim-list tip-sync / #220 G15 wx-dt/native honesty remainder / #215 G1 BoundSql/COUNT/export leftover honesty / #210 G17 Tongsuo/Nginx GMTLS handshake leftover honesty / #217 G5 PublishHook/DDL leftover honesty / #209 G9 Sentinel/Cluster remainder leftover honesty). **G18 stays closed.** This leftover does **not** redo #83 Redis-down GRANT. It does **not** redo #100 P2 timeout-then-reject. It does **not** redo #121 failover remainder. It does **not** redo #128 dual-reader harness. It does **not** redo #148 Sentinel / Cluster inventory. It does **not** redo #161 live multi-JVM leftover. It does **not** redo #174 Redis-down / invalidate honesty. It does **not** redo #209 Sentinel / Cluster remainder leftover. It does **not** rebuild `PolicySnapshot`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim a product G9 close. It does **not** claim Sentinel / Cluster product close. `g9_close=NOT_CLAIMED`. `sentinel_cluster_close=NOT_CLAIMED`. P0/M0 are **not** redone.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md): [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md) (#148), [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) (#161), [g9-sentinel-cluster-remainder-honesty.md](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md) (#83), [g9-failover-remainder.md](./g9-failover-remainder.md) (#121), [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) (#174), [g9-redis-down-invalidate-remainder.md](./g9-redis-down-invalidate-remainder.md) (#197), [g9-multinode-partition.md](./g9-multinode-partition.md) (#128), [g6-recovery-port-dry-run-remainder-honesty.md](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [g2-report-physical-dump-honesty.md](./g2-report-physical-dump-honesty.md) (#221), [g10-leftover-honesty-remainder.md](./g10-leftover-honesty-remainder.md) (#228), [g3-outbox-consume-idempotent-honesty.md](./g3-outbox-consume-idempotent-honesty.md) (#226), [s0-secret-rotate-getenv-remainder-honesty.md](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md) (#217), [g2-ops-pg-dump-honesty.md](./g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty.md](./g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty.md](./g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](./g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](./g3-outbox-consume-crash-honesty.md) (#195), and [g3-claim-crash-window.md](./g3-claim-crash-window.md) (#170). This file is the PASS/SKIP honesty leftover beside them.

#148 already inventoried Sentinel / Cluster / replica / live multi-JVM as honest SKIP. #161 already inventoried live multi-JVM / replica promotion as honest SKIP. #209 already gated the remaining Sentinel / Cluster observation path (inventory stub PASS; live Sentinel/Cluster SKIP). The leftover here is the **honesty gate** for the remaining live multi-JVM / replica promotion observation path: the standalone Redis 3.2 INFO replication inventory stub may **PASS**. Live replica promotion and live multi-JVM are **PASS** only when the topology is present **and** a promotion / second-JVM partition was observed. No second JVM / no replica is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| Inventory stub | `Redis32Client.infoField` parses standalone `role` / `connected_slaves`. `role=master` `connected_slaves=0` is **not** a replica. Surefire parent/fork is **not** a second application JVM. `inventory_harness=PASS`. Not a live promotion. |
| Live replica | `replica_promotion=SKIP` unless `replica_present` **and** `promotion_observed`. Presence without an observed promotion stays SKIP. |
| Live multi-JVM | `live_multi_jvm=SKIP` unless `second_jvm_present` **and** `multi_jvm_observed`. Optional `NEXUS_G9_SECOND_JVM_PID` without a partition / netem / two-JAR walk stays SKIP. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g9_close=CLOSED`, rejects `sentinel_cluster_close=CLOSED`, rejects #83/#100/#121/#128/#148/#161/#174/#209 redo flags, and requires sibling leftover rows kept. |

No Docker. No iptables / netem. No second JVM start. `Redis32Client` stays standalone RESP2. #148 / #161 / #209 scripts stay the sibling reproduction commands.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G9 close | Explicitly `g9_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Redis Sentinel / Cluster product close | Explicitly `sentinel_cluster_close=NOT_CLAIMED`. Inventory stays on #148 / #209. |
| #83 / #100 / #121 / #128 / #148 / #161 / #174 / #209 redo | Sibling leftovers on `main`. `g9_83_100_121_128_148_161_174_209_redone=false`. |
| Replica promotion | No replica, or replica present without an observed promotion. |
| Live multi-JVM / network partition | One application JVM. In-process dual-reader stays on #128. Surefire parent/fork is not a second JVM. |
| Live Redis process-down / live invalidate | Stay on #174 / #197. Dead client `:1` is not live-down. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## How to run

```bash
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/fake-replica-pass.env
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/fake-multi-jvm-pass.env
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/fake-g9-close.env
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/fake-sentinel-cluster-close.env
```

Always-green Java (does **not** rerun #128 / #148 / #161 / #174 / #209 suites):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9LiveMultiJvmReplicaRemainderHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The sibling leftovers stay siblings:

```bash
# do not run as this leftover
# ./scripts/g9-live-multi-jvm-replica.sh
# ./scripts/g9-sentinel-cluster-remainder-honesty.sh
# ./scripts/g9-sentinel-cluster-honesty.sh
```

Do **not** treat those scripts as this leftover. Java 21 / Boot **4.1.1**. No Docker. Live replica / second JVM are **not** required.

See [engineering/g9-live-multi-jvm-replica-remainder-honesty-EVIDENCE.md](./engineering/g9-live-multi-jvm-replica-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g9-live-multi-jvm-replica-remainder-honesty/`.

## Honest leftovers (not claimed)

- Redis replica promotion on a host that actually has a replica **and** observed failover
- Live multi-JVM network partition (iptables / netem / two JARs)
- Redis Sentinel quorum + automatic failover / Redis Cluster slot failover (#148 / #209 inventory)
- Product G9 close
- Redo of #83 / #100 / #121 / #128 / #148 / #161 / #174 / #209
