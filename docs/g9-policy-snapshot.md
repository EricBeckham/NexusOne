# G9 remainder: PolicySnapshot version poll + Redis invalidate-only

S0+S1 PostgreSQL slice. G2 role matrix is already on `main` (#73). G18 stays closed. This does **not** treat `tenant_member` as permission-complete and does **not** claim full G9 failover.

Open sibling [#83](https://github.com/EricBeckham/NexusOne/pull/83) records Redis-down / `decide == durable PermissionEngine` failover evidence in `docs/g9-policysnapshot-invalidate.md`. This PR owns version poll, `PolicySnapshotPoller`, and the `PermissionModule` listener. Indexes list both.

## What this close covers

ARCH-011 / G9 ask for two things that were only half-wired on `main`:

1. **Version polling.** `iam.perm_policy_version` is the clock. Every `SnapshotManager.get` re-reads it. `PolicySnapshotPoller` additionally polls cached tenants on `nexus.permission.snapshot-poll-interval` (product default 2s; test default 0s).
2. **Invalidate-only Redis.** Policy writes still `PUBLISH nexus:v1:permission:invalidate`. Each instance now `addListener`s that channel onto its own `SnapshotManager.notifyInvalidated`. Redis never stores a snapshot body.

Channel loss is covered by (1): a reader that never received PUBLISH still sees the new grant on the next `get` or poll tick. Redis down is covered the same way — publish failure is logged, not treated as success, and is not a truth source.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G9 failover | No Sentinel / Cluster / replica promotion. Bounded P2-down timeout-then-reject is [g9-p2-timeout-reject.md](./g9-p2-timeout-reject.md). Ordinary query fail-closed on version/load failure remains. Fail-closed DENY after Redis-down / lost invalidate is [g9-failover-remainder.md](./g9-failover-remainder.md). In-process dual-reader / lost-invalidate version-poll is [g9-multinode-partition.md](./g9-multinode-partition.md). |
| G2 product-wide same engine | Report / export / H5 / form-definition list / simulation admin stay on #73 and later work. |
| `tenant_member` | Demo ALL-scope seed for the closed G18 leave walk. Propagation evidence uses role `g9_self_reader` + `DataScope.SELF`. |
| Live Redis 3.2 on this VM | RESP2 client + tests are present; they skip when `127.0.0.1:6379` is down. |

## Wiring

| Piece | Behavior |
|---|---|
| `PermissionModule.create` | `invalidation.addListener(snapshots::notifyInvalidated)` |
| `PolicyAdminService.bump` | `repository.bumpVersion` then `notifyTenant` (local listeners + PUBLISH) |
| `RedisPolicyInvalidation` | Local deliver, PUBLISH, `SET EX` version hint. `startListening()` SUBSCRIBE loop. |
| `PolicySnapshotPoller` | `pollCached()`; request-path `get` still fail-closes |

## Evidence

Agent VM **2026-09-22T15:42:29Z**. OpenJDK **21.0.10**, Maven **3.8.7**, Spring Boot **4.1.1**. Zonky PostgreSQL **15.18**. No Docker. No live Redis / `127.0.0.1:5432`.

```
mvn -pl nexus-permission-core -am test
# BUILD SUCCESS  2026-09-22T15:46:14Z after rebase onto main@26d1f94
# Tests run: 42, Failures: 0, Errors: 0, Skipped: 5
```

| Suite | Count |
|---|---|
| `G9PolicySnapshotPropagationTest` | 4/4 |
| `G9PolicySnapshotApiTest` | 1/1 |
| `JdbcPolicySnapshotPollIT` | 1/1 (PG 15.18, Flyway V200+V300) |
| `SnapshotManagerTest` | 5/5 |
| `RedisInvalidationTest` | 1/1 (+3 skip, no Redis listener) |

Details: `nexus-permission-core/EVIDENCE.md`.
