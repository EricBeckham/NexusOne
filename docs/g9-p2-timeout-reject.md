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
| Multi-node live partition | Timeout is proven in-process (latch + Zonky `pg_sleep`). |
| Redis-down ordinary fail-closed | Already recorded; not re-claimed. |
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

Recorded on this agent VM after rebase onto latest `main`. OpenJDK **21**, Maven, Spring Boot **4.1.1**. Zonky PostgreSQL **15.18**. No Docker.

```
./scripts/g9-p2-timeout-reject.sh
# (filled after the IT run)
```

| Suite | What it proves |
|---|---|
| `G9P2TimeoutRejectTest` | `decide` rejects `P2.PERM.0005` after the configured timeout when version load hangs; healthy `decide` matches durable `PermissionEngine` |
| `JdbcP2TimeoutRejectIT` | Zonky PG 15: healthy JDBC `decide` unchanged; `pg_sleep` on version load rejects near 200ms, not after the sleep |
| `SnapshotManagerTest` | Version hang / snapshot hang reject near the clock; `pollCached` swallows timeout |
| `G9PolicySnapshotFailoverTest` | Redis-down ordinary fail-closed unchanged |
| `PermissionEngineTest` | P0-02 truth table unchanged |
