# G10 file reconcile honesty leftover — evidence

Recorded on this branch after rebase onto `origin/main` (`5496a6c`, includes #163 H5 every-page leftover, #164 RecoveryPort honesty leftover, and #161 live multi-JVM leftover). **Not** a product G10 close. **Not** identity drift (#141). **Not** G6 object restore. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md). Keep both leftover rows: [g10-identity-drift-dry-run.md](../g10-identity-drift-dry-run.md) and this leftover.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| MinIO | not listening on `127.0.0.1:19000` / `:9000` | `S3_*` unset |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g10-file-reconcile-honesty.sh
./scripts/g10-file-reconcile-honesty.sh --verify-report <out>/report.env
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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g10-file-reconcile-honesty/`. A sanitized copy is [`g10-file-reconcile-honesty/agent-vm.env`](./g10-file-reconcile-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g10_close=CLOSED` rejected | `2026-09-22T19:29:55Z` |
| Counting preview vs apply | **PASS** (gate) | in-memory `FileObjectReconciler`; collect `--skip-tests` on this VM |
| Tenant-prefix guard | **PASS** (gate) | tenant A apply leaves tenant B orphan |
| Live MinIO / object store | **SKIP** (expected) | `S3_ENDPOINT` unset; nothing on `:19000` / `:9000` |
| live_preview_observed | **false** | honesty gate requires `false` on SKIP |
| #141 identity drift redo | **false** | [g10-identity-drift-dry-run.md](../g10-identity-drift-dry-run.md) |
| G6 object restore redo | **false** | bytes are not reconstructed |
| Product G10 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live MinIO / object-store preview + apply + tenant-prefix walk on a host that actually has that process
- Product G10 close
- Identity apply / SAML / LDAP / SMS MFA (#141 leftover stays sibling)
- G6 whole-bucket / object restore
