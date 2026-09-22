# M0-02 isolation kernel evidence

Recorded 2026-09-22 on this agent VM. Java 21.0.10, Maven 3.9.16, in-process PostgreSQL **15.18** via zonky embedded binaries (no Docker).

## `mvn test`

G1 remaining-surfaces re-run **2026-09-22T15:28:16Z** (22 tests). Join + FieldPolicy-on-export remainder **2026-09-22T15:39:51Z**:

```
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Class | Tests |
|---|---|
| `SqlBypassScannerTest` | 2 |
| `QueryCompilerTest` | 7 (single-table plus INNER JOIN tenant inject / cross-tenant join reject / hostile DataScope on join) |
| `TableRegistryReplaceTest` | 3 |
| `PostgresIsolationIT` | 2 (dual-tenant CRUD; join + FieldPolicy export reject-before-JDBC on PG 15) |
| `PropagationContractTest` | 5 (cache / file / async rejects now assert `IsolationAuditEvent`) |
| `RequestIsolationPolicyTest` | 5 |
| `IsolatedExportTest` | 4 (file/cache/task `P1.M02.019`; join compile + FieldPolicy projection / reject-before-sink) |

S0+S1 G1 remaining surfaces (export / file / cache / Job / Task + Query AST joins + FieldPolicy on export + `AuditPort` `GUARD_REJECT`) are recorded in [docs/g1-isolation-surfaces.md](../../../docs/g1-isolation-surfaces.md). Not a product-wide zero-leak close.

## Static SQL-bypass gate

```
PASS static bypass check: .../sql-bypass-fixtures/positive
PASS static rejection: jdbc (exit 1)
PASS static rejection: spring (exit 1)
PASS static rejection: jpa (exit 1)
PASS static rejection: mybatis (exit 1)
```

Positive run allows `infrastructure/Adapter.java` (JDBC) because it sits under `--infra`. Negative fixtures are the same patterns as `poc/p0-01/test/fixtures`.
