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

M2 supersedes the original volatile product wiring described in the historical run above. Product bootstrap now wires `LocalThenEnvSecretPort` over `JdbcEncryptedSecretAdapter` and `EnvSecretAdapter` (`backend=jdbc+env`); PostgreSQL persists only CryptoPort envelopes. The embedded-PostgreSQL `NexusOneProductBootTest` also reconstructs the adapter and a CryptoPort from the same configured master key, reads the persisted value, and verifies rotation replaces its ciphertext and increments the secret version. Cross-process recovery requires stable injected master-key configuration; the ephemeral dev default is not restart-safe.

`GET /api/v1/meta/runtime` fields `secret_backend` / `secret_openbao=false` / `secret_kms=false` are covered by `NexusOnePostgresTest` and were not re-hit on this VM without a listener.
