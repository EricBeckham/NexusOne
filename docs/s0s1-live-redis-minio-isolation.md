# S0+S1 live Redis + MinIO isolation evidence

Ops drill for `CacheKeyGuard` / `FileObjectKeyGuard` against live Redis and MinIO when those processes can be started on **dedicated** ports. Not a P0/M0 redo. G18 stays closed. `AuditPort` same-TX `append` is not modified.

Runbook: [engineering/runbooks/live-redis-minio-isolation.md](./engineering/runbooks/live-redis-minio-isolation.md).

## Pending this PR's drill run

The first commit on this branch adds the script, optional live tests, and fixtures. Numbers below are filled after `./ops/scripts/live-redis-minio-isolation-drill.sh` on this agent VM.

| Slice | Expected |
|---|---|
| Fixtures `CachePortIsolationTest` / `FileServiceIsolationTest` / `FileApiTest` | Must `PASS` even with no live backends |
| Live Redis `:16379` (or next free dedicated port) | `PASS` with `P1.M02.014` + unchanged command count, or `SKIP` with reason |
| Live MinIO `:19000` (or next free dedicated port) | `PASS` with `P1.M02.015` + unchanged put/presign/delete, or `SKIP` with reason |
| Product `:5432` / `:6379` | Must not be bound by this drill |

## Command

```bash
./ops/scripts/live-redis-minio-isolation-drill.sh
```
