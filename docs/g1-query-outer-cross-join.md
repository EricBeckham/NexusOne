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

Recorded after the targeted suite on this agent VM. Java / Maven / Boot / PG versions and counts are filled after `mvn test`.

```
# pending targeted suite — see follow-up commit
```

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close.

| Still open | Why |
|---|---|
| Nested subquery | Evidenced in [g1-query-nested-subquery.md](./g1-query-nested-subquery.md). |
| Product-wide G1 close | UI / H5 every page, report UI / physical ledger dump, and callers that skip `QueryCompiler` stay open. |
| DataScope / FieldPolicy compilation into AST | Unchanged from [g1-query-join-field-policy.md](./g1-query-join-field-policy.md). |
| Silent drop on API reads | Unchanged. Export still rejects hidden/second-auth instead of dropping. |
| `P3RequestContext` | Unchanged. New join kinds use `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. Guard-reject still uses `appendNewTransaction` only. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | Direct JDBC / raw SQL outside `QueryCompiler` is still a caller-wiring gap. |
