# G1 remaining: Query AST joins + FieldPolicy on export

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. Export / file / cache / Job / Task guards already landed in [#71](https://github.com/EricBeckham/NexusOne/pull/71). This package extends the same M0-02 kernel for the two leftover G1 rows called out there.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining (G4 wiring is already on `main`).

## What this close covers

Cross-tenant **INNER JOIN** and export **FieldPolicy** violations are **rejected before JDBC / file sink**, and each reject emits:

1. `IsolationAuditEvent` (`ISOLATION_REJECT`) through the existing `IsolationAuditor`
2. `AuditPort.appendNewTransaction(AuditCaptures.guardReject(...))` in product (`GUARD_REJECT` / `isolation.guard.reject`)

| Surface | Guard | Code | Reject-before |
|---|---|---|---|
| Query AST INNER JOIN | `TenantInjector.injectJoin` / `TableAccessPlanGuard.checkJoin` | `P1.M02.020` | `Connection.prepareStatement` |
| Export FieldPolicy | `ExportFieldPolicyGuard` / `IsolatedExport.compile` / `writeProjected` | `P1.M02.021` | Query compile, file sink |

Joins reuse `QueryAst` + `QueryCompiler` + `GuardedJdbc`. Each tenant-scoped join table gets its own `tenant_id = ?`. FieldPolicy on export maps M0-05 `FieldRule` through `ExportFieldPolicies` and still uses `FieldPolicyProjector` for drop/mask after a visible projection is accepted.

## Evidence

Recorded on this agent VM. Java **21.0.10**, Maven **3.9.16**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` uses in-process PostgreSQL **15.18** (Zonky). No Docker.

```
mvn -f modules/isolation-kernel/pom.xml test
# Tests run: 28, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS  2026-09-22T15:41:22Z

mvn -pl nexus-bootstrap -am test \
  -Dtest=G1QueryJoinFieldPolicyTest,G1IsolationSurfacesTest \
  -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T15:42:22Z
# G1QueryJoinFieldPolicyTest 1/1, G1IsolationSurfacesTest 1/1
```

| Suite | What it proved |
|---|---|
| `QueryCompilerTest` | INNER JOIN injects `tenant_id` on both tables; foreign / null join tenant and tenant→platform join rejected with records; DataScope cannot strip a join tenant predicate |
| `PostgresIsolationIT` | Same-tenant join returns only the bound tenant's line; stolen join `tenant_id=beta` never reaches JDBC (`P1.M02.020`) |
| `IsolatedExportTest` | Hidden / missing / second-auth FieldPolicy rejected before compile or file sink (`P1.M02.021`); same-tenant visible projection compiles a join and writes CSV |
| `G1QueryJoinFieldPolicyTest` | Both codes land on `AuditPort` as `GUARD_REJECT` via `appendNewTransaction`; hash chain intact; `FieldPolicyProjector` drop/mask used on the allowed export |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close.

| Still open | Why |
|---|---|
| Subquery / batch write / sort / page AST | Evidenced in [g1-query-ast-remainder.md](./g1-query-ast-remainder.md) (`P1.M02.022`–`025`). Nested subquery-in-subquery is [g1-query-nested-subquery.md](./g1-query-nested-subquery.md). |
| OUTER JOIN / CROSS JOIN | Evidenced in [g1-query-outer-cross-join.md](./g1-query-outer-cross-join.md). |
| DataScope / FieldPolicy compilation into AST | M0-05 still merges FieldPolicy and compiles OR DataScope via `PermissionQueryBinder`. Kernel FieldPolicy is the export visibility gate, not a full projector/compiler. |
| Report UI / physical ledger dump | No report screen. `ledger.export.csv` is still the Task type; this package proves the compile / file FieldPolicy gate, not a product export wizard. |
| Silent drop on API reads | `FieldPolicyProjector` still drops hidden fields on UI/API rows. Export **rejects** a hidden/second-auth request instead of dropping it. |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. Joins / export FieldPolicy use `AccessContextHolder` only. |
| G4 same-request chain | Login / permission-change / admin / guard-reject wiring is on `main` ([g4-audit-coverage.md](./engineering/g4-audit-coverage.md)). Export + 90-day copy + 365-day same-PG purge: [g4-export-audit-archive.md](./g4-export-audit-archive.md). Same-trace MockMvc walk: [g4-same-trace-walk.md](./g4-same-trace-walk.md). Remaining G4 is browser PKCE / object-store cold / hot-row purge. Guard-reject still uses `appendNewTransaction` only. |
| GaussDB / live Redis / live MinIO | Unchanged from M0-10 / #71. |
| Platform-wide “every API” | Direct `QueryCompiler.compile` without `IsolatedExport.compile` is not a FieldPolicy gate. Callers that skip `IsolatedExport` for a dump still need product wiring. |
