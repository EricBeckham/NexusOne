# S0 leftover: env / process secret rotate-without-restart evidence

Recorded **2026-09-22T18:52:12Z** on this agent VM; rebased onto `origin/main` `7735636` (Merge #145). **Not** a product S0 close. **Not** OpenBao / KMS. **Not** CORE-SEC-003. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md). Secrets were supplied via injected env maps; none are committed.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21.0.10** | product baseline |
| Maven | **3.8.7** | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | local `mvn test` | No Docker / Compose |

## Commands

```bash
./scripts/s0-secret-rotate-without-restart.sh
```

`DualStackTlsProbe*` (G17 leftover on `main`, bctls **1.85** missing `SignatureAndHashAlgorithm.ecdsa_secp256r1_sha256`) was moved out of the compile tree for this leftover-off only, then restored. That compile break is out of SecretPort rotate scope and is **not** committed here.

## Results (agent VM)

Finished at: **2026-09-22T18:52:12Z**. `SecretPortRotateWithoutRestartTest` Tests run: **7**, Failures: **0**, Errors: **0**, Skipped: **1**. `EnvSecretAdapterTest` **3/3**. `SecretPortTest` **3/3**. `FileSecretPortWiringTest` **5/5**. **BUILD SUCCESS** (4.489 s). PASS: env-map rotate-without-restart picked up; OS getenv snapshot SKIP.

| Check | Result | Notes |
|---|---|---|
| `SecretPortRotateWithoutRestartTest.envMapRotateIsPickedUpOnNextGet` | **PASS** | mutable env map rotate visible on next get |
| `localThenEnvPicksUpEnvRotateWhenNoOverlay` / overlay-wins / blank→not-found | **PASS** | product `local+env` overlay semantics unchanged |
| `processGetenvRotateWithoutRestartIsJvmSnapshot` | **SKIP** expected | Java 21 `System.getenv()` process-start snapshot |
| `leftoverDocsStayHonest` / `envAdapterDoesNotCacheAndDoesNotCallOpenBao` | **PASS** | no OpenBao / KMS; docs refuse S0 close |
| `FileSecretPortWiringTest` + snapshot leftover | **5/5 PASS** | S3 still SecretPort-only; Minio snapshots at construct |
| `EnvSecretAdapterTest` / `SecretPortTest` | **3/3 + 3/3 PASS** | existing S0 reads / overlay unchanged |
| Product S0 close / OpenBao / KMS | **NOT_CLAIMED** | leftover only |
| G18 / AuditPort | unchanged | not reopened |

## Honest leftovers

- OS `System.getenv()` rotate-without-restart (JVM snapshot; SKIP)
- Rebuilding MinIO / JDBC clients after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / cloud KMS / 国密密码机
- Product S0 / S1 release close
- `DualStackTlsProbe` compile vs pinned bctls 1.85 (G17 leftover on `main`; out of this slice)
