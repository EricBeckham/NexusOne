# nexus-file-core evidence

## G10 file half (this PR)

Metadata ↔ object-store match + safe repair on M0-10 File Service. Identity/Keycloak reconcile is not in this evidence. G1 reject-before-execute and G6 restore are not claimed.

Recorded **2026-09-22T16:49:54Z** on this agent VM. OpenJDK **21.0.10**, Apache Maven **3.9.11** (unpacked under `/tmp` for this run; not on PATH at boot), Spring Boot **4.1.1**. No Docker. Secrets stayed in the environment; none committed.

### Default CountingObjectStorage

```bash
mvn -pl nexus-file-core,nexus-bootstrap,nexus-common -am test \
  -Dtest=FileObjectReconcileTest,FileReconcileApiTest,FileReconcileTaskHandlerTest,FileServiceIsolationTest,FileApiTest,FileSecretPortWiringTest,ErrorCodeTest,LiveMinioFileReconcileOptionalTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`BUILD SUCCESS` **2026-09-22T16:49:54Z**.

| Suite | Result | What it proved |
|---|---|---|
| `FileObjectReconcileTest` | **7/7** | match; dry-run leaves missing; apply `MARK_MISSING`; tenant-scoped orphan delete; tenant B cannot see/delete A; leftover delete; foreign key is `CONFLICT` (no delete); platform apply repairs known tenants and leaves unknown prefix |
| `FileReconcileApiTest` | **3/3** | tenant preview + apply; tenant B apply leaves A orphan; internal path 401 without bootstrap token |
| `FileReconcileTaskHandlerTest` | **2/2** | Job payload `{"apply":true}` marks missing; missing context rejected |
| `FileServiceIsolationTest` | **5/5** | unchanged G1 reject-before-S3 (not re-claimed) |
| `FileApiTest` | **1/1** | unchanged signed-url `P1.M02.015` |
| `FileSecretPortWiringTest` | **4/4** | S3 credentials still only via SecretPort |
| `ErrorCodeTest` | **3/3** | `CORE.FILE.0005` (`FILE_MISSING`) |
| `LiveMinioFileReconcileOptionalTest` | **1 skipped** | `S3_ENDPOINT` unset; `127.0.0.1:19000` and `:9000` not listening |

`nexus-file-core` selected tests: **Tests run: 21, Failures: 0, Errors: 0, Skipped: 1**. `nexus-bootstrap` handler: **2/2**.

### Live MinIO (optional, dedicated ports)

```bash
export S3_ENDPOINT=http://127.0.0.1:19000
# S3_ACCESS_KEY / S3_SECRET_KEY from env / SecretPort only
./nexus-file-core/scripts/run-g10-file-reconcile.sh
```

This VM had no native MinIO listener on `:19000` or `:9000`, and `S3_*` was unset. Live slice recorded as **SKIP**. Fake/counting storage still passed.

## G10 leftover honesty (this PR)

Always-green counting preview vs apply + tenant-prefix guards. Live MinIO / object-store is honest **SKIP** when `S3_*` is unset or the endpoint is down. Does **not** claim product G10 close. Does **not** redo identity drift (#141) or G6 object restore.

```bash
./scripts/g10-file-reconcile-honesty.sh
```

See [docs/g10-file-reconcile-honesty.md](../docs/g10-file-reconcile-honesty.md). Keep both leftover rows: identity drift (#141) and this file leftover.

## Honest gaps

- Not G6 object restore (missing objects are marked `MISSING`; bytes are not reconstructed).
- Not a G1 reject-before-execute redo (`FileServiceIsolationTest` / `FileApiTest` only confirm those suites still pass).
- G18 stays closed.
- Live MinIO was not reachable on this VM.
- GaussDB / AIStor licensed digest / target Linux/ARM Profile: not claimed.
- Product `nexus-bootstrap` packaged JAR was not the process under test for the HTTP slice (`SpringBootTest` + MockMvc).
