# M0-02 isolation kernel evidence

Recorded 2026-09-22 on this agent VM. Java 21.0.10, Maven 3.8.7, in-process PostgreSQL **15.18** via zonky embedded binaries (no Docker).

## `mvn test`

```
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Class | Tests |
|---|---|
| `SqlBypassScannerTest` | 2 |
| `QueryCompilerTest` | 4 |
| `PostgresIsolationIT` | 1 (dual-tenant CRUD on PG 15; denies use an unusable Connection so rejects cannot reach JDBC) |
| `PropagationContractTest` | 5 |
| `RequestIsolationPolicyTest` | 5 |

## Static SQL-bypass gate

```
PASS static bypass check: .../sql-bypass-fixtures/positive
PASS static rejection: jdbc (exit 1)
PASS static rejection: spring (exit 1)
PASS static rejection: jpa (exit 1)
PASS static rejection: mybatis (exit 1)
```

Positive run allows `infrastructure/Adapter.java` (JDBC) because it sits under `--infra`. Negative fixtures are the same patterns as `poc/p0-01/test/fixtures`.
