# M0-02 isolation kernel evidence

Recorded 2026-09-22 on this agent VM. Java 21.0.10, Maven 3.9.16, in-process PostgreSQL **15.18** via zonky embedded binaries (no Docker).

## `mvn test`

G1 join + export FieldPolicy re-run **2026-09-22T15:41:22Z**. Subquery / batch / sort / page remainder **2026-09-22T16:45:38Z**. Nested subquery-in-subquery **2026-09-22T17:08:42Z**. OUTER / CROSS JOIN remainder **2026-09-22T17:09:50Z**. DataScope / FieldPolicy AST encoding **2026-09-22T17:37:17Z**. QueryCompiler-bypass CI gate **2026-09-22T17:39:35Z**:

```
Tests run: 59 (52 DataScope/FieldPolicy AST + 7 QueryCompilerBypassGateTest), Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Class | Tests |
|---|---|
| `SqlBypassScannerTest` | 2 |
| `QueryCompilerBypassGateTest` | 7 (intentional `l_demo_leave` / API JDBC / MyBatis `${}` fail; mini-product market dump fails; catalog JDBC allowed; product `src/main` empty) |
| `QueryCompilerTest` | 23 (INNER / OUTER / CROSS JOIN + IN/EXISTS + nested subquery-in-subquery + batch INSERT + sort/page inject/reject; SELF/DEPT equality atoms; DataScope cannot strip join/subquery/nested/batch tenant or author `tenant_id`) |
| `PostgresIsolationIT` | 6 (dual-tenant CRUD; same-tenant INNER; same-tenant subquery/batch/sort/page; same-tenant nested IN/EXISTS; same-tenant OUTER/CROSS; SELF/DEPT filters; stolen plans never reach JDBC) |
| `PropagationContractTest` | 5 (cache / file / async rejects now assert `IsolationAuditEvent`) |
| `RequestIsolationPolicyTest` | 5 |
| `IsolatedExportTest` | 8 (export/file/cache/task + `EXPORT_ACCEPTED`; FieldPolicy `P1.M02.021`; stolen subquery / nested subquery `P1.M02.022`; stolen OUTER/CROSS `P1.M02.020`; encoded SELF + visible projection) |
| `TableRegistryReplaceTest` | 3 |

S0+S1 G1 remaining surfaces (export / file / cache / Job / Task + `AuditPort` `GUARD_REJECT`) are recorded in [docs/g1-isolation-surfaces.md](../../../docs/g1-isolation-surfaces.md). INNER JOIN + export FieldPolicy (`P1.M02.020` / `P1.M02.021`) are recorded in [docs/g1-query-join-field-policy.md](../../../docs/g1-query-join-field-policy.md). Subquery / batch / sort / page (`P1.M02.022`–`025`) are recorded in [docs/g1-query-ast-remainder.md](../../../docs/g1-query-ast-remainder.md). Nested subquery-in-subquery is recorded in [docs/g1-query-nested-subquery.md](../../../docs/g1-query-nested-subquery.md). OUTER / CROSS JOIN (`P1.M02.020`) is recorded in [docs/g1-query-outer-cross-join.md](../../../docs/g1-query-outer-cross-join.md). SELF/DEPT + FieldPolicy visibility encoding is recorded in [docs/g1-datascope-fieldpolicy-ast.md](../../../docs/g1-datascope-fieldpolicy-ast.md). Product list/detail opt-in is [docs/g1-queryast-product-optin.md](../../../docs/g1-queryast-product-optin.md). Product `src/main` QueryCompiler-bypass CI is [docs/g1-querycompiler-bypass-ci.md](../../../docs/g1-querycompiler-bypass-ci.md).

## Static SQL-bypass gate

```
PASS static bypass check: .../sql-bypass-fixtures/positive
PASS static rejection: jdbc (exit 1)
PASS static rejection: spring (exit 1)
PASS static rejection: jpa (exit 1)
PASS static rejection: mybatis (exit 1)
```

Positive run allows `infrastructure/Adapter.java` (JDBC) because it sits under `--infra`. Negative fixtures are the same patterns as `poc/p0-01/test/fixtures`.

## QueryCompiler bypass gate (product `src/main`)

Recorded **2026-09-22T17:42:01Z** after rebase onto `440a504` (#113). Allowlisted modules are `QueryCompilerBypassAllowlist` / [docs/g1-querycompiler-bypass-ci.md](../../../docs/g1-querycompiler-bypass-ci.md).

```
PASS QueryCompiler bypass gate: /workspace
QueryCompilerBypassGateTest 7/7
G1QueryCompilerBypassTest 1/1
QueryCompilerBypassArchTest 2/2 (product layers pass; RawTenantSqlFixture fails ArchUnit)
```
