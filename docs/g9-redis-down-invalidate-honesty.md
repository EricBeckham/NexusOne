# G9 leftover: Redis-down / PolicySnapshot invalidate honesty

S0+S1 PostgreSQL slice after #121 / #148. G18 stays closed. This does **not** rebuild `PolicySnapshot`, does **not** invent a second `TenantContext`, and does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo #100 / #121 / #128 / #148 / #161.

Already on `main` (do not redo):

- #83 Redis-down GRANT / ordinary fail-closed: [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md)
- #100 bounded P2-down timeout-then-reject: [g9-p2-timeout-reject.md](./g9-p2-timeout-reject.md)
- #121 Redis-down / lost-invalidate DENY: [g9-failover-remainder.md](./g9-failover-remainder.md)
- #128 in-process dual-reader / lost-invalidate version-poll: [g9-multinode-partition.md](./g9-multinode-partition.md)
- #148 Sentinel / Cluster honesty inventory: [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md)
- #161 live multi-JVM / replica promotion honesty: [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md)

This leftover is a **narrower honesty inventory**. Live Redis 3.2 process-down and live PolicySnapshot PUBLISH/SUBSCRIBE are **SKIP** unless Redis is actually listening **and** a process-down / live invalidate walk was observed. A dead client on port `1` (#83 / #121) is **not** a live Redis process-down. `RedisInvalidationTest` skipping PING/SUBSCRIBE is **not** a live invalidate PASS. Product G9 close stays `NOT_CLAIMED`.

Keep **both** leftover rows in [docs/README.md](./README.md): [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md) (#83) and [g9-failover-remainder.md](./g9-failover-remainder.md) (#121). Keep this file. Keep [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md) (#148), [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) (#161), and [g3-claim-crash-window.md](./g3-claim-crash-window.md) (#170).

## What this leftover covers

| Path | Behavior |
|---|---|
| Dead-client simulation | #83 / #121 use `Redis32Client("127.0.0.1", 1)`. That is **not** a live Redis process-down. `dead_client_is_not_live_down=true`. |
| Live Redis probe | `Redis32Client.PING` / `INFO replication` against `127.0.0.1:6379`. Missing listener is honest SKIP. |
| Honest Redis-down SKIP | `live_redis_down=SKIP` unless `redis_present` **and** `redis_down_observed`. Presence without an observed process-down stays SKIP. |
| Honest live-invalidate SKIP | `live_invalidate=SKIP` unless `redis_present` **and** `live_invalidate_observed`. A surefire skip is not a live PUBLISH/SUBSCRIBE walk. |
| Honesty gate | `--verify-report` rejects fake PASS, rejects `g9_close=CLOSED`, rejects a #100/#121/#128/#148/#161 redo flag. |

No Docker. No iptables. No Redis start/stop. `Redis32Client` stays standalone RESP2.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G9 product close | Explicitly `g9_close=NOT_CLAIMED`. |
| Live Redis process-down | Dead client on port `1` is not a live crash. No Redis was stopped on this VM. |
| Live PolicySnapshot invalidate | No observed PUBLISH/SUBSCRIBE on `:6379`. |
| Redis Sentinel / Cluster / replica promotion | Inventory stays on #148 / #161. |
| In-process dual-reader / lost-invalidate | Stays on #128. |
| P2 timeout-then-reject | Stays on #100. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## How to run

```bash
./scripts/g9-redis-down-invalidate-honesty.sh
./scripts/g9-redis-down-invalidate-honesty.sh --verify-report <out>/report.env
./scripts/g9-redis-down-invalidate-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-redis-down-invalidate-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-honesty/fixtures/fake-live-redis-down-pass.env
./scripts/g9-redis-down-invalidate-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-honesty/fixtures/fake-live-invalidate-pass.env
./scripts/g9-redis-down-invalidate-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-honesty/fixtures/fake-g9-close.env
```

Always-green Java (does not redo #100 / #121 / #128 / #148 / #161 suites):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9RedisDownInvalidateHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The existing leftover scripts stay as reproduction commands:

```bash
./scripts/g9-policysnapshot-invalidate.sh
./scripts/g9-failover-remainder.sh
```

Zonky / live Redis are **not** required. Java 21 / Boot **4.1.1**. No Docker.

See [engineering/g9-redis-down-invalidate-honesty-EVIDENCE.md](./engineering/g9-redis-down-invalidate-honesty-EVIDENCE.md).

## Honest leftovers (not claimed)

- Live Redis 3.2 process-down mid-flight (not a dead client on port `1`)
- Live PolicySnapshot PUBLISH/SUBSCRIBE on a host that actually has Redis 3.2
- Redis Sentinel / Cluster failover / replica promotion / live multi-JVM (#148 / #161)
- Product G9 close
- Redesigning `PolicySnapshot` / `SnapshotManager` / `PolicySnapshotPoller`
