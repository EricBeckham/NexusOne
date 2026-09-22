# G9 leftover: fail-closed / durable PermissionEngine when Redis is down

S0+S1 PostgreSQL slice. G18 stays closed. This does **not** rebuild `PolicySnapshot`, does **not** invent a second `TenantContext`, and does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. Version poll + invalidate-only stays on [g9-policy-snapshot.md](./g9-policy-snapshot.md). Redis-down GRANT / ordinary fail-closed stays on [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md). Bounded P2-down timeout-then-reject stays on [g9-p2-timeout-reject.md](./g9-p2-timeout-reject.md).

On `main` today those three already landed (#89 / #83 / #100). This package owns only the remaining fail-closed direction: after a GRANT is cached, a durable DENY with Redis down or a lost invalidate channel must **not** silently allow. `PermissionService.decide` stays on durable `PermissionEngine`. Redis PUBLISH / SET EX failure is recorded and is **not** treated as a remote success.

## What this close covers

| Path | Behavior |
|---|---|
| Redis-down GRANT then DENY | Admin write still bumps `iam.perm_policy_version` (or the in-memory clock). `decide` reloads from that store. Decision equals `PermissionEngine.evaluate` (`explicit_deny`). |
| Lost pub/sub after DENY | Next `SnapshotManager.get` or `PolicySnapshotPoller.tick` re-reads the durable version. Cached ALLOW cannot survive the version bump. |
| Publish / SET EX failure | `RedisPolicyInvalidation.lastRemoteNotifySucceeded()` is false. Local listener delivery is not a Redis success. The durable write is still the grant/deny success. |
| Durable version / load miss | Fail-closed `P2.PERM.0005` / 503. Never an allow. |
| Tenant context | Issued `one.nexus.p2.kernel.TenantContext` via `TenantContextHolder` only. |

Wiring is the existing `SnapshotManager` + `PermissionModule.create` + `RedisPolicyInvalidation`. No second engine.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G9 product close | Explicitly not this slice. |
| Redis Sentinel / Cluster / replica promotion | Honesty leftover is [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md). Same as #83 / #89 / #100. |
| Multi-node live partition | In-process dual-reader / lost-invalidate version-poll is [g9-multinode-partition.md](./g9-multinode-partition.md). Live multi-JVM / Sentinel / Cluster / replica promotion honesty leftover is [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md). |
| G2 product-wide same engine | Unchanged from #73. |
| G18 browser walk | Closed; not walked again. |
| Live Redis 3.2 on this VM | RESP2 client + tests are present; they skip when `127.0.0.1:6379` is down. Redis-down uses a dead client on port `1`. Live Redis process-down / live invalidate honesty leftover is [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md). Remainder leftover (dead-client harness PASS; live crash / live invalidate SKIP) is [g9-redis-down-invalidate-remainder.md](./g9-redis-down-invalidate-remainder.md). |

## How to run

```bash
./scripts/g9-failover-remainder.sh
```

Equivalent:

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9FailoverRemainderTest,JdbcG9FailoverRemainderIT,RedisInvalidationTest,G9PolicySnapshotFailoverTest,PermissionEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Zonky in-process PostgreSQL **15.x**. No Docker. No Sentinel / Cluster.

## Evidence

Recorded **2026-09-22T17:50:40Z** on this agent VM after rebase onto `main@ec116a3` (#118). OpenJDK **21.0.10**, Maven **3.8.7**, Spring Boot **4.1.1**. Zonky PostgreSQL **15.18**. No Docker. No Sentinel / Cluster.

```
./scripts/g9-failover-remainder.sh
# Tests run: 22, Failures: 0, Errors: 0, Skipped: 3  BUILD SUCCESS  2026-09-22T17:50:40Z
```

Skipped 3 are live Redis `:6379` listener cases. JDBC proof is Zonky **15.18**.

| Suite | Count | What it proves |
|---|---|---|
| `G9FailoverRemainderTest` | **5/5** | Redis-down DENY after GRANT is `explicit_deny` == durable `PermissionEngine`; lost channel reloads deny; poller tick sees deny; durable miss is `P2.PERM.0005`; publish failure is not remote success |
| `JdbcG9FailoverRemainderIT` | **1/1** | Zonky PG **15.18**: writer DENY without the reader's channel; poll tick then `decide` fail-closes (no silent allow) |
| `RedisInvalidationTest` | **1/4** (3 skip) | Dead Redis notify is observed and `lastRemoteNotifySucceeded()` is false; live PING/SUBSCRIBE skipped |
| `G9PolicySnapshotFailoverTest` | **5/5** | Existing Redis-down GRANT / ordinary fail-closed unchanged |
| `PermissionEngineTest` | **7/7** | P0-02 truth table unchanged |

Details: `nexus-permission-core/EVIDENCE.md`.
