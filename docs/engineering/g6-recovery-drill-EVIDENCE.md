# G6 recovery drill evidence

Recorded **2026-09-22T15:27:57Z** on the agent VM against `main` `34a45cb` + this PR. Operator: cloud agent. Script: [`scripts/g6-recovery/run-drill.sh`](../../scripts/g6-recovery/run-drill.sh).

| Gate | Result |
|---|---|
| PostgreSQL **full-database** restore on a **dedicated** cluster/database | **PASS** |
| Local **MinIO** object restore to an isolated bucket/prefix | **PASS** |
| `haopan_platform` touched | **No** (name refused; dedicated cluster has no such database) |
| Single-tenant logical restore | **Not performed in this dual-tenant run** (both tenants restored). Leftover later: [g6-single-tenant-logical-restore-EVIDENCE.md](./g6-single-tenant-logical-restore-EVIDENCE.md) |
| G17 claimed | **No** |
| Minute-level RPO / delivery-grade HA claimed | **No** |
| Target Linux/ARM Profile claimed | **No** |
| Product runtime / `RecoveryPort` changed | **No** (still dry-run) |

This is the S1 **environment-level** restore drill asked by 需求规格 V1.1 §12 G6 / CORE-BKP-004 / CORE-BKP-006. It is **not** a wholesale G6 close if the org still requires minute-level RPO against a delivery-grade deployment checklist.

## Environment

| Item | Value |
|---|---|
| OS | Ubuntu 24.04.4 LTS, `Linux cursor 6.12.94+ x86_64` |
| JDK (host only; app not started) | OpenJDK `21.0.10` |
| PostgreSQL | **15.19** (`Ubuntu 15.19-1.pgdg24.04+2`) user-space cluster |
| `pg_dump` / `pg_restore` | **15.19** (`/usr/lib/postgresql/15/bin`) |
| Dedicated listen | `127.0.0.1:55432` (port **5432** refused by the script and was not accepting connections) |
| Source DB | `nexusone_g6_src` |
| Restore DB | `nexusone_g6_dst` (empty `TEMPLATE template0` before restore) |
| MinIO | community `RELEASE.2025-09-07T16-13-09Z` (`commit-id=07c3a429bfed433e49018cb0f78a52145d4bedeb`, AGPLv3) |
| MinIO binary SHA-256 | `7c5bd8512c6e966455b1d198209358b2d191c77a83ab377c4073281065fb855f` (matches GitHub release checksum) |
| `mc` | `RELEASE.2025-08-13T08-35-41Z` |
| MinIO API | `127.0.0.1:9100` |
| Source bucket | `nexusone-g6-src` |
| Restore target | `nexusone-g6-dst/restore-20260922/` |
| Docker | not used |
| Workdir | `.local/g6-recovery/` (gitignored; dumps and secrets stay off Git) |

Databases present on the dedicated cluster after the drill: `postgres`, `template0`, `template1`, `nexusone_g6_src`, `nexusone_g6_dst`.  
`SELECT datname FROM pg_database WHERE datname ILIKE '%haopan%'` → empty.

## Commands (sanitized)

Credentials were local-trust for PostgreSQL and a generated MinIO secret in `.local/g6-recovery/minio.secret` (`0600`). Neither is committed.

```bash
./scripts/g6-recovery/run-drill.sh
```

Equivalent core steps the script executed:

```bash
export PATH="/usr/lib/postgresql/15/bin:$PATH"
export PGHOST=127.0.0.1 PGPORT=55432
# dedicated cluster via initdb + pg_ctl; not the package default cluster; not :5432

psql -d postgres -c "CREATE DATABASE nexusone_g6_src TEMPLATE template0 ENCODING 'UTF8';"
psql -d nexusone_g6_src -f scripts/g6-recovery/seed-source.sql

pg_dump --format=custom --file=.local/g6-recovery/backup/nexusone_g6_src.dump \
  --dbname=nexusone_g6_src
sha256sum .local/g6-recovery/backup/nexusone_g6_src.dump

psql -d postgres -c "CREATE DATABASE nexusone_g6_dst TEMPLATE template0 ENCODING 'UTF8';"
pg_restore --dbname=nexusone_g6_dst --exit-on-error \
  .local/g6-recovery/backup/nexusone_g6_src.dump

mc alias set g6local http://127.0.0.1:9100 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mirror --overwrite g6local/nexusone-g6-src .local/g6-recovery/object-backup/nexusone-g6-src
mc mirror --overwrite .local/g6-recovery/object-backup/nexusone-g6-src \
  g6local/nexusone-g6-dst/restore-20260922
```

Guard probes (must fail):

```text
G6_SRC_DB=haopan_platform ./scripts/g6-recovery/run-drill.sh
→ FAIL: refusing database name that mentions haopan: haopan_platform

G6_PGPORT=5432 ./scripts/g6-recovery/run-drill.sh
→ FAIL: refusing default product port 5432; G6 drill uses a dedicated cluster (default 55432)
```

## PostgreSQL full-database restore — PASS

Custom dump (this run): **7162** bytes, SHA-256

`467bef2eb3d8a5f17cdaaff7cef899f0bbd4f50574880d4338c6649b9deb9778`

Table content is compared by `COPY (SELECT * FROM … ORDER BY …) TO STDOUT WITH CSV | sha256sum`. Source and restore hashes matched for every table.

| Table | Rows | Source SHA-256 | Restore SHA-256 | Result |
|---|---:|---|---|---|
| `platform.audit_record` | 2 | `6d7b39bca1f72f4489a54435edb24e07dd8a69f9797ccf216a3f8d17d39377a2` | same | PASS |
| `platform.audit_chain_meta` | 1 | `cd4e5487b268316afc54a55c6988a12ad0814064eafbc8f0d7aca14f31a3b171` | same | PASS |
| `files.file_object` | 2 | `09116f08de6525f574cfe8fe09983a3cd5e9231e515e771b0e5ab0773bcaeee5` | same | PASS |
| `ledger.sample_row` | 2 | `a22324d1adab6edc1d61c43b8ce2b48d3f593a9b6c693f91a0afac7d15b144b0` | same | PASS |
| `platform.flyway_schema_history` | 3 | `b432db900c1831f186bcb94124ca19e0edc9a1ed48f685b8536faef9e1a64446` | same | PASS |

Restored `tenant_id` values (both required):

- `11111111-1111-4111-8111-111111111111` (`leave-tenant-a`, 3.00)
- `22222222-2222-4222-8222-222222222222` (`expense-tenant-b`, 42.50)

Restored Flyway-shaped history: `001` create logical schemas, `501` audit hash chain, `510` file object. Schemas present: `platform`, `files`, `ledger`.

Timing for this fixture (not a production data volume): `pg_dump` **42 ms**, `pg_restore` **42 ms**.

## MinIO object restore — PASS

Three objects (two tenants + platform marker) were mirrored from `nexusone-g6-src` into isolated `nexusone-g6-dst/restore-20260922/`. Bytes compared with SHA-256 of `mc cat`.

| Object key | Bytes | Source SHA-256 | Restored SHA-256 | Result |
|---|---:|---|---|---|
| `tenants/11111111-1111-4111-8111-111111111111/leave/2026/09/file-a.bin` | 29 | `4924dc838f91973799e2d7cce37acb2e40e2488915581d0f9823517c4964b690` | same | PASS |
| `tenants/22222222-2222-4222-8222-222222222222/expense/2026/09/file-b.bin` | 31 | `1cd28fe425d2da5ad14a6ba63fe539f6ece76dbc00ecc2d774324675d069d4cd` | same | PASS |
| `platform/runtime/g6-marker.txt` | 34 | `a7d0189418818921c5e51470bfd445a7d928fcc2ded7b86d288931c1c8cfbce3` | same | PASS |

Object-key sets were identical (count 3). Backup **32 ms**, restore **30 ms**. End-to-end script wall (including CREATE DATABASE / seed / checks): **760 ms**.

## Explicitly out of scope

- **`haopan_platform`** and any product database on `:5432` (`nexusone`, `nexus_g18_loop`, etc.).
- **Single-tenant logical restore as a V1.1 product capability** (tenant export/import is still migration only). The ops leftover drill is [g6-single-tenant-logical-restore-EVIDENCE.md](./g6-single-tenant-logical-restore-EVIDENCE.md); this file remains the dual-tenant whole-DB + MinIO record.
- **Minute-level RPO** (WAL archive, streaming replica, delivery-grade HA). This drill is a logical dump + object mirror. Observed restore time is a fixture RTO, not a sales SLA.
- **G17** (三权分立, 国密 TLS 双证书, qualified GM/T provider, 密码机).
- **G4** audit coverage / 90+365 retention.
- **GaussDB**, **AIStor enterprise** image/license, **Keycloak/Flowable/MinIO upgrade-rollback**.
- **Target Linux/ARM / offline-package / SBOM Profile**.
- Product `RecoveryPort.dryRunPostgres()` / `dryRunObjectStore()` still do **not** execute dump or restore; `profileAccepted` remains false.
- P0/M0 product modules were not re-run or rewritten.

## Verdict

**PASS** for the G6 S1 environment-level drill: dedicated PostgreSQL whole-database restore + local MinIO object restore, with commands, checksums, and pass/fail recorded. **Do not merge as a G17 close. Do not treat this as minute-level RPO acceptance.**
