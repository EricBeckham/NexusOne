# S0 SecretPort evidence (Boot 4.1.1 + Java 21)

Recorded **2026-09-22T15:31:18Z** on this agent VM against `cursor/secret-port-s0-b396`. **Not** a target Profile / Linux-ARM acceptance. **Not** OpenBao / KMS. **Not** CORE-SEC-003.

Secrets were supplied via in-process overlay or injected env maps in tests; none are committed.

## Exact versions

| Item | Value |
|---|---|
| JDK | OpenJDK `21.0.10` (`21.0.10+7-Ubuntu-124.04`) |
| Maven | 3.8.7 |
| Spring Boot parent | **4.1.1** |
| PostgreSQL (Zonky embedded, bootstrap IT) | **15.18.0** |
| Artifact | `nexus-crypto-core` + File Service call site, started by `nexus-bootstrap` |
| Host | local JAR/process, no Docker |

## Commands

```bash
mvn -pl nexus-common,nexus-crypto-core,nexus-file-core test
mvn -pl nexus-bootstrap -am -DskipTests install
mvn -pl nexus-bootstrap test
```

## Results

| Module | Tests | Fail | Skip |
|---|---|---|---|
| `nexus-common` | 14 | 0 | 0 |
| `nexus-crypto-core` | 18 | 0 | 0 |
| `nexus-file-core` | 10 | 0 | 0 |
| `nexus-bootstrap` | 12 | 0 | 5 |

Crypto suite includes `EnvSecretAdapterTest` **3/3** and `SecretPortTest` **3/3** (local envelope + local-over-env overlay). File suite includes `FileSecretPortWiringTest` **4/4** (S3 credentials from SecretPort; missing port rejected; `fromSecretPort` does not invent keys). Bootstrap `NexusOneProductBootTest` **2/2** (product `SecretPort` is `LocalThenEnvSecretPort`, `backend=local+env`, put/get overlay). `NexusOnePostgresTest` **4 skipped** (no live `127.0.0.1:5432`).

`GET /api/v1/meta/runtime` fields `secret_backend` / `secret_openbao=false` / `secret_kms=false` are covered by `NexusOnePostgresTest` and were not re-hit on this VM without a listener.
