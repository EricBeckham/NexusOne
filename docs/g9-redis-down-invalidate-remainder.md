# G9 leftover — Redis-down / PolicySnapshot invalidate remainder beyond #174

S0+S1 remainder after the #174 honesty inventory ([g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md)) and sibling leftovers [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md) (#83) / [g9-failover-remainder.md](./g9-failover-remainder.md) (#121). Rebased onto `origin/main` `8037090` (Merge #203 G4 export-wizard deny honesty after #202 / #196 / #195 / #191). **G18 stays closed.** This leftover does **not** redo #83 / #100 / #121 / #128 / #148 / #161 / #174 product code. It does **not** rebuild `PolicySnapshot`, does **not** invent a second `TenantContext`, and does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. Does **not** claim product G9 close. Product G9 close stays `NOT_CLAIMED`. `g9_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md): [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md) (#83), [g9-failover-remainder.md](./g9-failover-remainder.md) (#121), [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) (#174), this file, [g9-multinode-partition.md](./g9-multinode-partition.md) (#128), [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md) (#148), [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) (#161), [s0s1-live-redis-minio-isolation-honesty.md](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md) (#186), [g4-p1-staging-audit-events-honesty.md](./g4-p1-staging-audit-events-honesty.md) (#190), [linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md) (#193), [g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md) (#192), [s0-secret-rotate-without-restart-honesty.md](./s0-secret-rotate-without-restart-honesty.md) (#200), [g15-wx-dt-native-bridge-honesty.md](./g15-wx-dt-native-bridge-honesty.md) (#198), [g1-queryast-boundsql-honesty.md](./g1-queryast-boundsql-honesty.md) (#191), [g3-outbox-consume-crash-honesty.md](./g3-outbox-consume-crash-honesty.md) (#195), [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md) (#196), [g10-leftover-honesty.md](./g10-leftover-honesty.md) (#202), [g4-export-wizard-deny-honesty.md](./g4-export-wizard-deny-honesty.md) (#203), [g17-separation-honesty.md](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md) (#183), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179), [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), [g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) (#175), and [g3-claim-crash-window.md](./g3-claim-crash-window.md) (#170).

#174 already catalogues that a dead client on port `1` is **not** a live Redis process-down, and that live invalidate stays SKIP unless observed. The leftover here **proves PASS** where that dead-client harness actually works (`Redis32Client("127.0.0.1", 1)` + `RedisPolicyInvalidation.notifyTenant` is not a remote success). Live Redis process crash and live PolicySnapshot PUBLISH/SUBSCRIBE stay honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| Dead-client harness | #83 / #121 use `Redis32Client("127.0.0.1", 1)`. This remainder **proves** notify fail-closes: `lastRemoteNotifySucceeded()=false`, PUBLISH / SET EX not written. `dead_client_harness=PASS`. `dead_client_is_not_live_down=true`. |
| Live Redis probe | `Redis32Client.PING` / `INFO replication` against `127.0.0.1:6379`. Missing listener is honest SKIP. |
| Honest Redis-down SKIP | `live_redis_down=SKIP` unless `redis_present` **and** `redis_down_observed`. Dead-client PASS is **not** a live process crash. |
| Honest live-invalidate SKIP | `live_invalidate=SKIP` unless `redis_present` **and** `live_invalidate_observed`. A surefire skip / #174 inventory is not a live PUBLISH/SUBSCRIBE walk. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g9_close=CLOSED`, rejects a #83/#100/#121/#128/#148/#161/#174 redo flag, and requires leftover rows kept. |

No Docker. No iptables. No Redis start/stop. `Redis32Client` stays standalone RESP2.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G9 product close | Explicitly `g9_close=NOT_CLAIMED`. |
| Live Redis process-down | Dead-client harness PASS is not a live crash. No Redis was stopped on this VM. |
| Live PolicySnapshot invalidate | No observed PUBLISH/SUBSCRIBE on `:6379`. |
| Redis Sentinel / Cluster / replica promotion | Inventory stays on #148 / #161. |
| In-process dual-reader / lost-invalidate | Stays on #128. |
| P2 timeout-then-reject | Stays on #100. |
| #174 honesty inventory redo | Sibling leftover. `g9_83_100_121_128_148_161_174_redone=false`. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## How to run

```bash
./scripts/g9-redis-down-invalidate-remainder.sh
./scripts/g9-redis-down-invalidate-remainder.sh --verify-report <out>/report.env
./scripts/g9-redis-down-invalidate-remainder.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-redis-down-invalidate-remainder.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder/fixtures/fake-live-redis-down-pass.env
./scripts/g9-redis-down-invalidate-remainder.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder/fixtures/fake-live-invalidate-pass.env
./scripts/g9-redis-down-invalidate-remainder.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder/fixtures/fake-g9-close.env
```

Always-green Java (does not redo #83 / #100 / #121 / #128 / #148 / #161 / #174 suites):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G9RedisDownInvalidateRemainderTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Sibling leftovers stay as reproduction commands (do **not** treat them as this leftover):

```bash
./scripts/g9-redis-down-invalidate-honesty.sh
./scripts/g9-policysnapshot-invalidate.sh
./scripts/g9-failover-remainder.sh
```

Zonky / live Redis are **not** required. Java 21 / Boot **4.1.1**. No Docker.

See [engineering/g9-redis-down-invalidate-remainder-EVIDENCE.md](./engineering/g9-redis-down-invalidate-remainder-EVIDENCE.md). Live `report.env` is gitignored under `.local/g9-redis-down-invalidate-remainder/`.

## Honest leftovers (not claimed)

- Live Redis 3.2 process-down mid-flight (not a dead client on port `1`)
- Live PolicySnapshot PUBLISH/SUBSCRIBE on a host that actually has Redis 3.2
- Redis Sentinel / Cluster failover / replica promotion / live multi-JVM (#148 / #161)
- Product G9 close
- Redesigning `PolicySnapshot` / `SnapshotManager` / `PolicySnapshotPoller`
- Redo of #83 / #100 / #121 / #128 / #148 / #161 / #174
