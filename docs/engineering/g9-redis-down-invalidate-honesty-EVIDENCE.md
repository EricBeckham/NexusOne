# G9 Redis-down / PolicySnapshot invalidate honesty leftover — evidence

Recorded on this branch after rebase onto `origin/main` (`8b6acd0`, Merge #172 G17 MFA recovery/email honesty leftover; also #168 at `e63d918`). **Not** a full G9 close. **Not** live Redis process-down. **Not** live PolicySnapshot invalidate. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `PolicySnapshot` core from #89 / #83 / #100 / #121 / #128 / #148 / #161 was not redesigned.

See [docs/g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md). Keep **both** leftover rows in [docs/README.md](../README.md): [g9-policysnapshot-invalidate.md](../g9-policysnapshot-invalidate.md) (#83) and [g9-failover-remainder.md](../g9-failover-remainder.md) (#121). Sibling leftovers (#148 Sentinel/Cluster honesty, #161 live multi-JVM, #170 G3 crash-window) stay documented.

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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g9-redis-down-invalidate-honesty/`.

| Check | Result | Notes |
|---|---|---|
| Live Redis-down | **SKIP** (expected) | no listener / process-down not observed; dead client `:1` is not live-down |
| Live PolicySnapshot invalidate | **SKIP** (expected) | no observed PUBLISH/SUBSCRIBE on `:6379` |
| Dead client is live Redis-down | **false** | `#83` / `#121` simulation is not a live crash |
| Product G9 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #100/#121/#128/#148/#161 | not redone | `g9_100_121_128_148_161_redone=false` |
| G18 / AuditPort / PolicySnapshot core | unchanged | not reopened / not redesigned |

## Honest leftovers

- Live Redis 3.2 process-down mid-flight on a host that actually has Redis
- Live PolicySnapshot PUBLISH/SUBSCRIBE invalidate walk
- Redis Sentinel / Cluster / replica promotion / live multi-JVM (#148 / #161)
- Product G9 close
