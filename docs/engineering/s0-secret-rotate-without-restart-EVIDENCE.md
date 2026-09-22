# S0 leftover: env / process secret rotate-without-restart evidence

Recorded **pending-this-run** on this agent VM after rebase onto `origin/main` `6fce50d` (Merge #134). **Not** a product S0 close. **Not** OpenBao / KMS. **Not** CORE-SEC-003. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md). Secrets were supplied via injected env maps; none are committed.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x** | filled after the test run |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | local `mvn test` | No Docker / Compose |

## Commands

```bash
./scripts/s0-secret-rotate-without-restart.sh
```

## Results (agent VM)

Filled after the leftover suite on this VM.

| Check | Result | Notes |
|---|---|---|
| `SecretPortRotateWithoutRestartTest.envMapRotateIsPickedUpOnNextGet` | pending | mutable env map rotate visible on next get |
| `localThenEnvPicksUpEnvRotateWhenNoOverlay` / overlay-wins / blank→not-found | pending | product `local+env` overlay semantics unchanged |
| `processGetenvRotateWithoutRestartIsJvmSnapshot` | **SKIP** expected | Java 21 `System.getenv()` process-start snapshot |
| `leftoverDocsStayHonest` / `envAdapterDoesNotCacheAndDoesNotCallOpenBao` | pending | no OpenBao / KMS; docs refuse S0 close |
| `FileSecretPortWiringTest` + snapshot leftover | pending | S3 still SecretPort-only; Minio snapshots at construct |
| `EnvSecretAdapterTest` / `SecretPortTest` | pending | existing S0 reads / overlay unchanged |
| Product S0 close / OpenBao / KMS | **NOT_CLAIMED** | leftover only |
| G18 / AuditPort | unchanged | not reopened |

## Honest leftovers

- OS `System.getenv()` rotate-without-restart (JVM snapshot; SKIP)
- Rebuilding MinIO / JDBC clients after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / cloud KMS / 国密密码机
- Product S0 / S1 release close
