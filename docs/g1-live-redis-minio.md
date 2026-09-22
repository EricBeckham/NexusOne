# G1 live Redis / MinIO isolation drills

S0+S1 optional evidence on top of [g1-isolation-surfaces.md](./g1-isolation-surfaces.md).  
G18 stays closed. **AuditPort same-TX `append` is unchanged.** Guard-reject still uses `appendNewTransaction`.

These drills prove the G1 cache / file surfaces against a **reachable** Redis and MinIO:

1. Cross-tenant work is rejected **before** the backend command / S3 call
2. `IsolationAuditEvent` (`ISOLATION_REJECT`) is recorded
3. Product `AuditPortIsolationSink` persists `GUARD_REJECT` via `appendNewTransaction` (never `append`)

They **skip** when the live service is absent. No Docker. Default `mvn test` stays green on a machine with only Java 21 / Boot 4.1.1 / PostgreSQL 15 (Zonky).

## How to run

```bash
# optional live endpoints — omit them to SKIP
export NEXUS_REDIS_HOST=127.0.0.1
export NEXUS_REDIS_PORT=6379
export S3_ENDPOINT=http://127.0.0.1:9000
export S3_ACCESS_KEY=...          # env / Secret only
export S3_SECRET_KEY=...
export S3_TEST_BUCKET=nexus-g1-live   # must already exist

./scripts/g1-live-redis-minio-drill.sh
```

| Probe | Skip when |
|---|---|
| Live Redis | `PING` at `NEXUS_REDIS_HOST:NEXUS_REDIS_PORT` fails (defaults `127.0.0.1:6379`) |
| Live MinIO | `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` missing, endpoint unreachable, or `S3_TEST_BUCKET` (default `nexus-g1-live`) not present |

The product Redis adapter still only emits the Redis **3.2** command set (`GET` / `SET EX` / `SET NX PX` / `DEL` / `EVAL` / `PING` / `INFO server`). A newer Redis that accepts those commands is a valid live target; record the `INFO server` version.

## What each test proves

| Suite | Reject-before | Guard record |
|---|---|---|
| `LiveRedisIsolationDrillTest` | `CacheKeyGuard` + `Redis32CacheAdapter.release` of a foreign key; live command count unchanged; foreign key never `EXISTS` | `ISOLATION_REJECT` / `P1.M02.014` |
| `LiveMinioIsolationDrillTest` | `FileService.signDownload` / `download` + `FileObjectKeyGuard`; live put/get/presign counts unchanged; object stays under `tenants/{A}/` | `ISOLATION_REJECT` / `P1.M02.015` |
| `LiveG1CacheFileDrillTest` | Same live paths through `AuditPortIsolationSink` | `GUARD_REJECT` via **`appendNewTransaction` only**; `append` call count stays 0; hash chain intact |
| `G1IsolationSurfacesTest` | In-process (no live backend) | Unchanged four-surface close |

`LiveRedis32OptionalTest` remains the capability ping (TTL / `INCR` / lock) and also skips when Redis is down.

## Evidence

Recorded on this agent VM. Java **21.0.10**, Maven **3.9.11**, Spring Boot **4.1.1**. No Docker. Live Redis is product **3.2.12** (`INFO server`). Live MinIO is community `RELEASE.2025-09-07T16-13-09Z` on `127.0.0.1:9000` with a pre-created `nexus-g1-live` bucket. Isolation-kernel / Task ITs still use in-process PostgreSQL **15.18** (Zonky) when those suites run. `AuditPort.append` was not modified.

### Live pass — `2026-09-22T15:41:53Z`

```
./scripts/g1-live-redis-minio-drill.sh
# redis_probe=UP 3.2.12
# minio_probe=UP http://127.0.0.1:9000 bucket=nexus-g1-live
# BUILD SUCCESS  Total time: 6.694 s
```

| Suite | Result | What it printed |
|---|---|---|
| `LiveRedisIsolationDrillTest` | **1/1** | `LIVE_REDIS_ISOLATION_DRILL version=3.2.12` |
| `LiveRedis32OptionalTest` | **1/1** | capability ping on the same 3.2.12 |
| `CachePortIsolationTest` | **5/5** | in-process reject-before Fake Redis (always-on) |
| `LiveMinioIsolationDrillTest` | **1/1** | `LIVE_MINIO_ISOLATION_DRILL ... object=tenants/{A}/ledger/2026/09/...` |
| `FileServiceIsolationTest` | **5/5** | in-process reject-before counting storage |
| `LiveG1CacheFileDrillTest` | **2/2** | `LIVE_G1_REDIS_DRILL guard=P1.M02.014 newTx=2 append=0` ; `LIVE_G1_MINIO_DRILL guard=P1.M02.015 newTx=3 append=0` |
| `G1IsolationSurfacesTest` | **1/1** | in-process four-surface close unchanged |

### Clean skip — `2026-09-22T15:42:04Z`

Same suites with `NEXUS_REDIS_PORT=1` and `S3_*` unset:

```
LiveRedisIsolationDrillTest     Tests run: 1, Skipped: 1   (127.0.0.1:1)
LiveRedis32OptionalTest         Tests run: 1, Skipped: 1
LiveMinioIsolationDrillTest     Tests run: 1, Skipped: 1   (S3_* missing)
LiveG1CacheFileDrillTest        Tests run: 2, Skipped: 2
BUILD SUCCESS
```

`mvn test` does not fail when live services are absent.

This page is not a product-wide G1 “zero leak” close and is not GaussDB.

## Honest leftover

| Still open | Why |
|---|---|
| GaussDB | PostgreSQL-only slice |
| UI / H5 / join-AST / FieldPolicy export | Unchanged from [g1-isolation-surfaces.md](./g1-isolation-surfaces.md) |
| G4 same-request chain | Login / permission / admin / export / guard-reject on one browser request |
| Target Linux/ARM Profile Redis 3.2 + MinIO/AIStor | These drills are optional and local; they are not the G14 component-matrix close |
