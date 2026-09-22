# S0+S1 live Redis + MinIO isolation evidence

Ops drill for `CacheKeyGuard` / `FileObjectKeyGuard` against live Redis and MinIO on **dedicated** ports. Not a P0/M0 redo. G18 stays closed. `AuditPort` same-TX `append` was not modified.

Runbook: [engineering/runbooks/live-redis-minio-isolation.md](./engineering/runbooks/live-redis-minio-isolation.md).

Recorded **2026-09-22T15:40:42Z** on this agent VM. OpenJDK **21.0.10**, Maven **3.8.7**, Spring Boot **4.1.1**. Native processes. No Docker. Product `:5432` / `:6379` were free and were **not** bound.

## Command

```bash
./ops/scripts/live-redis-minio-isolation-drill.sh
```

```
== drill summary
fixture CachePortIsolationTest/FileServiceIsolationTest/FileApiTest: PASS
live Redis 127.0.0.1:16379: PASS (native /tmp/nexus-isolation-drill/redis-3.2.13/src/redis-server :16379)
live MinIO 127.0.0.1:19000: PASS (native /tmp/nexus-isolation-drill/minio :19000)
product ports avoided: :5432 :6379
AuditPort TX: not modified
```

`BUILD SUCCESS` 2026-09-22T15:40:42Z. Cache module 7/7. File module 7/7 (0 skipped).

## Live Redis (`P1.M02.014`)

Compiled Redis **3.2.13** from source into `/tmp/nexus-isolation-drill`. Bound `127.0.0.1:16379` only (save/AOF off).

```
LIVE redis isolation PASS host=127.0.0.1 port=16379 version=redis_version:3.2.13 reject=P1.M02.014 commandsUnchanged=1
```

| Check | Result |
|---|---|
| Tenant A `SETEX` of `nexus:drill:alpha:p3:iso-…` | 1 counted command (control write) |
| Seed B key via raw Jedis (not the product adapter) | B value present |
| Tenant A `CacheKeyGuard.assertMatches(nexus:drill:beta:…)` | `P1.M02.014` + `IsolationAuditEvent` |
| Counted Redis commands after reject | still **1** |
| B key after reject | still `owned-by-b` |
| A `GET` | still `owned-by-a` on the A key |

`LiveRedis32OptionalTest` also ran against the same dedicated port (TTL/INCR smoke, 1/1).

## Live MinIO (`P1.M02.015`)

Native community binary `RELEASE.2025-09-07T16-13-09Z` (GitHub asset; floating `dl.min.io/.../minio` is HTTP 410). Bound `127.0.0.1:19000` (console `:19001`). Credentials in `/tmp/nexus-isolation-drill/minio.env` (`0600`, not git). Bucket `nexus-isolation-drill`. File metadata in-memory — no PostgreSQL.

```
LIVE minio isolation PASS endpoint=http://127.0.0.1:19000 bucket=nexus-isolation-drill reject=P1.M02.015 puts=1 signs=0 deletes=1
```

| Check | Result |
|---|---|
| Tenant A upload | 1 `put`; object under `tenants/{A}/ledger/…` |
| Tenant B `signDownload` / `FileObjectKeyGuard.assertOwned` | `P1.M02.015` |
| Tenant B `delete` | `P1.M02.016` (`SideEffectGuard` before storage) |
| `presignGet` count on reject | **0** |
| Extra `put`/`delete` on reject | **0** |
| MinIO list `tenants/{B}/` | empty |
| Tenant A object after reject | still listed; download bytes match |
| Same-tenant A cleanup `delete` | the `deletes=1` in the PASS line |

## Fixtures (always, no live backend required)

| Suite | Count | What it proved |
|---|---|---|
| `CachePortIsolationTest` | 5/5 | Fake Redis: foreign key rejected; command count unchanged |
| `FileServiceIsolationTest` | 5/5 | Counting storage: cross-tenant sign rejected; `presignGet` stays 0 |
| `FileApiTest` | 1/1 | HTTP signed-url still `P1.M02.015` |

## Honest leftovers

| Item | Status |
|---|---|
| Product Redis on `:6379` | Not used. Dedicated `:16379` only. |
| Floating `dl.min.io/server/minio/release/linux-amd64/minio` | HTTP **410**. Drill falls back to GitHub `RELEASE.2025-09-07T16-13-09Z`. Not a licensed AIStor digest. |
| GaussDB | Out of this PostgreSQL slice. Unused here. |
| AuditPort same-TX `append` | Unchanged. |
| G6 restore / RPO | Still [object-store-restore.md](./engineering/runbooks/object-store-restore.md). |
