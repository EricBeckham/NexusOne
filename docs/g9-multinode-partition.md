# G9 leftover: in-process dual-reader / partition fail-closed harness

S0+S1 PostgreSQL slice. G18 stays closed. This does **not** rebuild `PolicySnapshot`, does **not** invent a second `TenantContext`, and does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. Version poll + invalidate-only stays on [g9-policy-snapshot.md](./g9-policy-snapshot.md). Redis-down GRANT / ordinary fail-closed stays on [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md). Bounded P2-down timeout-then-reject stays on [g9-p2-timeout-reject.md](./g9-p2-timeout-reject.md). Redis-down / lost-invalidate DENY after GRANT stays on [g9-failover-remainder.md](./g9-failover-remainder.md) (#121).

On `main` today those four already landed (#89 / #83 / #100 / #121). This package owns only the remaining **partition-oriented** evidence: two reader nodes share the durable store but not the invalidate bus. After a GRANT is cached on both, a durable DENY whose `PUBLISH` is lost must **not** silently allow. Each node converges only via **its own** `PolicySnapshotPoller.tick` or request-path `SnapshotManager.get`. `PermissionService.decide` stays on durable `PermissionEngine`.

This is an **in-process dual-reader simulation**. It is **not** Redis Sentinel, Redis Cluster, replica promotion, or a live multi-JVM network partition.

## What this close covers

| Path | Behavior |
|---|---|
| Lost invalidate across two readers | Writer + reader A + reader B share `InMemoryPolicyRepository` (or `iam.perm_policy_version` on Zonky PG 15). Separate `InMemoryPolicyInvalidation` buses. Writer DENY notifies only the writer bus. |
| Partition window | Both reader LRU entries stay on the GRANT snapshot. Neither is `isMarkedInvalidated`. |
| Independent version poll | `PolicySnapshotPoller.tick` on reader A advances A only. Reader B stays stale until **its** tick. Then both `decide` == durable `PermissionEngine` (`explicit_deny`). |
| Independent request-path `get` | `readerA.service().decide` reloads A. Reader B LRU is unchanged until B's own `decide`. |
| Redis-down writer + partitioned readers | Writer uses `RedisPolicyInvalidation` against a dead client on port `1`. `lastRemoteNotifySucceeded()` is false. Readers still converge via version poll. |
| Tenant context | Issued `one.nexus.p2.kernel.TenantContext` via `TenantContextHolder` only. Role is `g9_self_reader` + `DataScope.SELF`, not `tenant_member`. |

Wiring is the existing `SnapshotManager` + `PermissionModule.create` + `PolicySnapshotPoller`. No second engine. No production `AuditPort` change.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G9 product close | Explicitly not this slice. |
| Redis Sentinel / Cluster / replica promotion | Same as #83 / #89 / #100 / #121. Not started, not evidenced. |
| Live multi-JVM / network partition | Two `PermissionModule` instances in one JVM. Separate buses simulate a lost `PUBLISH`. No second process, no iptables, no Docker. |
| Production bounded-stale window | Product poller default remains 2s (#89). This package only proves per-node poll / `get` convergence. |
| G2 product-wide same engine | Unchanged from #73 / #122. |
| G18 browser walk | Closed; not walked again. |
| G4 hot-row purge / `AuditPort` | Orthogonal (#123). This package does not touch `AuditPort`. |

## How to run

```bash
./scripts/g9-multinode-partition.sh
```

Equivalent:

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9DualReaderPartitionTest,JdbcG9DualReaderPartitionIT,G9FailoverRemainderTest,G9PolicySnapshotPropagationTest,PermissionEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Zonky in-process PostgreSQL **15.x**. No Docker. No Sentinel / Cluster. No live Redis required.

## Evidence

Recorded after the harness run on this agent VM (OpenJDK **21**, Maven **3.8.x**, Spring Boot **4.1.1**). See the follow-up commit on this PR for the exact suite counts. Until that run lands, treat the script as the reproduction command, not as a claimed BUILD SUCCESS.

| Suite | What it proves |
|---|---|
| `G9DualReaderPartitionTest` | Two in-process readers: lost invalidate leaves both LRU on GRANT; A's poll / `get` does not mutate B; both fail-close to `explicit_deny` == durable `PermissionEngine`; Redis-down writer does not notify partitioned readers |
| `JdbcG9DualReaderPartitionIT` | Zonky PG **15.x**: `iam.perm_policy_version` bumps while both reader LRU stay stale; each node's poll converges independently |
| `G9FailoverRemainderTest` | #121 Redis-down DENY regression |
| `G9PolicySnapshotPropagationTest` | #89 dual-instance GRANT / poll regression |
| `PermissionEngineTest` | P0-02 truth table unchanged |

Details: `nexus-permission-core/EVIDENCE.md`.
