# G9 Redis-down / PolicySnapshot invalidate remainder leftover — evidence

Recorded on this branch after rebase onto `origin/main` `514c9b7` (Merge #194 claim-list tip alignment after #186; also #185 / #189 / #187). **Not** a full G9 close. **Not** live Redis process-down. **Not** live PolicySnapshot invalidate. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `PolicySnapshot` core from #89 / #83 / #100 / #121 / #128 / #148 / #161 / #174 was not redesigned.

See [docs/g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g9-policysnapshot-invalidate.md](../g9-policysnapshot-invalidate.md) (#83), [g9-failover-remainder.md](../g9-failover-remainder.md) (#121), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), this leftover, [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), and the other leftover rows already on `main`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04 | product baseline |
| Maven | 3.8.7 (or agent PATH) | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Redis | not required | live Redis-down / live invalidate SKIP when listener missing |
| Runtime | local `mvn test` + collect script | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g9-redis-down-invalidate-remainder/`.

| Check | Result | Notes |
|---|---|---|
| Dead-client harness (port `1`) | **PASS** | `Redis32Client` + `RedisPolicyInvalidation.notifyTenant`; not remote success |
| Live Redis-down | **SKIP** (expected) | no listener / process-down not observed; dead-client PASS is not live-down |
| Live PolicySnapshot invalidate | **SKIP** (expected) | no observed PUBLISH/SUBSCRIBE on `:6379` |
| Dead client is live Redis-down | **false** | `#83` / `#121` simulation is not a live crash |
| Leftover rows kept | **true** | `#83` / `#121` / `#174` / `#185` + siblings |
| Product G9 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #83/#100/#121/#128/#148/#161/#174 | not redone | `g9_83_100_121_128_148_161_174_redone=false` |
| G18 / AuditPort / PolicySnapshot core | unchanged | not reopened / not redesigned |

## Honest leftovers

- Live Redis 3.2 process-down mid-flight on a host that actually has Redis
- Live PolicySnapshot PUBLISH/SUBSCRIBE invalidate walk
- Redis Sentinel / Cluster / replica promotion / live multi-JVM (#148 / #161)
- Product G9 close
