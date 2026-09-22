# G1 leftover: DataScope multi-value IN into Query AST

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. `#115` encoded safe SELF/DEPT equality. `#130` opted product list/detail into that encoder. This package encodes **one concrete multi-value shape** — single-column `department_id IN (?, …)` used by `DEPT_AND_CHILDREN` and SPECIFIED lists — onto the same M0-02 AST and through `QueryCompiler`.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining. It does **not** claim a product-wide G1 close. Orthogonal to `#130` (product list/detail opt-in), `#134` (export FieldPolicy hidden drop, now on `main`), `#136` (SurfaceGuard deny audit), and `#140` (G7 expense/travel query).

## What this close covers

`QueryAst.filters()` may carry a non-empty `Collection` value. `PostgresRenderer` renders it as parameterized `IN (?, …)` after `TenantInjector` still owns scalar `tenant_id`. Empty `IN`, nested `IN`, null members, and `tenant_id IN` are rejected before JDBC.

| Surface | Guard | What is encoded | Leftover |
|---|---|---|---|
| `DEPT_AND_CHILDREN` / SPECIFIED lists | `QueryAstPolicyCompiler` + `QueryCompiler` | `department_id IN (?, …)` AND-ed after `tenant_id` | Mixed-column SELF∨DEPT |
| Same-column OR of equalities | Collapsed to the same `IN` | `department_id = d1 OR department_id = d2` | Different-column OR stays leftover |
| Empty / hostile `IN` | Fail-closed | Empty atom stays leftover `FALSE` on `PermissionQueryBinder`; empty/tenant `IN` on the AST is `INVALID_IDENTIFIER` / `CROSS_TENANT_READ` before JDBC | Callers must not skip `QueryCompiler` |
| Product list / detail | `#130` `QueryAstScopedQuery` | Fully encoded `IN` now takes the `kernel.jdbc()` path | Mixed OR and `COUNT(*)` wrap still `BoundSql` |

`tenant_id` stays on `TenantInjector` / `AccessContextHolder`. DataScope must not author it, including as `IN`.

## Evidence

Recorded on this agent VM. Java **21**, Maven **3.9.x**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` uses in-process PostgreSQL **15.x** (Zonky). No Docker.

```
mvn -f modules/isolation-kernel/pom.xml test
# pending this run

mvn -pl nexus-permission-core,nexus-bootstrap -am test \
    -Dtest=QueryAstPolicyCompilerTest,IsolationDataScopeInjectorTest,QueryAstScopedQueryTest,G1QueryDataScopeInAstTest,G1QueryDataScopeFieldPolicyAstTest,G1QueryAstProductOptInTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# pending this run
```

| Suite | What it proved |
|---|---|
| `QueryCompilerTest` | Multi-value `IN` renders beside `tenant_id`; empty `IN` is `P1.M02.007`; `tenant_id IN` is `P1.M02.003` before JDBC |
| `PostgresIsolationIT` | Same-tenant `department_id IN (d1,d2)` returns only those rows; empty / stolen-tenant `IN` never reach JDBC |
| `QueryAstPolicyCompilerTest` | `DEPT_AND_CHILDREN` / SPECIFIED / same-column OR encode; SELF∨DEPT and empty `IN` stay on binder (`tenant_id` first, empty → `FALSE`) |
| `QueryAstScopedQueryTest` | Product-shaped list with SPECIFIED `IN` compiles fully through `QueryCompiler` |
| `IsolationDataScopeInjectorTest` | Injector ANDs an `IN` list without stripping `tenant_id` |
| `G1QueryDataScopeInAstTest` | `DEPT_AND_CHILDREN` compiles through `QueryCompiler`; SELF∨DEPT leftover binder; hash chain intact |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close and **not** a full G1 close.

| Still open | Why |
|---|---|
| Mixed-column OR (SELF∨DEPT) | Query AST filters stay AND of column atoms. `owner_id = ? OR department_id = ?` remains `PermissionQueryBinder`. |
| FieldPolicy mask / second-auth | AST / export drop hidden columns (`#134`). Mask merge and second-auth still run in `FieldPolicyProjector`. |
| `COUNT(*)` wrap | Totals still wrap compiled SELECT text. Not a Query AST node. |
| Callers that still skip | `PermissionService.scopedQuery`, hook `.query()` strings, and any adapter that still builds `BoundSql` without `QueryAstScopedQuery`. |
| Report UI / physical ledger dump | Unchanged. |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. Encoding uses `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. Guard-reject still uses `appendNewTransaction` only. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | Direct JDBC / raw SQL outside `QueryCompiler` is still a caller-wiring gap. |
