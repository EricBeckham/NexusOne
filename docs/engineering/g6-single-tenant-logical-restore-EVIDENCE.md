# G6 leftover: single-tenant logical restore evidence

Recorded **2026-09-22T17:10:52Z** on the agent VM against `main` `d7abf34` + this PR. Operator: cloud agent. Script: [`scripts/g6-recovery/run-single-tenant.sh`](../../scripts/g6-recovery/run-single-tenant.sh).

This is the leftover marked **Not performed** on the dual-tenant whole-DB drill ([g6-recovery-drill-EVIDENCE.md](./g6-recovery-drill-EVIDENCE.md)). It restores **one** tenant's business rows into a dedicated empty database. It is **not** a V1.1 product single-tenant restore capability, **not** a `RecoveryPort` change, **not** minute-level RPO, **not** G17, and **not** a G18 reopen.

| Gate | Result |
|---|---|
| Single-tenant logical restore into a dedicated empty DB | **PASS** |
| Only one `tenant_id` in dest business tables | **PASS** (`11111111-1111-4111-8111-111111111111`) |
| Other tenant absent from dest | **PASS** (`22222222-2222-4222-8222-222222222222` rows = 0) |
| Source still holds both tenants | **PASS** |
| Dual-tenant whole-DB restore still available | **Yes** (`scripts/g6-recovery/run-drill.sh`; `pg_dump -Fc` → `nexusone_g6_dst` still has both tenants) |
| `haopan_platform` touched | **No** (name refused; dedicated cluster has no such database) |
| G17 claimed | **No** |
| Minute-level RPO / delivery-grade HA claimed | **No** |
| Target Linux/ARM Profile claimed | **No** |
| Product runtime / `RecoveryPort` changed | **No** (still dry-run) |

## Environment

| Item | Value |
|---|---|
| OS | Ubuntu 24.04.4 LTS, `Linux cursor 6.12.94+ x86_64` |
| JDK (host only; app not started) | OpenJDK `21.0.10` |
| PostgreSQL | **15.19** (`Ubuntu 15.19-1.pgdg24.04+2`) user-space cluster |
| `pg_dump` / `psql` | **15.19** (`/usr/lib/postgresql/15/bin`) |
| Dedicated listen | `127.0.0.1:55432` (port **5432** refused by the script; package `15/main` cluster was **down**) |
| Source DB | `nexusone_g6_src` (dual-tenant fixture) |
| Single-tenant dest | `nexusone_g6_one` (empty `TEMPLATE template0` before schema apply) |
| Restored tenant | `11111111-1111-4111-8111-111111111111` (`leave-tenant-a`, 3.00) |
| Excluded tenant | `22222222-2222-4222-8222-222222222222` (`expense-tenant-b`, 42.50) |
| Docker | not used |
| Workdir | `.local/g6-recovery/` (gitignored; dumps and secrets stay off Git) |

Databases present on the dedicated cluster after this leftover + a dual-tenant `pg_dump -Fc` smoke into `nexusone_g6_dst`: `postgres`, `template0`, `template1`, `nexusone_g6_src`, `nexusone_g6_one`, `nexusone_g6_dst`.  
`SELECT datname FROM pg_database WHERE datname ILIKE '%haopan%'` → empty.

## Commands (sanitized)

Local-trust PostgreSQL on the dedicated cluster. No MinIO secret is required for this leftover. Nothing from `.local/g6-recovery/` is committed.

```bash
./scripts/g6-recovery/run-single-tenant.sh
```

Equivalent core steps the script executed:

```bash
export PATH="/usr/lib/postgresql/15/bin:$PATH"
export PGHOST=127.0.0.1 PGPORT=55432
# dedicated cluster via initdb + pg_ctl; not the package default cluster; not :5432

psql -d postgres -c "CREATE DATABASE nexusone_g6_src TEMPLATE template0 ENCODING 'UTF8';"
psql -d nexusone_g6_src -f scripts/g6-recovery/seed-source.sql

pg_dump --schema-only --no-owner --no-privileges \
  --file=.local/g6-recovery/backup/nexusone_g6_src.schema.sql \
  --dbname=nexusone_g6_src

psql -d postgres -c "CREATE DATABASE nexusone_g6_one TEMPLATE template0 ENCODING 'UTF8';"
psql -d nexusone_g6_one -f .local/g6-recovery/backup/nexusone_g6_src.schema.sql

# one tenant only
psql -d nexusone_g6_src -c \
  "COPY (SELECT * FROM ledger.sample_row WHERE tenant_id = '11111111-1111-4111-8111-111111111111'::uuid) TO STDOUT" \
  | psql -d nexusone_g6_one -c "COPY ledger.sample_row FROM STDIN"
# same COPY for platform.audit_record and files.file_object
# flyway_schema_history copied as schema metadata
# audit_chain_meta rebuilt from the restored tenant's last audit row (head_index=1, hash-1)
```

Guard probes (must fail):

```text
G6_SRC_DB=haopan_platform ./scripts/g6-recovery/run-single-tenant.sh
→ FAIL: refusing database name that mentions haopan: haopan_platform

G6_ONE_DB=haopan_restore ./scripts/g6-recovery/run-single-tenant.sh
→ FAIL: refusing database name that mentions haopan: haopan_restore

G6_PGPORT=5432 ./scripts/g6-recovery/run-single-tenant.sh
→ FAIL: refusing default product port 5432; G6 drill uses a dedicated cluster (default 55432)

G6_TENANT_ID=not-a-uuid ./scripts/g6-recovery/run-single-tenant.sh
→ FAIL: refusing G6_TENANT_ID: not a UUID: not-a-uuid
```

Dual-tenant whole-DB script is unchanged as an operator path and still refuses the same names/port:

```text
G6_SRC_DB=haopan_platform ./scripts/g6-recovery/run-drill.sh
→ FAIL: refusing database name that mentions haopan: haopan_platform

G6_PGPORT=5432 ./scripts/g6-recovery/run-drill.sh
→ FAIL: refusing default product port 5432; G6 drill uses a dedicated cluster (default 55432)
```

## Single-tenant table hashes — PASS (one tenant only)

Table content is compared by `COPY (SELECT * FROM … WHERE tenant_id = '<uuid>' ORDER BY …) TO STDOUT WITH CSV | sha256sum` on the source, versus `COPY (SELECT * FROM … ORDER BY …)` on the dest (dest must only contain that tenant). Source and restore hashes matched.

| Table | Rows | Source SHA-256 (tenant filter) | Restore SHA-256 (whole dest table) | Result |
|---|---:|---|---|---|
| `platform.audit_record` | 1 | `86e307ea155a98b9c2fb23069bde1eafa4285fdb8a0e7f23fff215eaaf5be56c` | same | PASS |
| `files.file_object` | 1 | `6ae39274fe0c4a34c778f4370b8968bf91f3713bb8933e19d327fb56b5e3f4e3` | same | PASS |
| `ledger.sample_row` | 1 | `5eae0aa6ec6f7eaa867f8a80546fa80d9b6876f1a044fa0af495d1fb6ef8de81` | same | PASS |
| `platform.flyway_schema_history` (schema metadata, not tenant-scoped) | 3 | `b432db900c1831f186bcb94124ca19e0edc9a1ed48f685b8536faef9e1a64446` | same | PASS |

Restored `tenant_id` values (exactly one required):

- `11111111-1111-4111-8111-111111111111` (`leave-tenant-a`, 3.00)

Absent from dest (required):

- `22222222-2222-4222-8222-222222222222` (`expense-tenant-b`)

Source `ledger.sample_row` still has both tenants after the leftover. Dest `platform.audit_chain_meta` is reconstructed for the restored tenant only (`default` / `head_index=1` / `hash-1`) and is **not** compared to the dual-tenant source head.

Timing for this fixture (not a production data volume): schema `pg_dump` **44 ms**, tenant `COPY` **55 ms**. End-to-end script wall **627 ms**.

## Dual-tenant full restore still available

After the leftover, `pg_dump --format=custom` of `nexusone_g6_src` restored into empty `nexusone_g6_dst` with `--exit-on-error`. Dest `ledger.sample_row` contained **both** tenants. Operator path remains [`run-drill.sh`](../../scripts/g6-recovery/run-drill.sh) (whole DB + MinIO). This leftover does not replace that path.

## Explicitly out of scope

- **`haopan_platform`** and any product database on `:5432`.
- **V1.1 product single-tenant logical restore.** Tenant export/import stays migration-only. This file records an ops leftover drill only.
- **Minute-level RPO** (WAL archive, streaming replica, delivery-grade HA).
- **G17** (三权分立, 国密 TLS 双证书, qualified GM/T provider, 密码机).
- **Target Linux/ARM / offline-package / SBOM Profile**.
- Product `RecoveryPort.dryRunPostgres()` / `dryRunObjectStore()` still do **not** execute dump or restore; `profileAccepted` remains false.
- P0/M0 product modules were not re-run or rewritten. G18 stays closed.

## Verdict

**PASS** for the G6 leftover single-tenant logical restore drill: one tenant's business rows into a dedicated empty PostgreSQL database, with table SHA-256, row counts, and the other tenant absent. Dual-tenant whole-database restore remains available. **Do not merge as a G17 close. Do not treat this as minute-level RPO or a product RecoveryPort change.**
