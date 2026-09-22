# Live Redis + MinIO isolation drill

S0+S1 ops drill. Not a P0/M0 redo. Product G18 stays closed. This runbook does **not** change `AuditPort` same-TX `append`.

Goal: prove `CacheKeyGuard` (`P1.M02.014`) and `FileObjectKeyGuard` (`P1.M02.015`) reject a foreign tenant **before** a live Redis command or MinIO/S3 call. When live backends are missing, the same rejects are still proven against Fake Redis / counting storage fixtures, and the live slice is recorded as `SKIP` with a reason.

## Ports

Prefer dedicated drill ports. Do not steal product listeners.

| Role | Default | Notes |
|---|---|---|
| Drill Redis | `127.0.0.1:16379` | `NEXUS_DRILL_REDIS_PORT`. Never default to product `:6379`. |
| Drill MinIO API | `127.0.0.1:19000` | `NEXUS_DRILL_MINIO_API_PORT` |
| Drill MinIO console | `127.0.0.1:19001` | drill-only |
| Product PostgreSQL | `:5432` | **Do not bind.** File metadata for this drill is in-memory. |
| Product Redis | `:6379` | Reuse only if the operator exports `NEXUS_REDIS_PORT=6379` on purpose. |

If `:16379` / `:19000` are already taken, the script picks the next free dedicated port and logs the switch.

## What is proven

| Surface | Guard | Reject | Live assertion |
|---|---|---|---|
| Cache | `CacheKeyGuard` | `P1.M02.014` | Tenant A cannot address `nexus:{env}:{B}:...`. Counted Redis commands stay unchanged. A seeded B key is untouched. |
| File | `FileObjectKeyGuard` | `P1.M02.015` | Tenant B cannot sign/delete A's object. `put`/`presign`/`delete` counts stay unchanged. MinIO listing has no `tenants/{B}/` object for the run. |

Same-tenant A write **does** hit the live backend (that is the control). The reject path must not.

Fixtures (always):

- `CachePortIsolationTest` — Fake Redis, reject-before-command
- `FileServiceIsolationTest` / `FileApiTest` — counting / memory storage, reject-before-S3

Optional live:

- `LiveRedisIsolationOptionalTest` — JUnit `assumeTrue` skip when `NEXUS_REDIS_HOST:NEXUS_REDIS_PORT` (default `:16379`) is down
- `LiveMinioIsolationOptionalTest` — skip when `S3_ENDPOINT` / keys are unset or MinIO is down

`LiveRedis32OptionalTest` remains the existing TTL/INCR smoke (also skips when unreachable).

## Run

Native processes. No Docker. No product JAR.

```bash
./ops/scripts/live-redis-minio-isolation-drill.sh
```

The script:

1. Logs whether product `:5432` / `:6379` are occupied and refuses to bind them.
2. Tries to start or reuse Redis on the dedicated port (prefers a compiled Redis **3.2.13** under `/tmp/nexus-isolation-drill`; falls back to `redis-server` on PATH; otherwise `SKIP` live Redis).
3. Tries to start or reuse native MinIO on the dedicated API port. Writes credentials to `/tmp/nexus-isolation-drill/minio.env` (`0600`). Passwords are not printed.
4. Runs the fixture + optional live tests.
5. Prints `PASS` / `SKIP (reason)` / `FAIL`.
6. Stops only the processes it started.

Manual backends (if you already have native Redis/MinIO):

```bash
export NEXUS_REDIS_HOST=127.0.0.1
export NEXUS_REDIS_PORT=16379          # dedicated
export S3_ENDPOINT=http://127.0.0.1:19000
export S3_ACCESS_KEY=...               # from Secret / env, not git
export S3_SECRET_KEY=...
export S3_TEST_BUCKET=nexus-isolation-drill

mvn -pl nexus-cache-core,nexus-file-core -am test \
  -Dtest=CachePortIsolationTest,FileServiceIsolationTest,FileApiTest,LiveRedisIsolationOptionalTest,LiveMinioIsolationOptionalTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## Honest leftovers

| Item | Status |
|---|---|
| Product Redis 3.2 on `:6379` | Not this drill. Dedicated port only. If compile of 3.2.13 fails, the script may start whatever `redis-server` is on PATH and must record that version. |
| AIStor / licensed MinIO digest | Not claimed. The drill tries the last community linux-amd64 GitHub asset (`RELEASE.2025-09-07T16-13-09Z`), then `go install`. The floating `dl.min.io/.../minio` URL currently returns HTTP 410. |
| GaussDB | Out of this PostgreSQL slice. Unused here anyway. |
| AuditPort same-TX `append` | Unchanged. Guard rejects still use `appendNewTransaction` as in G1. |
| G6 object-store restore / RPO | Still [object-store-restore.md](./object-store-restore.md). This drill is isolation, not restore. |

Evidence from a real run lives in [docs/s0s1-live-redis-minio-isolation.md](../../s0s1-live-redis-minio-isolation.md).
