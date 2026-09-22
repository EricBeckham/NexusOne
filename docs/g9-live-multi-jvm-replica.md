# G9 leftover: live multi-JVM / replica promotion honesty

S0+S1 PostgreSQL slice after #148. G18 stays closed. This does **not** rebuild `PolicySnapshot`, does **not** invent a second `TenantContext`, and does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo #89 / #83 / #100 / #121 / #128 / #148.

Already on `main` (do not redo):

- #89 version poll + invalidate-only: [g9-policy-snapshot.md](./g9-policy-snapshot.md)
- #83 Redis-down GRANT / ordinary fail-closed: [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md)
- #100 bounded P2-down timeout-then-reject: [g9-p2-timeout-reject.md](./g9-p2-timeout-reject.md)
- #121 Redis-down / lost-invalidate DENY: [g9-failover-remainder.md](./g9-failover-remainder.md)
- #128 in-process dual-reader / lost-invalidate version-poll: [g9-multinode-partition.md](./g9-multinode-partition.md)
- #148 Sentinel / Cluster honesty inventory: [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md)

This leftover is a **narrower honesty inventory**. Live multi-JVM and replica promotion are **SKIP** unless the topology is actually present **and** a second JVM partition / replica promotion was observed. A surefire parent/fork, standalone Redis 3.2, or a missing listener is **not** a product failover close. Sentinel / Cluster product close stays `NOT_CLAIMED`.

Keep both leftover rows in [docs/README.md](./README.md): [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md) (#162) and this file. Keep [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md) (#148), [g7-withdraw-browser.md](./g7-withdraw-browser.md) (#159), [s0-secret-rotate-without-restart.md](./s0-secret-rotate-without-restart.md) (#143), and [g2-ops-pg-dump.md](./g2-ops-pg-dump.md) (#155).

## What this leftover covers

| Path | Behavior |
|---|---|
| Replica probe | `Redis32Client.INFO replication` (role / `connected_slaves`). `replica_present` is true only for `role=slave` or `connected_slaves>0`. |
| Honest replica SKIP | `replica_promotion=SKIP` unless `replica_present` **and** `promotion_observed`. Presence without an observed promotion stays SKIP. |
| Live JVM probe | Optional `NEXUS_G9_SECOND_JVM_PID`. Surefire parent/fork is **not** a second application JVM. |
| Honest multi-JVM SKIP | `live_multi_jvm=SKIP` unless a distinct live PID **and** `NEXUS_G9_MULTI_JVM_OBSERVED=true` (partition / netem / two JARs). |
| Honesty gate | `--verify-report` rejects fake PASS, rejects `g9_close=CLOSED`, rejects `sentinel_cluster_close=CLOSED`. |
| #148 Sentinel / Cluster | Not re-inventoried. `sentinel_cluster_close=NOT_CLAIMED`. |

No Docker. No iptables / netem. No second JVM start. `Redis32Client` stays standalone RESP2.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G9 product close | Explicitly `g9_close=NOT_CLAIMED`. |
| Redis Sentinel / Cluster product close | Explicitly `sentinel_cluster_close=NOT_CLAIMED`. Inventory stays on #148. |
| Replica promotion | No replica, or replica present without an observed promotion. |
| Live multi-JVM / network partition | One application JVM. In-process dual-reader stays on #128. |
| Production bounded-stale window | Product poller default remains 2s (#89). |
| S0 rotate / G2 ops `pg_dump` | Sibling leftovers on `main` (#143 / #155). Not rewritten. |
| G18 reopen / `AuditPort` | Closed / untouched. |

## How to run

```bash
./scripts/g9-live-multi-jvm-replica.sh
./scripts/g9-live-multi-jvm-replica.sh --verify-report <out>/report.env
./scripts/g9-live-multi-jvm-replica.sh --verify-report \
  scripts/g9-live-multi-jvm-replica/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-live-multi-jvm-replica.sh --verify-report \
  scripts/g9-live-multi-jvm-replica/fixtures/fake-replica-pass.env
./scripts/g9-live-multi-jvm-replica.sh --verify-report \
  scripts/g9-live-multi-jvm-replica/fixtures/fake-multi-jvm-pass.env
./scripts/g9-live-multi-jvm-replica.sh --verify-report \
  scripts/g9-live-multi-jvm-replica/fixtures/fake-sentinel-cluster-close.env
```

Always-green Java (does not redo #128 / #148 suites):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9LiveMultiJvmReplicaHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Zonky / live Redis / second JVM are **not** required. Java 21 / Boot **4.1.1**. No Docker.

See [engineering/g9-live-multi-jvm-replica-EVIDENCE.md](./engineering/g9-live-multi-jvm-replica-EVIDENCE.md).

## Honest leftovers (not claimed)

- Redis replica promotion on a host that actually has a replica **and** observed failover
- Live multi-JVM network partition (iptables / netem / two JARs)
- Redis Sentinel quorum + automatic failover / Redis Cluster slot failover (#148 inventory)
- Product G9 close
- Redesigning `PolicySnapshot` / `SnapshotManager` / `PolicySnapshotPoller`
