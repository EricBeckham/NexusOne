# G9 PolicySnapshot invalidate / Redis-down failover

门禁 G9（需求规格 V1.1 §12 / ARCH-011）：**实例按策略版本轮询。缓存频道丢失或 P2 短时不可用时，普通查询重新加载快照；超过时限无法加载则拒绝。**

S0+S1 overnight slice. G18 stays closed. This package does **not** redo P0/M0, does **not** add a second `TenantContext`, and does **not** change `PermissionEngine` (coordinate with G2 matrix #73: same `PermissionService.decide` → `PermissionEngine.evaluate`).

## 口径

| Path | Must keep |
|---|---|
| Redis invalidate | `PUBLISH nexus:v1:permission:invalidate` + `SET EX` version marker only. Redis is **not** a policy payload store. |
| Redis down / publish fail | Grant writes still bump `iam.perm_policy_version` (or the in-memory durable map). `PermissionService.decide` reloads from that store. Decision equals `PermissionEngine.evaluate` on `PolicyRepository.loadSnapshot`. |
| Lost pub/sub | Next `SnapshotManager.get` re-reads the durable version. Channel loss cannot leave a process on the old snapshot. |
| Durable version / load failure | Fail-closed `P2.PERM.0005` / `SnapshotUnavailable`. Redis notify must not substitute a decision. |
| Tenant context | Issued `one.nexus.p2.kernel.TenantContext` via `TenantContextHolder` only. Client `X-Tenant-Id` stays `P2.PERM.0013`. |

Implementation is the existing M0-05 `SnapshotManager` + `RedisPolicyInvalidation`. This package adds measurable evidence, not a second engine.

## How to run

```bash
./scripts/g9-policysnapshot-invalidate.sh
```

Equivalent:

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9PolicySnapshotFailoverTest,RedisInvalidationTest,SnapshotManagerTest,PermissionEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Live Redis 3.2 on `127.0.0.1:6379` is optional. `RedisInvalidationTest` skips the PING/SUBSCRIBE cases when it is not listening. Redis-down cases always run (dead client on port `1`).

## Evidence

Recorded **2026-09-22T15:55:05Z** on this agent VM after rebase onto `main@215308f` (G9 product wiring #89 + G7 #69 + G4 #77 + G2 #73). Java **21.0.10**, Maven **3.8.7**, Spring Boot **4.1.1**. No Docker. No local Redis 3.2 / PostgreSQL listener (Redis-down uses a dead client on port `1`). `RedisInvalidationTest` is main's #89 copy (0 diff).

```
./scripts/g9-policysnapshot-invalidate.sh
# Tests run: 21, Failures: 0, Errors: 0, Skipped: 3  BUILD SUCCESS  2026-09-22T15:55:05Z

mvn -pl nexus-permission-core -am test
# Tests run: 47, Failures: 0, Errors: 0, Skipped: 5  BUILD SUCCESS  2026-09-22T15:55:20Z
```

| Suite | Count | What it proves |
|---|---|---|
| `G9PolicySnapshotFailoverTest` | **5/5** | Redis-down grant → `decide` matches durable `PermissionEngine`; lost channel still reloads; Redis snapshot key is not truth; durable failure stays `P2.PERM.0005`; missing issued context is `P2.PERM.0009` (no invented holder) |
| `RedisInvalidationTest` | **1/4** (3 skip) | Main/#89 contract: dead Redis publish is observed and is not truth; live PING/SUBSCRIBE skipped — no Redis 3.2 on this VM |
| `SnapshotManagerTest` | **5/5** | Main/#89: poll-on-get; `pollCached` reloads LRU; background poll swallows load failure |
| `PermissionEngineTest` | **7/7** | P0-02 truth table unchanged (G2 coordination) |

## Honest leftovers

| Still open | Why |
|---|---|
| Live Redis process-down / live invalidate | Dead client on port `1` is not a live Redis crash. Honesty leftover is [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md). |
| Redis Sentinel / Cluster / replica promotion | Explicitly not this slice (same as M0-05). |
| Multi-node live partition | In-process dual-reader / lost-invalidate version-poll is [g9-multinode-partition.md](./g9-multinode-partition.md). Live multi-JVM / Sentinel / Cluster / replica promotion honesty leftover is [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md). |
| Production poll interval / bounded-stale window | Product poller is on `main` (#89). This package only proves Redis-down / lost-channel `decide` == durable `PermissionEngine`. Bounded P2-down timeout-then-reject is [g9-p2-timeout-reject.md](./g9-p2-timeout-reject.md). Fail-closed DENY after lost invalidate is [g9-failover-remainder.md](./g9-failover-remainder.md). In-process dual-reader / lost-invalidate version-poll is [g9-multinode-partition.md](./g9-multinode-partition.md). |
| G2 product-wide matrix / UI / H5 / report / export | G2 list/detail/flow + role matrix is on `main` (#73). This package does not edit `PermissionEngine`, `SurfaceGuard`, or the role-matrix APIs. |
| Live JAR + browser snapshot walk | G18 already recorded `GET /api/v1/permission/snapshot`. This package is invalidate + Redis-down evidence, not a second G18 walk. |
| GaussDB | Same as M0-05: in-memory durable store for the failover path. |
