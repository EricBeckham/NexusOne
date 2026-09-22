# G9 leftover — Sentinel / Cluster remainder honesty beyond #148/#161

S0+S1 remainder after the #148 Sentinel / Cluster honesty leftover ([g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md)) and the #161 live multi-JVM / replica leftover ([g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md)). Rebased onto `origin/main` `eff62d8` (Merge #214 tip-sync after #188 G2 ops pg_dump leftover honesty / #211 tip-sync / #205 G17 MFA recovery/OTP leftover honesty / #197 G9 Redis-down invalidate remainder leftover honesty / #204 G2 H5/form.write leftover honesty / #206 tip-sync / #203 G4 export-wizard deny leftover honesty / #202 G10 file-reconcile / identity-drift leftover honesty / #196 G5 mid-publish / browser honesty / #195 G3 consume-crash honesty). **G18 stays closed.** This leftover does **not** redo #83 Redis-down GRANT. It does **not** redo #100 P2 timeout-then-reject. It does **not** redo #121 failover remainder. It does **not** redo #128 dual-reader harness. It does **not** redo #148 Sentinel / Cluster inventory. It does **not** redo #161 live multi-JVM leftover. It does **not** redo #174 Redis-down / invalidate honesty. It does **not** rebuild `PolicySnapshot`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim a product G9 close. `g9_close=NOT_CLAIMED`. P0/M0 are **not** redone.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md): [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md) (#148), [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) (#161), [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md) (#83), [g9-failover-remainder.md](./g9-failover-remainder.md) (#121), [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) (#174), [g9-multinode-partition.md](./g9-multinode-partition.md) (#128), [g3-outbox-consume-crash-honesty.md](./g3-outbox-consume-crash-honesty.md) (#195), [g3-claim-crash-window.md](./g3-claim-crash-window.md) (#170), [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md) (#196), [g10-leftover-honesty.md](./g10-leftover-honesty.md) (#202), [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) (#169), [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179), [g4-export-wizard-deny-honesty.md](./g4-export-wizard-deny-honesty.md) (#203), [g2-h5-every-page-form-write-honesty.md](./g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](./g9-redis-down-invalidate-remainder.md) (#197), [g17-mfa-recovery-vue-otp-honesty.md](./g17-mfa-recovery-vue-otp-honesty.md) (#205), and [g2-ops-pg-dump-honesty.md](./g2-ops-pg-dump-honesty.md) (#188). This file is the PASS/SKIP honesty leftover beside them.

#148 already inventoried Sentinel / Cluster / replica / live multi-JVM as honest SKIP. #161 already inventoried live multi-JVM / replica promotion as honest SKIP. The leftover here is the **honesty gate** for the remaining Sentinel / Cluster observation path: the standalone Redis 3.2 INFO / SENTINEL|CLUSTER inventory stub may **PASS**. Live Sentinel failover and live Cluster slot failover are **PASS** only when the topology is present **and** a failover was observed. Missing `redis-sentinel` / Cluster / `:26379` is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| Inventory stub | `Redis32Client.infoField` parses standalone `redis_mode` / `role` / `connected_slaves`. SENTINEL/CLUSTER commands stay absent on the stub. `inventory_harness=PASS`. Not a live failover. |
| Live Sentinel | `sentinel_failover=SKIP` unless `sentinel_present` **and** `sentinel_failover_observed`. `SENTINEL MASTERS` is not a failover. |
| Live Cluster | `cluster_failover=SKIP` unless `cluster_enabled` **and** `cluster_failover_observed`. Cluster slots / ASK / MOVED are not implemented. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g9_close=CLOSED`, rejects #83/#100/#121/#128/#148/#161/#174 redo flags, and requires sibling leftover rows kept. |

No Docker. No iptables. No second JVM. `Redis32Client` stays standalone RESP2. #148 / #161 / #174 scripts stay the sibling reproduction commands.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G9 close | Explicitly `g9_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| #83 / #100 / #121 / #128 / #148 / #161 / #174 redo | Sibling leftovers on `main`. `g9_83_100_121_128_148_161_174_redone=false`. |
| Redis Sentinel quorum + automatic failover | No `redis-sentinel` / `:26379` on this VM, or present without an observed failover. |
| Redis Cluster slot failover | No `redis_mode=cluster`. Cluster slots / ASK / MOVED not implemented. |
| Replica promotion / live multi-JVM | Stay on #161. |
| Live Redis process-down / live invalidate | Stay on #174. Dead client `:1` is not live-down. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## How to run

```bash
./scripts/g9-sentinel-cluster-remainder-honesty.sh
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-remainder-honesty/fixtures/fake-sentinel-pass.env
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-remainder-honesty/fixtures/fake-cluster-pass.env
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-remainder-honesty/fixtures/fake-g9-close.env
```

Always-green Java (does **not** rerun #128 / #148 / #161 / #174 suites):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9SentinelClusterRemainderHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The sibling leftovers stay siblings:

```bash
./scripts/g9-sentinel-cluster-honesty.sh
./scripts/g9-live-multi-jvm-replica.sh
./scripts/g9-redis-down-invalidate-honesty.sh
./scripts/g9-failover-remainder.sh
```

Do **not** treat those scripts as this leftover. Java 21 / Boot **4.1.1**. No Docker. Live Sentinel / Cluster are **not** required.

See [engineering/g9-sentinel-cluster-remainder-honesty-EVIDENCE.md](./engineering/g9-sentinel-cluster-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g9-sentinel-cluster-remainder-honesty/`.

## Honest leftovers (not claimed)

- Redis Sentinel quorum + automatic failover against a live 3.2 topology
- Redis Cluster slot failover / replica promotion
- Live multi-JVM network partition (iptables / netem / two JARs)
- Product G9 close
- Redo of #83 / #100 / #121 / #128 / #148 / #161 / #174
