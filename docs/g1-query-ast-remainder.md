# G1 remaining: Query AST subquery / batch write / sort / page

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. Export / file / cache / Job / Task landed in [#71](https://github.com/EricBeckham/NexusOne/pull/71). INNER JOIN + export FieldPolicy landed in [#87](https://github.com/EricBeckham/NexusOne/pull/87). This package extends the same M0-02 `QueryCompiler` for the leftover G1 AST rows called out there.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining (G4 wiring is already on `main`).

## What this close covers

Cross-tenant **IN / EXISTS subquery** and **multi-row INSERT**, plus **invalid ORDER BY** and **unbounded LIMIT/OFFSET**, are **rejected before JDBC**, and each reject emits:

1. `IsolationAuditEvent` (`ISOLATION_REJECT`) through the existing `IsolationAuditor`
2. `AuditPort.appendNewTransaction(AuditCaptures.guardReject(...))` in product (`GUARD_REJECT` / `isolation.guard.reject`)

| Surface | Guard | Code | Reject-before |
|---|---|---|---|
| Query AST subquery | `TenantInjector.injectSubquery` / `TableAccessPlanGuard.checkSubquery` | `P1.M02.022` | `Connection.prepareStatement` |
| Query AST batch write | `TenantInjector.inject` batch path / `TableAccessPlanGuard.check` | `P1.M02.023` | `Connection.prepareStatement` |
| Query AST sort | `QueryCompiler.validateSort` | `P1.M02.024` | `Connection.prepareStatement` |
| Query AST page | `QueryCompiler.validatePage` | `P1.M02.025` | `Connection.prepareStatement` |

Subqueries reuse `QueryAst` + `QueryCompiler` + `GuardedJdbc`. Each tenant-scoped inner table gets its own `tenant_id = ?`. Batch INSERT injects `tenant_id` on every row. Sort accepts only registered identifiers and `ASC`/`DESC`. Page is parameterized `LIMIT ? OFFSET ?` with `1..1000` / `offset >= 0`.

## Evidence

Recorded on this agent VM. Java **21.0.10**, Maven **3.9.16**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` uses in-process PostgreSQL **15.18** (Zonky). No Docker.

```
mvn -f modules/isolation-kernel/pom.xml test
# Tests run: 37, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS  2026-09-22T16:45:38Z

mvn -pl nexus-bootstrap -am test \
    -Dtest=G1QueryAstRemainderTest,G1QueryJoinFieldPolicyTest,G1IsolationSurfacesTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T16:46:04Z
# G1QueryAstRemainderTest 1/1, G1QueryJoinFieldPolicyTest 1/1, G1IsolationSurfacesTest 1/1
```

| Suite | What it proved |
|---|---|
| `QueryCompilerTest` | IN / EXISTS inject `tenant_id` on the inner table; stolen / null subquery tenant and tenant→platform subquery rejected with records; DataScope cannot strip a subquery tenant predicate; batch INSERT injects every row; stolen / null batch tenant rejected; DataScope cannot rewrite batch `tenant_id`; unregistered / injected sort and unbounded page rejected (`P1.M02.024` / `P1.M02.025`) |
| `PostgresIsolationIT` | Same-tenant subquery + sort + page returns only the bound tenant's line; stolen subquery / batch / sort / page never reach JDBC |
| `IsolatedExportTest` | Stolen subquery rejected before export compile; same-tenant visible projection compiles IN + ORDER BY + LIMIT |
| `G1QueryAstRemainderTest` | All four codes land on `AuditPort` as `GUARD_REJECT` via `appendNewTransaction`; hash chain intact; `IsolatedExport.compile` of a same-tenant paged subquery is allowed |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close. Nested subquery-in-subquery is evidenced separately in [g1-query-nested-subquery.md](./g1-query-nested-subquery.md).

| Still open | Why |
|---|---|
| OUTER JOIN / CROSS JOIN | Not added to `QueryJoin`. INNER JOIN equality remains the only join shape. Parallel package; this file does not claim it. |
| DataScope / FieldPolicy compilation into AST | Unchanged from [g1-query-join-field-policy.md](./g1-query-join-field-policy.md). |
| Report UI / physical ledger dump | Unchanged. `ledger.export.csv` is still the Task type. |
| Silent drop on API reads | Unchanged. Export still rejects hidden/second-auth instead of dropping. |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. New AST nodes use `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. Guard-reject still uses `appendNewTransaction` only. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | Direct JDBC / raw SQL outside `QueryCompiler` is still a caller-wiring gap. |
