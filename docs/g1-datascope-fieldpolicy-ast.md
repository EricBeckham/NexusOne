# G1 remaining: DataScope / FieldPolicy compilation into Query AST

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. INNER / OUTER / CROSS JOIN, nested IN/EXISTS, batch write, sort, and page already landed on `QueryCompiler`. This package encodes **safe SELF/DEPT DataScope equality** and **FieldPolicy visibility** into the same M0-02 AST.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining. It does **not** claim a product-wide G1 close.

## What this close covers

`PermissionQueryBinder` / `FieldPolicyProjector` still sit beside the AST for OR trees, multi-value `IN`, and masks. Where the AST can represent the constraint (single-value equality / visible columns), `QueryAstPolicyCompiler` writes it onto `QueryAst.filters()` / `QueryAst.projection()` and `QueryCompiler` renders it after `TenantInjector`.

| Surface | Guard | What is encoded | Leftover |
|---|---|---|---|
| SELF / single-DEPT | `QueryAstPolicyCompiler` + `DataScopeInjector.andEquals` / `IsolationDataScopeInjector` | `owner_id = ?` / `department_id = ?` AND-ed after `tenant_id` | OR trees, multi-value `IN`, `DEPT_AND_CHILDREN`, CUSTOM |
| FieldPolicy visibility | `QueryAst.withProjection` | Hidden fields dropped from SELECT | Mask / second-auth still `FieldPolicyProjector` |
| Export FieldPolicy | `ExportFieldPolicyGuard.dropHidden` / `IsolatedExport.compile` | Hidden Query AST columns dropped before compile | Second-auth / empty leftover / unmapped still `P1.M02.021` — [g1-export-fieldpolicy-drop.md](./g1-export-fieldpolicy-drop.md) |

`tenant_id` stays on `TenantInjector` / `AccessContextHolder`. DataScope must not author it. The kernel `DataScopeInjector` may only AND extra equality atoms and still cannot strip a tenant predicate.

## Evidence

Recorded on this agent VM. Java **21.0.10**, Maven **3.9.16**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` uses in-process PostgreSQL **15.18** (Zonky). No Docker.

```
mvn -f modules/isolation-kernel/pom.xml test
# Tests run: 52, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS  2026-09-22T17:37:17Z

mvn -pl nexus-permission-core,nexus-bootstrap -am test \
    -Dtest=QueryAstPolicyCompilerTest,IsolationDataScopeInjectorTest,G1QueryDataScopeFieldPolicyAstTest,G1QueryJoinFieldPolicyTest,G1QueryAstRemainderTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T17:37:49Z
# QueryAstPolicyCompilerTest 3/3, IsolationDataScopeInjectorTest 5/5,
# G1QueryDataScopeFieldPolicyAstTest 1/1, G1QueryJoinFieldPolicyTest 1/1,
# G1QueryAstRemainderTest 1/1
```

| Suite | What it proved |
|---|---|
| `QueryCompilerTest` | SELF/DEPT equality atoms render beside `tenant_id`; `andEquals` cannot author `tenant_id`; hidden column dropped from projection before render |
| `PostgresIsolationIT` | Same-tenant SELF / DEPT returns only the bound owner/dept row; stolen tenant and DataScope stripping `tenant_id` never reach JDBC |
| `IsolatedExportTest` | Encoded SELF + visible projection compiles; mixed hidden is now dropped; second-auth / empty leftover still `P1.M02.021` |
| `QueryAstPolicyCompilerTest` | SELF/DEPT + hidden drop encoded; OR / multi-value IN leftover; `tenant_id` authorship rejected |
| `IsolationDataScopeInjectorTest` | Query AST injector ANDs `owner_id` without stripping `tenant_id`; kernel compile renders both |
| `G1QueryDataScopeFieldPolicyAstTest` | Encoded SELF/DEPT compile through `QueryCompiler`; export mixed hidden drops; hidden-only still `GUARD_REJECT` (`P1.M02.021`); OR leftover still `PermissionQueryBinder`; hash chain intact |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close and **not** a full G1 close.

| Still open | Why |
|---|---|
| OR / multi-value IN DataScope | Single-column `IN` (`DEPT_AND_CHILDREN` / SPECIFIED) is [g1-datascope-or-in-ast.md](./g1-datascope-or-in-ast.md). Mixed-column SELF∨DEPT stays on `PermissionQueryBinder`. |
| FieldPolicy mask / second-auth | AST / export drop hidden columns. Mask merge and second-auth still run in `FieldPolicyProjector`. Export still rejects second-auth without grant. See [g1-export-fieldpolicy-drop.md](./g1-export-fieldpolicy-drop.md). |
| Product list / detail SQL | Ledger list/detail now opt in ([g1-queryast-product-optin.md](./g1-queryast-product-optin.md)). `PermissionDataScopeHook` / `PermissionService.scopedQuery` still bind `ScopedQuery` strings for callers that skip. |
| Report UI / physical ledger dump | Report query HTTP inherits the list opt-in. Physical dump / report wizard stay open. `ledger.export.csv` is still the Task type. |
| Silent drop on API reads | Unchanged for callers that still project rows after fetch. AST drop is opt-in. |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. New AST encoding uses `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. Guard-reject still uses `appendNewTransaction` only. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | Direct JDBC / raw SQL outside `QueryCompiler` is still a caller-wiring gap. |
