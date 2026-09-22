# M0-11 evidence (Boot 4.1.1 + PostgreSQL 15.x)

Recorded **2026-09-22** on the agent VM against `main` (`d19fba5`). Same-TX hash-chain hardening re-ran `nexus-audit-core` on this branch at **2026-09-22T13:36:12Z** (Zonky PG 15.18; live `127.0.0.1:5432` still absent). **Not** a target Profile / Linux-ARM / offline-package acceptance. **Not** G4 / G6 / G17. Product G18 was closed later by the browser record in [g18-business-path.md](../g18-business-path.md); this file stays the M0-11 library evidence.

Secrets (`NEXUS_DB_PASSWORD`, `NEXUS_CRYPTO_MASTER_KEY`) were supplied via environment only; none are committed.

## Exact versions

| Item | Value |
|---|---|
| JDK | OpenJDK `21.0.10` (`21.0.10+7-Ubuntu-124.04`) |
| Maven | 3.8.7 |
| Spring Boot parent | **4.1.1** |
| Flyway | **13.7.0** (window **V500+**; this PR applies **V501**. **V500** is M0-09 execution channel) |
| Bouncy Castle | `bcprov-jdk18on` **1.85.2** (same JCA path as `poc/p0-10`; not a GM/T qualified module) |
| PostgreSQL `server_version` | `15.19 (Ubuntu 15.19-1.pgdg24.04+2)` |
| `pg_dump --version` | `pg_dump (PostgreSQL) 15.19 (Ubuntu 15.19-1.pgdg24.04+2)` |
| `psql` default client on PATH | 16.15 (Ubuntu package). Server and dump tool used for this evidence are **15.19**. |
| Artifact | `nexus-crypto-core` + `nexus-audit-core` libraries, started by `nexus-bootstrap` |
| Host | local JAR/process, `127.0.0.1:5432`, no Docker |

Databases: `nexusone` (bootstrap IT), `nexus_m0_11_test` (JDBC hash-chain IT). Credentials via `NEXUS_DB_*` only.

## `mvn test` (M0-11 slice)

From repo root:

```bash
mvn -pl nexus-common,nexus-crypto-core,nexus-audit-core,nexus-bootstrap -am test
```

| Module | Tests | Fail | Skip |
|---|---|---|---|
| `nexus-common` | 14 | 0 | 0 |
| `nexus-crypto-core` | 14 | 0 | 0 |
| `nexus-audit-core` | 11 | 0 | 1 (`JdbcAuditChainTest`, no live PG) |
| `nexus-bootstrap` | 8 | 0 | 0 |

`nexus-crypto-core` includes `FixedVectorTest` (7) against published SHA-256 / SM3 / HMAC-SHA256 RFC 4231 / NIST AES-256-GCM / GM/T 0002 SM4-ECB / GM/T 0003 SM2 sample keys, plus BC 1.85.2 + Java 21 frozen SM4-GCM and HMAC-SM3.

`nexus-audit-core` includes `JdbcAuditChainTest` (skipped here; no live `nexus_m0_11_test`) and `JdbcAuditChainTransactionTest` **4/4** on Zonky PG 15.18 (`append` joins Spring TX / rolls back with the chain head; commit leaves an intact chain; `appendNewTransaction` still survives). Recorded **2026-09-22T13:36:12Z**.

Bootstrap `NexusOnePostgresTest` asserts Flyway applied **≥ 8** versioned migrations including **V500** (M0-09) and **V501** (M0-11). After rebase onto #51–#53 a clean `nexusone` applied **9** SQL versions (V001–V003, V100–V101, V200, V300, V500, V501). `GET /api/v1/meta/runtime` returns `crypto_suite":"INTERNATIONAL"` without key material.

## Flyway history after bootstrap IT (`nexusone`)

History table: `platform.flyway_schema_history`.

| version | description |
|---|---|
| 001 | create logical schemas |
| 002 | create table registry |
| 003 | platform runtime meta |
| 100 | p1 tenant |
| 101 | p1 platform tenant |
| 200 | iam identity |
| 300 | permission engine (M0-05) |
| 500 | execution channel (M0-09) |
| **501** | **audit hash chain (M0-11)** |

`platform.audit_event` (P1 staging) is unchanged. `platform.audit_record` / `platform.audit_chain_meta` are the M0-11 append-only store.

## Recovery dry-run

`DryRunRecoveryPort.dryRunPostgres()` reported `pg_dump (PostgreSQL) 15.19 …`. `profileAccepted` is always false. No dump or restore was executed. No object-store restore was executed.

## What this is not

- Not G4 (audit coverage / 90-day hot + 365-day archive).
- Not G6 (whole-DB + object restore drill on an independent instance with RPO/RTO).
- Not G17 (classified / GM qualified delivery).
- Not a business-path end-to-end hash chain (P1/P2/isolation-kernel still do not write `AuditPort`).
