# S0+S1 live Redis + MinIO tenant-key / bucket-prefix isolation honesty leftover — evidence

Recorded on this branch after rebase onto `origin/main` (`03e2a4a`, Merge #189 S0+S1 claim-list tip sync after #187; also #183 / #184 / #182 / #179 / #181 / #177 / #180). **Not** a product live Redis/MinIO drill close. **Not** product G9 close. **Not** product G10 close. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0-05 / P0-06 and M0 Redis/MinIO bases were not redone.

See [docs/s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md). Keep **all** sibling leftover rows in [docs/README.md](../README.md), including [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173), and this leftover.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Redis 3.2 | not required | live tenant-key isolation SKIP when listener missing |
| MinIO | not listening on `127.0.0.1:19000` / `:9000` | `S3_*` unset |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/s0s1-live-redis-minio-isolation-honesty.sh
./scripts/s0s1-live-redis-minio-isolation-honesty.sh --verify-report <out>/report.env
./scripts/s0s1-live-redis-minio-isolation-honesty.sh --verify-report \
  scripts/s0s1-live-redis-minio-isolation-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/s0s1-live-redis-minio-isolation-honesty.sh --verify-report \
  scripts/s0s1-live-redis-minio-isolation-honesty/fixtures/fake-live-redis-isolation-pass.env
./scripts/s0s1-live-redis-minio-isolation-honesty.sh --verify-report \
  scripts/s0s1-live-redis-minio-isolation-honesty/fixtures/fake-live-minio-isolation-pass.env
./scripts/s0s1-live-redis-minio-isolation-honesty.sh --verify-report \
  scripts/s0s1-live-redis-minio-isolation-honesty/fixtures/fake-g9-close.env
./scripts/s0s1-live-redis-minio-isolation-honesty.sh --verify-report \
  scripts/s0s1-live-redis-minio-isolation-honesty/fixtures/fake-g10-close.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/s0s1-live-redis-minio-isolation-honesty/`. A sanitized copy is [`s0s1-live-redis-minio-isolation-honesty/agent-vm.env`](./s0s1-live-redis-minio-isolation-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g9_close=CLOSED` / `g10_close=CLOSED` rejected | recorded after collect |
| Fixture tenant-key `P1.M02.014` | **PASS** (gate) | `CacheKeyGuard` reject-before-sink |
| Fixture bucket-prefix `P1.M02.015` | **PASS** (gate) | `FileObjectKeyGuard` reject-before-sink |
| Live Redis tenant-key isolation | **SKIP** (expected) | no listener / walk not observed; leftover does not start Redis |
| Live MinIO bucket-prefix isolation | **SKIP** (expected) | `S3_ENDPOINT` unset; nothing on `:19000` / `:9000` |
| live_redis_isolation_observed | **false** | honesty gate requires `false` on SKIP |
| live_minio_isolation_observed | **false** | honesty gate requires `false` on SKIP |
| #174 G9 Redis-down redo | **false** | [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) |
| #169 G10 file-reconcile redo | **false** | [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) |
| Product live drill / G9 / G10 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live Redis 3.2 tenant-key reject-before-command on a host that actually has Redis
- Live MinIO bucket-prefix reject-before-S3 on a host that actually has that process
- Product live Redis / MinIO drill close
- Product G9 / G10 close
