# M0-02 isolation kernel evidence

Recorded 2026-09-22 on this agent VM. Java 21.0.10, Maven 3.9.16, in-process PostgreSQL **15.18** via zonky embedded binaries (no Docker).

## `mvn test`

G1 join + export FieldPolicy re-run **2026-09-22T15:41:22Z**. Subquery / batch / sort / page remainder **2026-09-22T16:45:38Z**. Nested subquery-in-subquery **2026-09-22T17:08:42Z**:

```
Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Class | Tests |
|---|---|
| `SqlBypassScannerTest` | 2 |
| `QueryCompilerTest` | 17 (INNER JOIN + IN/EXISTS + nested subquery-in-subquery + batch INSERT + sort/page inject/reject; DataScope cannot strip join/subquery/nested/batch tenant) |
| `PostgresIsolationIT` | 4 (dual-tenant CRUD; same-tenant join; same-tenant subquery/batch/sort/page; same-tenant nested IN/EXISTS; stolen plans never reach JDBC) |
| `PropagationContractTest` | 5 (cache / file / async rejects now assert `IsolationAuditEvent`) |
| `RequestIsolationPolicyTest` | 5 |
| `IsolatedExportTest` | 6 (export/file/cache/task + `EXPORT_ACCEPTED`; FieldPolicy `P1.M02.021`; stolen subquery / nested subquery `P1.M02.022` reject-before-compile) |
| `TableRegistryReplaceTest` | 3 |

S0+S1 G1 remaining surfaces (export / file / cache / Job / Task + `AuditPort` `GUARD_REJECT`) are recorded in [docs/g1-isolation-surfaces.md](../../../docs/g1-isolation-surfaces.md). INNER JOIN + export FieldPolicy (`P1.M02.020` / `P1.M02.021`) are recorded in [docs/g1-query-join-field-policy.md](../../../docs/g1-query-join-field-policy.md). Subquery / batch / sort / page (`P1.M02.022`–`025`) are recorded in [docs/g1-query-ast-remainder.md](../../../docs/g1-query-ast-remainder.md). Nested subquery-in-subquery is recorded in [docs/g1-query-nested-subquery.md](../../../docs/g1-query-nested-subquery.md).

## Static SQL-bypass gate

```
PASS static bypass check: .../sql-bypass-fixtures/positive
PASS static rejection: jdbc (exit 1)
PASS static rejection: spring (exit 1)
PASS static rejection: jpa (exit 1)
PASS static rejection: mybatis (exit 1)
```

Positive run allows `infrastructure/Adapter.java` (JDBC) because it sits under `--infra`. Negative fixtures are the same patterns as `poc/p0-01/test/fixtures`.
