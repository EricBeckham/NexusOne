# G10 leftover — file reconcile honesty (live MinIO / preview vs apply / tenant prefix)

S0+S1 remainder after the file half ([g10-file-reconcile.md](./g10-file-reconcile.md)). Rebased onto `origin/main` `808e31e` (after #157 Linux/ARM Profile honesty leftover). **G18 stays closed.** This slice does **not** redo identity drift ([g10-identity-drift-dry-run.md](./g10-identity-drift-dry-run.md) / #141), does **not** redo G6 object restore, and does **not** claim product G10 close.

Keep both leftover rows in [docs/README.md](./README.md): `g10-identity-drift-dry-run` (#141) and this file.

The file half already matches `files.file_object` rows to object keys under the tenant prefix and can `POST {"apply":true}` to repair. Counting / in-memory storage already proves preview vs apply and tenant-prefix guards. The leftover here is the **honesty harness**: live MinIO / object-store is **PASS** only when the endpoint is up **and** a preview + apply + tenant-prefix walk was observed. Missing `S3_*` or a down listener is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| Counting preview vs apply | `FileObjectReconciler` dry-run leaves `UPLOADED` + missing bytes; apply is `MARK_MISSING` only. Bytes are never invented. |
| Tenant-prefix guard | Tenant A apply lists only `tenants/{A}/`. Tenant B orphan is not deleted. Foreign key stays `CONFLICT`. |
| Live MinIO inventory | Probe `S3_ENDPOINT` + loopback `:19000` / `:9000`. Credentials stay in env / SecretPort. |
| Honest SKIP | Unset `S3_*`, down endpoint, or no observed walk stays `SKIP` with a reason. |
| Honesty gate | `--verify-report` rejects `live_minio_reconcile=PASS` without present + observed preview/apply/prefix, and rejects `g10_close=CLOSED` / #141 redo / G6 restore redo. |

`path` remains `files.reconcile`. Client `X-Tenant-Id` is still rejected by product IAM. No Docker.

## How to run

```bash
# Always-green counting + live probe (honest SKIP when MinIO is down)
./scripts/g10-file-reconcile-honesty.sh

# Honesty gate only
./scripts/g10-file-reconcile-honesty.sh --verify-report \
  scripts/g10-file-reconcile-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-file-reconcile-honesty.sh --verify-report \
  scripts/g10-file-reconcile-honesty/fixtures/fake-live-minio-pass.env
./scripts/g10-file-reconcile-honesty.sh --verify-report \
  scripts/g10-file-reconcile-honesty/fixtures/fake-g10-close.env
```

Equivalent tests:

```bash
mvn -pl nexus-file-core,nexus-bootstrap -am test \
  -Dtest=G10FileReconcileHonestyTest,FileObjectReconcileTest,FileReconcileApiTest,LiveMinioFileReconcileOptionalTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The existing file-half script is unchanged:

```bash
./nexus-file-core/scripts/run-g10-file-reconcile.sh
```

If live MinIO is later listening on dedicated ports (native process, not Docker):

```bash
export S3_ENDPOINT=http://127.0.0.1:19000
export S3_ACCESS_KEY=...          # env / Secret only
export S3_SECRET_KEY=...
export S3_TEST_BUCKET=nexus-isolation-drill
./scripts/g10-file-reconcile-honesty.sh
```

`PASS` requires all of: reachable object store, credentials, observed preview (`applied=false`), observed apply, and tenant-prefix guard on the live walk. This leftover does not start MinIO.

See [engineering/g10-file-reconcile-honesty-EVIDENCE.md](./engineering/g10-file-reconcile-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g10-file-reconcile-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G10 close | Explicitly `g10_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Identity drift-fix dry-run | Sibling leftover [g10-identity-drift-dry-run.md](./g10-identity-drift-dry-run.md) (#141). `identity_drift_141_redone=false`. |
| G6 object restore | Missing objects are marked `MISSING`. Bytes are not reconstructed. |
| Live MinIO on this agent VM | `S3_*` unset; nothing on `:19000` / `:9000`. Honesty SKIP. |
| SAML / LDAP / SMS MFA | Identity leftovers. Not this slice. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Docker / Compose | Forbidden. Native MinIO only when the operator already started it. |
