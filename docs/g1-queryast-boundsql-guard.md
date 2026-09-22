# G1 leftover: GuardedLedgerStore leftover BoundSql / count bypass guard

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. `#115` encoded SELF/DEPT equality. `#130` opted product list/detail into that encoder. `#142` encoded single-column `department_id IN (?, …)` onto the AST. This leftover **does not redo that AST work**. It closes the remaining **raw BoundSql bypass** on `GuardedLedgerStore` for mixed-column OR leftovers and `COUNT(*)`.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining. It does **not** claim a product-wide G1 close. Orthogonal to `#142` (DataScope IN into Query AST), `#144` (G2 tenant_only / catalog), `#141` (G10 identity drift-fix dry-run), `#139` (G3 outbox consume-idempotent), `#137` (physical dump SurfaceGuard), and `#134` (export FieldPolicy drop).

## What this leftover covers

Leftover mixed-column OR (SELF∨DEPT) still cannot sit in Query AST filters. `COUNT(*)` is still not a Query AST node. Both paths now compile through `QueryAstScopedQuery` and execute as leftover-bound `RenderedSql` on `kernel.jdbc()` / `GuardedJdbc`. Raw `BoundSql` with only an unquoted `tenant_id` substring is rejected.

| Surface | Guard | What changed | Still leftover |
|---|---|---|---|
| Mixed-column OR list / detail | `QueryAstScopedQuery.executed` + `GuardedLedgerStore` | Leftover-bound SQL must start from QueryCompiler SQL (quoted `"tenant_id"`) and keep leftover `AND (…)` before `ORDER BY` / `LIMIT`; execute via `kernel.jdbc()` | SELF∨DEPT still not an AST OR node |
| Ledger total | `QueryAstScopedQuery.count` / `countExecuted` | Wrap only a `CompiledRead`; unquoted `tenant_id` BoundSql fails closed | `COUNT(*)` is not a Query AST node |
| Encoded SELF / DEPT / single-column `IN` | Unchanged `#130` / `#142` | Fully encoded reads already used `kernel.jdbc()`; count now uses the same plan | Mask / second-auth still `FieldPolicyProjector` |

`GuardedLedgerStore` no longer prepares leftover `BoundSql` itself. TenantInjector still owns `tenant_id`.

## Evidence

Recorded on this agent VM after rebase onto `main@e0a409a` (#141). Java **21**, Maven **3.8.x / 3.9.x**, Spring Boot **4.1.1**. Isolation-kernel compile path is in-process (no live PostgreSQL required for this leftover). No Docker.

```
mvn -pl nexus-permission-core,nexus-bootstrap -am test \
    -Dtest=QueryAstScopedQueryTest,QueryAstPolicyCompilerTest,G1QueryAstBoundSqlGuardTest,G1QueryAstProductOptInTest,G1QueryDataScopeInAstTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# evidence pending this agent run
```

| Suite | What it proved |
|---|---|
| `QueryAstScopedQueryTest` | Leftover OR + count stay on quoted `"tenant_id"` QueryCompiler SQL; raw unquoted `tenant_id` BoundSql count is rejected; `#142` SPECIFIED `IN` still fully encoded |
| `G1QueryAstBoundSqlGuardTest` | Product-shaped leftover OR / count execute as leftover-bound `RenderedSql`; `GuardedLedgerStore` source has no `BoundSql` / `prepareStatement`; hash chain intact |
| `G1QueryAstProductOptInTest` | `#130` SELF opt-in unchanged |
| `G1QueryDataScopeInAstTest` | `#142` single-column `IN` encode unchanged |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close and **not** a full G1 close.

| Still open | Why |
|---|---|
| Mixed-column OR (SELF∨DEPT) | Query AST filters stay AND of column atoms. Leftover is guarded, not encoded. |
| FieldPolicy mask / second-auth | AST / export drop hidden columns. Mask merge and second-auth still run in `FieldPolicyProjector`. |
| `COUNT(*)` wrap | Totals still wrap compiled SELECT text. Not a Query AST node. |
| Callers that still skip | `PermissionService.scopedQuery`, hook `.query()` strings, and any adapter that still builds `BoundSql` without `QueryAstScopedQuery`. |
| Report UI / physical ledger dump | Unchanged. |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. Encoding uses `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. No `AuditPort` rewrite. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | `#119` still gates new raw JDBC. |
