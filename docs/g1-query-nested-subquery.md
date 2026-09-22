# G1 remaining: Query AST nested subquery

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. One-level IN / EXISTS, batch write, sort, and page landed in [#95](https://github.com/EricBeckham/NexusOne/pull/95) ([g1-query-ast-remainder.md](./g1-query-ast-remainder.md)). This package extends the same M0-02 `QueryCompiler` so **subquery-in-subquery** compiles.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining. It does **not** add OUTER JOIN / CROSS JOIN. It does **not** claim a full G1 close.

## What this close covers

A nested IN / EXISTS child is a `QuerySubquery` on `QuerySubquery.nested()`. Every level still goes through:

1. `TenantInjector.injectSubquery` — each tenant-scoped inner table gets its own `tenant_id = ?`
2. `TableAccessPlanGuard.checkSubquery` — tenant predicate / platform table / missing context
3. DataScope preservation — a hostile injector cannot strip a nested `tenant_id`
4. Parameterized `PostgresRenderer` — illegal shapes never reach `Connection.prepareStatement`

Stolen / null nested tenant and tenant→platform nested table reuse `P1.M02.022` (`CROSS_TENANT_SUBQUERY`) or `P1.M02.004` (`ILLEGAL_PLATFORM_ACCESS`). Nesting deeper than `QuerySubquery.MAX_NESTING` (8) is rejected as an illegal shape before render.

| Surface | Guard | Code | Reject-before |
|---|---|---|---|
| Nested Query AST subquery | `TenantInjector.injectSubquery` / `TableAccessPlanGuard.checkSubquery` at every depth | `P1.M02.022` | `Connection.prepareStatement` |

## Evidence

Recorded on this agent VM. Java **21.0.10**, Maven **3.9.16**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` uses in-process PostgreSQL **15.18** (Zonky). No Docker.

```
mvn -f modules/isolation-kernel/pom.xml test
# Tests run: 42, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS  2026-09-22T17:08:42Z

mvn -pl nexus-bootstrap -am test \
    -Dtest=G1QueryNestedSubqueryTest,G1QueryAstRemainderTest,G1QueryJoinFieldPolicyTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T17:09:15Z
# G1QueryNestedSubqueryTest 1/1, G1QueryAstRemainderTest 1/1, G1QueryJoinFieldPolicyTest 1/1
```

| Suite | What it proved |
|---|---|
| `QueryCompilerTest` | Nested IN / EXISTS inject `tenant_id` on every level; stolen / null nested tenant and tenant→platform nested rejected with records; duplicate nested EXISTS alias rejected; depth above 8 rejected; DataScope cannot strip a nested tenant predicate |
| `PostgresIsolationIT` | Same-tenant nested IN and EXISTS return only the bound tenant's entry; stolen / null nested tenant and tenant→platform nested never reach JDBC |
| `IsolatedExportTest` | Stolen nested subquery rejected before export compile; same-tenant nested IN compiles with three `tenant_id` parameters |
| `G1QueryNestedSubqueryTest` | Stolen nested tenant lands on `AuditPort` as `GUARD_REJECT` (`P1.M02.022`) via `appendNewTransaction`; hash chain intact; `IsolatedExport.compile` of a same-tenant nested IN is allowed |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close and **not** a full G1 close.

| Still open | Why |
|---|---|
| OUTER JOIN / CROSS JOIN | Evidenced in [g1-query-outer-cross-join.md](./g1-query-outer-cross-join.md). |
| DataScope / FieldPolicy compilation into AST | SELF/DEPT equality + visibility encoding is [g1-datascope-fieldpolicy-ast.md](./g1-datascope-fieldpolicy-ast.md). OR / IN / mask leftovers stay there. |
| Report UI / physical ledger dump | Unchanged. `ledger.export.csv` is still the Task type. |
| Silent drop on API reads | Unchanged. Export still rejects hidden/second-auth instead of dropping. |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. New AST nodes use `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. Guard-reject still uses `appendNewTransaction` only. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | Direct JDBC / raw SQL outside `QueryCompiler` is still a caller-wiring gap. |
