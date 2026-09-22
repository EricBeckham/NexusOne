# S0+S1 leftover — live Redis + MinIO tenant-key / bucket-prefix isolation honesty

S0+S1 remainder after the optional G1 live Redis/MinIO drills ([g1-live-redis-minio.md](./g1-live-redis-minio.md)) and the ops dedicated-port drill ([s0s1-live-redis-minio-isolation.md](./s0s1-live-redis-minio-isolation.md)). Rebased onto `origin/main` `03e2a4a` (Merge #189 S0+S1 claim-list tip sync after #187; also #183 / #184 / #182 / #179 / #181 / #177 / #180). **G18 stays closed.** This leftover does **not** redo G9 Redis-down / PolicySnapshot honesty ([g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) / #174), does **not** redo G10 file-reconcile honesty ([g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) / #169), does **not** redo G10 identity drift-fix honesty ([g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) / #179), does **not** redo #83 / #100 / #121 / #148 / #161, and does **not** redo P0-05 / P0-06 or M0 Redis/MinIO bases.

The leftover here is the **honesty inventory** for **tenant-key** (`CacheKeyGuard` `P1.M02.014`) and **bucket-prefix** (`FileObjectKeyGuard` `P1.M02.015`) isolation under live Redis 3.2 / MinIO. In-process fixtures stay always-green. Live Redis / MinIO is **PASS** only when the listener is up **and** a reject-before-backend walk was observed. Missing `S3_*` or a down listener is honest **SKIP**. It never fabricates `PASS`. Product live Redis/MinIO drill close stays `live_drill_close=NOT_CLAIMED`. Product G9 / G10 close stay `NOT_CLAIMED`.

Keep **all** sibling leftover rows in [docs/README.md](./README.md), including [g17-separation-honesty.md](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171), [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) (#174), [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) (#169), [g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) (#175), and this file.

## What this leftover covers

| Path | Behavior |
|---|---|
| Fixture tenant-key | `CacheKeyGuard.assertMatches` rejects `nexus:{env}:{B}:...` **before** the cache sink. Command / write count stays unchanged. `P1.M02.014`. |
| Fixture bucket-prefix | `FileObjectKeyGuard.assertOwned` rejects `tenants/{B}/...` **before** the storage sink. `put` / `presign` counts stay unchanged. `P1.M02.015`. |
| Live Redis inventory | Probe dedicated `:16379` then product `:6379`. This leftover **does not start** Redis. Presence without an observed reject-before-command walk stays SKIP. |
| Live MinIO inventory | Probe `S3_ENDPOINT` + loopback `:19000` / `:9000`. Credentials stay in env / SecretPort. Presence without an observed reject-before-S3 walk stays SKIP. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g9_close=CLOSED` / `g10_close=CLOSED` / `live_drill_close=CLOSED`, rejects a #169/#174 redo flag. |

No Docker. No iptables. Dedicated product `:5432` / `:6379` are **not** bound by this leftover.

## What this does not claim

| Leftover | Why |
|---|---|
| Product live Redis / MinIO drill close | Explicitly `live_drill_close=NOT_CLAIMED`. Existing drills stay optional + skip-when-absent. |
| Product G9 close | Orthogonal leftover [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md). Dead client `:1` is not this tenant-key walk. |
| Product G10 close | Orthogonal leftover [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md). Preview/apply reconcile is not this bucket-prefix reject. |
| Starting Redis 3.2 / MinIO | Ops drill may start native processes. This leftover only probes. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## How to run

```bash
# Always-green fixture + live probe (honest SKIP when Redis/MinIO are down)
./scripts/s0s1-live-redis-minio-isolation-honesty.sh

# Honesty gate only
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

Always-green Java (does not redo #169 / #174 suites):

```bash
mvn -pl modules/isolation-kernel -am test \
  -Dtest=S0S1LiveRedisMinioIsolationHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Existing reproduction commands stay unchanged:

```bash
./scripts/g1-live-redis-minio-drill.sh
./ops/scripts/live-redis-minio-isolation-drill.sh
./scripts/g9-redis-down-invalidate-honesty.sh
./scripts/g10-file-reconcile-honesty.sh
```

See [engineering/s0s1-live-redis-minio-isolation-honesty-EVIDENCE.md](./engineering/s0s1-live-redis-minio-isolation-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/s0s1-live-redis-minio-isolation-honesty/`.

## Honest leftovers (not claimed)

- Live Redis 3.2 tenant-key reject-before-command on a host that actually has Redis
- Live MinIO bucket-prefix reject-before-S3 on a host that actually has MinIO + credentials
- Product live Redis / MinIO drill close
- Product G9 / G10 close
- Redis Sentinel / Cluster / replica promotion (#148 / #161 / #174)
- File metadata ↔ object reconcile apply (#169)
