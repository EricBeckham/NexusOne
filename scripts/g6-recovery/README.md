# G6 recovery drill (ops only)

Environment-level restore on a **dedicated** PostgreSQL 15 cluster and a **local** MinIO. This is not product runtime, not a single-tenant logical restore, and not G17.

## What it does

1. Starts (or reuses) a dedicated PostgreSQL 15 cluster on `127.0.0.1:55432` — never port `5432`, never `haopan_platform`.
2. Creates `nexusone_g6_src`, seeds two tenants, takes `pg_dump --format=custom`, restores into empty `nexusone_g6_dst`.
3. Starts (or reuses) local MinIO on `127.0.0.1:9100` (P0 community tag `RELEASE.2025-09-07T16-13-09Z`).
4. Uploads tenant-prefixed objects, `mc mirror` to a local backup directory, restores into isolated bucket `nexusone-g6-dst/restore-YYYYMMDD`.
5. Compares table COPY SHA-256 values, object SHA-256 values, and writes pass/fail under `.local/g6-recovery/evidence/` (gitignored).

## Prerequisites

- PostgreSQL 15 binaries (`pg_dump`, `pg_restore`, `initdb`, `pg_ctl`) at `/usr/lib/postgresql/15/bin` or `G6_PG15_BIN`.
- MinIO and `mc` binaries. Default paths: `$HOME/.local/g6-recovery/bin/{minio,mc}`.
- No Docker. Credentials only via `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` or a generated file in the workdir (`0600`).

```bash
./scripts/g6-recovery/run-drill.sh
```

Do not point this script at a product database. It refuses `haopan_platform`, refuses port `5432`, and refuses identical source/restore names.

## Out of scope

- Minute-level RPO (WAL archive / replica / delivery-grade HA)
- GaussDB, AIStor enterprise, target Linux/ARM Profile
- G17 (classified / GM TLS / 三权分立)
- Single-tenant logical restore (V1.1 explicitly excluded)
- Changing `RecoveryPort` (product dry-run stays dry-run)
