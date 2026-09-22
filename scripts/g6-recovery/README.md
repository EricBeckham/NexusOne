# G6 recovery drill (ops only)

Environment-level restore on a **dedicated** PostgreSQL 15 cluster and a **local** MinIO. This is not product runtime and not G17.

Two operator paths:

| Script | What it restores |
|---|---|
| [`run-drill.sh`](./run-drill.sh) | Dual-tenant **whole database** + MinIO bucket into `nexusone_g6_dst` |
| [`run-single-tenant.sh`](./run-single-tenant.sh) | **One** tenant's business rows into empty `nexusone_g6_one` |

The leftover script does **not** claim a V1.1 product single-tenant restore capability (tenant export/import stays migration-only). It does **not** change `RecoveryPort`.

## Dual-tenant whole-database + objects

1. Starts (or reuses) a dedicated PostgreSQL 15 cluster on `127.0.0.1:55432` — never port `5432`, never `haopan_platform`.
2. Creates `nexusone_g6_src`, seeds two tenants, takes `pg_dump --format=custom`, restores into empty `nexusone_g6_dst`.
3. Starts (or reuses) local MinIO on `127.0.0.1:9100` (P0 community tag `RELEASE.2025-09-07T16-13-09Z`).
4. Uploads tenant-prefixed objects, `mc mirror` to a local backup directory, restores into isolated bucket `nexusone-g6-dst/restore-YYYYMMDD`.
5. Compares table COPY SHA-256 values, object SHA-256 values, and writes pass/fail under `.local/g6-recovery/evidence/` (gitignored).

```bash
./scripts/g6-recovery/run-drill.sh
```

## Single-tenant leftover (business rows only)

1. Same dedicated cluster and dual-tenant source fixture.
2. `pg_dump --schema-only` into empty `nexusone_g6_one` (`TEMPLATE template0`).
3. `COPY` only `G6_TENANT_ID` (default `11111111-1111-4111-8111-111111111111`) from `platform.audit_record`, `files.file_object`, and `ledger.sample_row`.
4. Copies `platform.flyway_schema_history` as schema metadata. Rebuilds `platform.audit_chain_meta` from the restored tenant's last audit row (does **not** copy the dual-tenant chain head).
5. Fails if the other tenant appears in the dest, or if the source no longer holds both tenants.

```bash
./scripts/g6-recovery/run-single-tenant.sh
G6_TENANT_ID=22222222-2222-4222-8222-222222222222 ./scripts/g6-recovery/run-single-tenant.sh
```

## Prerequisites

- PostgreSQL 15 binaries (`pg_dump`, `pg_restore`, `initdb`, `pg_ctl`) at `/usr/lib/postgresql/15/bin` or `G6_PG15_BIN`.
- Dual-tenant + MinIO path also needs MinIO and `mc`. Default paths: `$HOME/.local/g6-recovery/bin/{minio,mc}`.
- No Docker. Credentials only via `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` or a generated file in the workdir (`0600`).

Do not point these scripts at a product database. They refuse `haopan_platform`, refuse port `5432`, and refuse identical source/restore names.

## Out of scope

- Minute-level RPO (WAL archive / replica / delivery-grade HA)
- GaussDB, AIStor enterprise, target Linux/ARM Profile
- G17 (classified / GM TLS / 三权分立)
- Changing `RecoveryPort` (product dry-run stays dry-run). Honesty / mutation contract leftover: [docs/g6-recovery-port-dry-run.md](../../docs/g6-recovery-port-dry-run.md). Still-dry-run honesty leftover (#135 is not product HA / minute RPO / delivery HA): [docs/g6-recovery-port-honesty.md](../../docs/g6-recovery-port-honesty.md). Follow-on honest PASS/SKIP leftover (does not claim product G6 / RecoveryPort / HA close): [docs/g6-recovery-port-dry-run-honesty.md](../../docs/g6-recovery-port-dry-run-honesty.md). Follow-on remainder honesty leftover (does not redo #135/#164/#186): [docs/g6-recovery-port-dry-run-remainder-honesty.md](../../docs/g6-recovery-port-dry-run-remainder-honesty.md)
- Claiming V1.1 now ships product single-tenant logical restore
