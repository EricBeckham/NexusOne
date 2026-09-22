# G10 file half: metadata ↔ object store

S0+S1 gate **G10 身份与文件对账** is split. The identity half (P2 persons ↔ Keycloak) is already on `main` ([g10-identity-reconcile.md](./g10-identity-reconcile.md), #76). This package is the **file half only**: `files.file_object` rows match object keys under the tenant prefix, and safe inconsistencies can be repaired.

Reuse: M0-10 File Service, `ObjectStoragePort`, `SecretPort` (S3 keys). Boot **4.1.1**, Java **21**. No Docker. G18 stays closed. This is **not** a G1 reject-before-execute redo and **not** G6 object restore.

## Path

| Call | Who | Effect |
|---|---|---|
| `GET /api/v1/internal/files/reconcile` | `X-Nexus-Bootstrap-Token` + `PlatformContext` `files.reconcile` | Platform preview (match, no writes) |
| `POST /api/v1/internal/files/reconcile` `{"apply":true}` | bootstrap token | Platform repair (known tenant prefixes only) |
| `GET /api/v1/files/reconcile` | bound `TenantContext` | Current-tenant preview |
| `POST /api/v1/files/reconcile` `{"apply":true}` | bound `TenantContext` | Current-tenant repair |

`path` on the report is `files.reconcile`. Listing and deletes always go through `FileObjectKeyGuard` on `tenants/{tenantId}/` (or `platform/` for platform). Client `X-Tenant-Id` is still rejected by product IAM.

Job: `FileReconcileTaskHandler` (`files.reconcile`) on the existing IsolatedQuartzJob / TaskQueue restore path, plus `FileReconcileRunner` for platform. Optional cron `nexus.files.reconcile.schedule-enabled=true` is preview-only.

## Findings and safe repair

Metadata is the file identity source. Bytes are never invented.

| Finding | Meaning | Apply |
|---|---|---|
| `MATCHED` | uploaded+object, missing+no object, or deleted+no object | none |
| `MISSING` | `UPLOADED` row, no object under the owned prefix | `MARK_MISSING` — status only |
| `ORPHAN` | object under a **known** tenant prefix, no row | `DELETE_ORPHAN` after `assertOwned` |
| `ORPHAN` (unknown tenant prefix) | object under `tenants/{other}/` with no metadata for that tenant | report only |
| `LEFTOVER` | `DELETED` row still has an object | `DELETE_LEFTOVER` after `assertOwned` |
| `DRIFT` | `MISSING` row, object present again | `MARK_UPLOADED` — status only |
| `CONFLICT` | key outside the file's tenant prefix / bound context | no write |

Tenant B apply lists only `tenants/{B}/`. It cannot delete or mark tenant A objects.

## Exclusions (this half)

- G1 reject-before-execute (already on `main`; not re-claimed)
- G6 whole-bucket / object restore
- G18 business path (closed)
- Identity / Keycloak reconcile (other half)
- AIStor licensed digest / GaussDB
- Docker / Compose as the app or MinIO run path

## Evidence

Default (CountingObjectStorage / in-memory metadata, no MinIO process):

```bash
mvn -pl nexus-file-core,nexus-bootstrap -am test \
  -Dtest=FileObjectReconcileTest,FileReconcileApiTest,FileReconcileTaskHandlerTest,FileServiceIsolationTest,FileApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Live MinIO on **dedicated** ports (opt-in, native process, not Docker), same style as #79:

```bash
export S3_ENDPOINT=http://127.0.0.1:19000
export S3_ACCESS_KEY=...          # env / Secret only
export S3_SECRET_KEY=...
export S3_TEST_BUCKET=nexus-isolation-drill
./nexus-file-core/scripts/run-g10-file-reconcile.sh
```

The live suite **skips** when `S3_*` is unset or the endpoint is down. Fake/counting storage always proves in CI.

Command log and honest gaps: `nexus-file-core/EVIDENCE.md`.

Leftover honesty (live MinIO PASS/SKIP, preview vs apply, tenant-prefix guards; does **not** claim product G10 close): [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md).

Recorded **2026-09-22T16:49:54Z**: Counting / memory `mvn -pl nexus-file-core,nexus-bootstrap,nexus-common -am test` (selected suites) → file-core **21 run / 0 fail / 1 skip**, bootstrap handler **2/2**. Live `LiveMinioFileReconcileOptionalTest` skipped (`S3_ENDPOINT` unset; nothing on `:19000` / `:9000`). Java **21.0.10**, Maven **3.9.11**, Boot **4.1.1**. No Docker.
