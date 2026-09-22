# G9 remainder: PolicySnapshot version poll + Redis invalidate-only

S0+S1 PostgreSQL slice. Orthogonal to G2 PR #73 (role matrix / same-engine surfaces). G18 stays closed. This does **not** treat `tenant_member` as permission-complete and does **not** claim full G9 failover.

## What this close covers

ARCH-011 / G9 ask for two things that were only half-wired on `main`:

1. **Version polling.** `iam.perm_policy_version` is the clock. Every `SnapshotManager.get` re-reads it. `PolicySnapshotPoller` additionally polls cached tenants on `nexus.permission.snapshot-poll-interval` (product default 2s; test default 0s).
2. **Invalidate-only Redis.** Policy writes still `PUBLISH nexus:v1:permission:invalidate`. Each instance now `addListener`s that channel onto its own `SnapshotManager.notifyInvalidated`. Redis never stores a snapshot body.

Channel loss is covered by (1): a reader that never received PUBLISH still sees the new grant on the next `get` or poll tick. Redis down is covered the same way — publish failure is logged, not treated as success, and is not a truth source.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G9 failover | No Sentinel / Cluster / replica promotion, no bounded “P2 down then reject after timeout” clock. Ordinary query fail-closed on version/load failure remains; the timeout path is still open. |
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

See `nexus-permission-core/EVIDENCE.md` (this PR). Targeted suites: `G9PolicySnapshotPropagationTest`, `G9PolicySnapshotApiTest`, `JdbcPolicySnapshotPollIT`, `SnapshotManagerTest`, `RedisInvalidationTest`.
