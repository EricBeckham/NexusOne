# nexus-file-core evidence

## G10 file half (this PR)

Metadata ↔ object-store match + safe repair on M0-10 File Service. Identity/Keycloak reconcile is not in this evidence. G1 reject-before-execute and G6 restore are not claimed.

Recorded after the local Maven run. Secrets stay in the environment; none committed.

### Default CountingObjectStorage

```bash
mvn -pl nexus-file-core,nexus-bootstrap -am test \
  -Dtest=FileObjectReconcileTest,FileReconcileApiTest,FileReconcileTaskHandlerTest,FileServiceIsolationTest,FileApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Results will be appended after the run.

### Live MinIO (optional, dedicated ports)

```bash
export S3_ENDPOINT=http://127.0.0.1:19000
# S3_ACCESS_KEY / S3_SECRET_KEY from env / SecretPort only
./nexus-file-core/scripts/run-g10-file-reconcile.sh
```

Skip when `S3_*` is unset or the endpoint is down.

## Honest gaps

- Not G6 object restore (missing objects are marked `MISSING`; bytes are not reconstructed).
- Not a G1 reject-before-execute redo.
- G18 stays closed.
- Live MinIO is opt-in; CI always uses Fake/counting storage.
- GaussDB / AIStor licensed digest / target Linux/ARM Profile: not claimed.
