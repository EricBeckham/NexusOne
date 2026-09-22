# G9 leftover: bounded P2-down timeout then reject

S0+S1 PostgreSQL slice. G18 stays closed. This does **not** rebuild `PolicySnapshot`, does **not** change `AuditPort`, and does **not** redo P0/M0. Redis-down ordinary fail-closed stays on [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md). Version poll + invalidate-only stays on [g9-policy-snapshot.md](./g9-policy-snapshot.md).

ARCH-013 / G9: **普通查询发现策略版本落后时重新加载；超过时限仍无法加载则拒绝。**

This package owns only that leftover clock: when `iam.perm_policy_version` or snapshot load cannot complete inside `nexus.permission.snapshot-load-timeout`, `PermissionService.decide` fail-closes `P2.PERM.0005`. The healthy path is unchanged.

## What this close covers

| Path | Behavior |
|---|---|
| Version / snapshot load hang | `SnapshotManager.get` runs both I/O calls under one deadline. Timeout → `SnapshotUnavailable` → `decide` `P2.PERM.0005` / 503 |
| Immediate durable miss | Unchanged: still `P2.PERM.0005` (already evidenced on #83) |
| Healthy `decide` | Same `PermissionEngine.evaluate` on the loaded snapshot |
| Background poll | `pollCached` still swallows timeout / load failure |

Config: `nexus.permission.snapshot-load-timeout` (product default **2s**, `NEXUS_PERM_SNAPSHOT_LOAD_TIMEOUT`). Zero / negative / null normalize to `SnapshotManager.DEFAULT_LOAD_TIMEOUT` so the clock stays on.

Wiring is the existing `SnapshotManager` + `PermissionModule.create`. No second engine. No `PolicySnapshot` rebuild.

## What this does not claim

| Leftover | Why |
|---|---|
| Redis Sentinel / Cluster / replica promotion | Explicitly not this slice. |
| Multi-node live partition | Timeout is proven in-process (latch + Zonky `pg_sleep`). Dual-reader / lost-invalidate version-poll is [g9-multinode-partition.md](./g9-multinode-partition.md). Live multi-JVM / Sentinel / Cluster honesty leftover is [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md). |
| Redis-down ordinary fail-closed | Already recorded; not re-claimed. Fail-closed DENY after lost invalidate is [g9-failover-remainder.md](./g9-failover-remainder.md). |
| G2 product-wide same engine | Unchanged from #73. |
| G18 browser walk | Closed; not walked again. |

## How to run

```bash
./scripts/g9-p2-timeout-reject.sh
```

Equivalent:

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9P2TimeoutRejectTest,JdbcP2TimeoutRejectIT,SnapshotManagerTest,G9PolicySnapshotFailoverTest,PermissionEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Zonky in-process PostgreSQL **15.x**. No Docker. No Sentinel / Cluster.

## Evidence

Recorded **2026-09-22T17:09:12Z** on this agent VM after rebase onto `main@d7abf34`. OpenJDK **21.0.10**, Maven **3.8.7**, Spring Boot **4.1.1**. Zonky PostgreSQL **15.18**. No Docker. No Sentinel / Cluster.

```
./scripts/g9-p2-timeout-reject.sh
# Tests run: 23, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS  2026-09-22T17:09:12Z

mvn -pl nexus-permission-core -am test
# Tests run: 66, Failures: 0, Errors: 0, Skipped: 5  BUILD SUCCESS  2026-09-22T17:09:55Z
```

Skipped 5 are unchanged live-listener cases (Redis `:6379` / `127.0.0.1:5432`). JDBC timeout proof is Zonky **15.18**.

| Suite | Count | What it proves |
|---|---|---|
| `G9P2TimeoutRejectTest` | **2/2** | `decide` rejects `P2.PERM.0005` after the configured timeout when version load hangs; healthy `decide` matches durable `PermissionEngine` |
| `JdbcP2TimeoutRejectIT` | **1/1** | Zonky PG **15.18**: healthy JDBC `decide` unchanged; `pg_sleep(3)` on version load rejects near 200ms, not after the sleep |
| `SnapshotManagerTest` | **8/8** | Version hang / snapshot hang reject near the clock; `pollCached` swallows timeout; existing poll / fail-closed cases unchanged |
| `G9PolicySnapshotFailoverTest` | **5/5** | Redis-down ordinary fail-closed unchanged |
| `PermissionEngineTest` | **7/7** | P0-02 truth table unchanged |
