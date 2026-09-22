# G1 remaining: Query AST OUTER JOIN / CROSS JOIN

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. INNER JOIN + export FieldPolicy landed in [#87](https://github.com/EricBeckham/NexusOne/pull/87). Subquery / batch / sort / page landed in [#95](https://github.com/EricBeckham/NexusOne/pull/95). This package extends the same M0-02 `QueryCompiler` / `QueryJoin` for the leftover join shapes called out there.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining (G4 wiring is already on `main`). Product G18 stays closed; the leave path is not reopened.

## What this close covers

**LEFT / RIGHT / FULL OUTER JOIN** and **CROSS JOIN** are first-class `QueryJoin` shapes. Each tenant-scoped joined table still gets its own `tenant_id` predicate (ON for INNER/OUTER; WHERE for CROSS). Cross-tenant or illegal join shapes are **rejected before JDBC**, and each reject emits:

1. `IsolationAuditEvent` (`ISOLATION_REJECT`) through the existing `IsolationAuditor`
2. `AuditPort.appendNewTransaction(AuditCaptures.guardReject(...))` in product (`GUARD_REJECT` / `isolation.guard.reject`)

| Surface | Guard | Code | Reject-before |
|---|---|---|---|
| Query AST OUTER JOIN | `TenantInjector.injectJoin` / `TableAccessPlanGuard.checkJoin` | `P1.M02.020` | `Connection.prepareStatement` |
| Query AST CROSS JOIN | same; CROSS cannot carry `ON`; tenant filters stay in `WHERE` | `P1.M02.020` | `Connection.prepareStatement` |
| Illegal join shape | `QueryCompiler.validateJoin` (OUTER without `ON`; CROSS with `ON`) | `P1.M02.020` | `Connection.prepareStatement` |

Same reject-before-execute rules as INNER JOIN: stolen / null join `tenant_id`, tenant→platform join, and DataScope stripping a join tenant predicate never reach the renderer.

## Evidence

Recorded on this agent VM. Java **21.0.10**, Maven **3.9.16**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` uses in-process PostgreSQL **15.18** (Zonky). No Docker.

```
mvn -f modules/isolation-kernel/pom.xml test
# Tests run: 43, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS  2026-09-22T17:09:50Z

mvn -pl nexus-bootstrap -am test \
    -Dtest=G1QueryOuterCrossJoinTest,G1QueryAstRemainderTest,G1QueryJoinFieldPolicyTest,G1IsolationSurfacesTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T17:10:15Z
# G1QueryOuterCrossJoinTest 1/1, G1QueryAstRemainderTest 1/1,
# G1QueryJoinFieldPolicyTest 1/1, G1IsolationSurfacesTest 1/1
```

| Suite | What it proved |
|---|---|
| `QueryCompilerTest` | LEFT / RIGHT / FULL OUTER and CROSS inject `tenant_id` on both tables; stolen / null join tenant, tenant→platform, OUTER without `ON`, and CROSS with `ON` rejected with records; DataScope cannot strip an OUTER/CROSS join tenant predicate |
| `PostgresIsolationIT` | Same-tenant LEFT JOIN keeps the orphan entry and never attaches a beta line; CROSS JOIN stays on the bound tenant's line; stolen / illegal joins never reach JDBC (`P1.M02.020`) |
| `IsolatedExportTest` | Stolen LEFT / CROSS rejected before export compile; same-tenant visible projection compiles both shapes with join `tenant_id = ?` |
| `G1QueryOuterCrossJoinTest` | Stolen LEFT / CROSS, OUTER without `ON`, and CROSS with `ON` land on `AuditPort` as `GUARD_REJECT` via `appendNewTransaction` (`P1.M02.020`); hash chain intact; `IsolatedExport.compile` of same-tenant LEFT and CROSS is allowed |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close.

| Still open | Why |
|---|---|
| Nested subquery | Evidenced in [g1-query-nested-subquery.md](./g1-query-nested-subquery.md). |
| Product-wide G1 close | UI / H5 every page, report UI / physical ledger dump, and callers that skip `QueryCompiler` stay open. |
| DataScope / FieldPolicy compilation into AST | SELF/DEPT equality + visibility encoding is [g1-datascope-fieldpolicy-ast.md](./g1-datascope-fieldpolicy-ast.md). OR / IN / mask leftovers stay there. |
| Silent drop on API reads | Unchanged. Export still rejects hidden/second-auth instead of dropping. |
| `P3RequestContext` | Unchanged. New join kinds use `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. Guard-reject still uses `appendNewTransaction` only. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | Direct JDBC / raw SQL outside `QueryCompiler` is still a caller-wiring gap. |
