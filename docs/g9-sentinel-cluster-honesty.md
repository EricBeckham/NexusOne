# G9 leftover: Sentinel / Cluster / replica promotion / live multi-JVM honesty

S0+S1 PostgreSQL slice. G18 stays closed. This does **not** rebuild `PolicySnapshot`, does **not** invent a second `TenantContext`, and does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. Version poll + invalidate-only stays on [g9-policy-snapshot.md](./g9-policy-snapshot.md). Redis-down GRANT / ordinary fail-closed stays on [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md). Bounded P2-down timeout-then-reject stays on [g9-p2-timeout-reject.md](./g9-p2-timeout-reject.md). Redis-down / lost-invalidate DENY after GRANT stays on [g9-failover-remainder.md](./g9-failover-remainder.md) (#121). In-process dual-reader / lost-invalidate version-poll stays on [g9-multinode-partition.md](./g9-multinode-partition.md) (#128).

This leftover **extends that #128 harness**. Writer + two readers still share one `InMemoryPolicyRepository` (or `iam.perm_policy_version` on Zonky PG 15) and the existing `SnapshotManager` / `PermissionModule.create` wiring. There is **no second snapshot store**.

It is an **honesty inventory**. Redis Sentinel, Redis Cluster, replica promotion, and a live multi-JVM network partition are **SKIP** on this agent VM unless the topology is actually present **and** a failover / promotion / second JVM was observed. Missing Redis, standalone Redis 3.2, or a dead client is **not** a product failover close.

## What this leftover covers

| Path | Behavior |
|---|---|
| Same #128 store | Two in-process readers share `InMemoryPolicyRepository`. Lost invalidate still fail-closes to `explicit_deny` == durable `PermissionEngine`. |
| Topology probe | `Redis32Client.INFO` / `tryCommand(SENTINEL\|CLUSTER)` against `127.0.0.1:6379`. Script also inventories `redis-cli` / `redis-sentinel` / `:26379` when present. |
| Honest SKIP | `sentinel_failover`, `cluster_failover`, `replica_promotion`, `live_multi_jvm` stay `SKIP` with a reason. |
| Honesty gate | `--verify-report` rejects `PASS` without topology + observed failover/promotion, and rejects `g9_close=CLOSED`. |
| Tenant context | Issued `one.nexus.p2.kernel.TenantContext` via `TenantContextHolder` only. Role is `g9_self_reader` + `DataScope.SELF`. |

No Docker. No iptables. No second JVM. `Redis32Client` stays standalone RESP2.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G9 product close | Explicitly `g9_close=NOT_CLAIMED`. |
| Redis Sentinel failover | No `redis-sentinel` / `:26379` on this VM. `SENTINEL MASTERS` is not a failover. |
| Redis Cluster failover | No `redis_mode=cluster`. Cluster slots / ASK / MOVED not implemented. |
| Replica promotion | No replica. `connected_slaves=0`. Client is not a Sentinel failover client. |
| Live multi-JVM / network partition | Same one-JVM dual-reader as #128. No second process, no netem. |
| Production bounded-stale window | Product poller default remains 2s (#89). |
| G2 / G10 / G3 leftovers | Orthogonal. Those rows stay on `docs/README.md`. |
| G18 reopen / `AuditPort` | Closed / untouched. |

## How to run

```bash
./scripts/g9-sentinel-cluster-honesty.sh
./scripts/g9-sentinel-cluster-honesty.sh --verify-report <out>/report.env
./scripts/g9-sentinel-cluster-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-sentinel-cluster-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-honesty/fixtures/fake-sentinel-pass.env
./scripts/g9-sentinel-cluster-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-honesty/fixtures/fake-g9-close.env
```

Equivalent tests (same #128 store + honesty gate):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9SentinelClusterHonestyTest,G9DualReaderPartitionTest,JdbcG9DualReaderPartitionIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #128 script is unchanged as a reproduction command:

```bash
./scripts/g9-multinode-partition.sh
```

Zonky in-process PostgreSQL **15.x**. Java 21 / Boot **4.1.1**. No Docker. No Sentinel / Cluster required.

See [engineering/g9-sentinel-cluster-honesty-EVIDENCE.md](./engineering/g9-sentinel-cluster-honesty-EVIDENCE.md).

Live multi-JVM / replica promotion honesty leftover (does not redo this Sentinel / Cluster inventory):

```bash
./scripts/g9-live-multi-jvm-replica.sh
```

See [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md). Sentinel / Cluster remainder PASS/SKIP leftover (does not redo this inventory): [g9-sentinel-cluster-remainder-honesty.md](./g9-sentinel-cluster-remainder-honesty.md).

## Honest leftovers (not claimed)

- Redis Sentinel quorum + automatic failover against a live 3.2 topology
- Redis Cluster slot failover / replica promotion
- Live multi-JVM network partition (iptables / netem / two JARs) — honesty leftover [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md)
- Product G9 close
- Redesigning `PolicySnapshot` / `SnapshotManager` / `PolicySnapshotPoller`
