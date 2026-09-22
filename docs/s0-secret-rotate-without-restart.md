# S0 leftover — env / process secret rotate-without-restart

S0+S1 remainder after SecretPort on `main` ([s0-secret-port.md](./engineering/s0-secret-port.md)). **Not** a P0 / M0-11 CryptoPort redo. **G18 stays closed.** This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** introduce OpenBao / cloud KMS / 国密 KMS / 密码机. It does **not** claim a product S0 close.

Existing SecretPort already covers local envelope `put` versioning, local-over-env overlay, env logical-name / alias reads, and File Service S3 wiring. The leftover here is **whether rotating an env-backed secret is visible without process restart**.

## What this slice proves

| Path | Behaviour |
|---|---|
| Mutable env map | `EnvSecretAdapter` does not cache. Next `get` / `find` / `getUtf8` sees the rotated value (or blank → `SECRET_NOT_FOUND`). |
| `LocalThenEnvSecretPort` without overlay | Same live env read. Rotate is picked up. |
| Overlay still wins | Local `put` still hides a later env rotate. After `delete`, the rotated env value is visible. |
| Product `EnvSecretAdapter.system()` / `System.getenv()` | Java 21 `System.getenv()` is an unmodifiable process-start snapshot. OS environ rotate of a running JVM is **not** a supported API. Test **SKIP**s with that reason. Restart is required to see a new OS environment. |
| File S3 client | `MinioObjectStorage.fromSecretPort` snapshots access/secret keys at construct. A later SecretPort-visible env rotate does not rebuild the Minio client. Honest leftover; this package does not rewrite S3. |

No OpenBao. No KMS. Test secrets are in-process maps only; none are committed.

## How to run (no Docker)

```bash
./scripts/s0-secret-rotate-without-restart.sh
```

Equivalent:

```bash
mvn -pl nexus-crypto-core,nexus-file-core -am test \
  -Dtest=SecretPortRotateWithoutRestartTest,EnvSecretAdapterTest,SecretPortTest,FileSecretPortWiringTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Java **21** / Boot **4.1.1**. No Docker. No live `127.0.0.1:5432` required.

See [engineering/s0-secret-rotate-without-restart-EVIDENCE.md](./engineering/s0-secret-rotate-without-restart-EVIDENCE.md).

## What this does not claim

| Leftover | Why |
|---|---|
| Product S0 close | Env + local overlay only. CORE-SEC-003 / OpenBao / KMS stay out. |
| OS process env rotate without restart | JVM snapshot. Honest SKIP. |
| JDBC DataSource runtime SecretPort | Spring still binds `NEXUS_DB_*` at start. Unchanged from [s0-secret-port.md](./engineering/s0-secret-port.md). |
| S3 / MinIO client live rotate | Credentials snapshotted at `fromSecretPort`. |
| OpenBao / cloud KMS / 国密密码机 | ARCH-007. Not introduced. |
| G18 reopen / AuditPort rewrite | Closed; not walked again. |

## Honest leftovers (not claimed)

- OS `System.getenv()` rotate-without-restart on a running JVM (Java 21 snapshot; SKIP)
- Rebuilding `MinioObjectStorage` / Spring DataSource after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / KMS / 密码机 adapters
- Product S0 / S1 release close
