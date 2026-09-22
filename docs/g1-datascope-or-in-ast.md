# G1 leftover: DataScope multi-value IN + SELF∨DEPT OR into Query AST

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. `#115` encoded safe SELF/DEPT equality. `#130` opted product list/detail into that encoder. `#142` encoded single-column `department_id IN (?, …)`. This leftover encodes **cross-column SELF∨DEPT OR** onto the same M0-02 AST as `QueryAst.disjunctions()` and through `QueryCompiler`.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining. It does **not** claim a product-wide G1 close. Orthogonal to `#130` (product list/detail opt-in), `#134` (export FieldPolicy hidden drop), `#146` (leftover BoundSql / COUNT guard), and `#162` (FieldPolicy mask / second-auth honesty). Keep both leftover rows (`g1-datascope-or-in-ast` and `g1-fieldpolicy-mask-second-auth`) in [README.md](./README.md).

## What this close covers

`QueryAst.filters()` may carry a non-empty `Collection` value. `QueryAst.disjunctions()` may carry a `QueryOr` of column atoms. `PostgresRenderer` renders IN as parameterized `IN (?, …)` and OR as `AND ((col = ?) OR (col = ?))` after `TenantInjector` still owns scalar `tenant_id`. Empty `IN`, nested `IN`, null members, `tenant_id IN`, and `tenant_id` inside an OR branch are rejected before JDBC.

| Surface | Guard | What is encoded | Leftover |
|---|---|---|---|
| `DEPT_AND_CHILDREN` / SPECIFIED lists | `QueryAstPolicyCompiler` + `QueryCompiler` | `department_id IN (?, …)` AND-ed after `tenant_id` | Empty `IN` stays binder `FALSE` |
| Same-column OR of equalities | Collapsed to the same `IN` | `department_id = d1 OR department_id = d2` | Unsupported operators |
| Mixed-column SELF∨DEPT | `QueryAst.disjunctions()` | `("owner_id" = ?) OR ("department_id" = ?)` AND-ed after `tenant_id` | Unencodable columns / AND of leftover OR |
| Empty / hostile `IN` / tenant in OR | Fail-closed | Empty atom stays leftover `FALSE` on `PermissionQueryBinder`; empty/tenant `IN` and `tenant_id` OR branch are `INVALID_IDENTIFIER` / `CROSS_TENANT_READ` before JDBC | Callers must not skip `QueryCompiler` |
| Product list / detail | `#130` `QueryAstScopedQuery` | Fully encoded `IN` / SELF∨DEPT now take the `kernel.jdbc()` path | `COUNT(*)` wrap is guarded ([g1-queryast-boundsql-guard.md](./g1-queryast-boundsql-guard.md)), not an AST node |

`tenant_id` stays on `TenantInjector` / `AccessContextHolder`. DataScope must not author it, including as `IN` or as an OR branch.

## Evidence

Recorded on this agent VM after rebase onto `origin/main` `845df2b` (#160). Java **21**, Maven **3.9.x**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` uses in-process PostgreSQL **15.x** (Zonky). No Docker.

```
mvn -f modules/isolation-kernel/pom.xml test
# pending this run

mvn -pl nexus-permission-core,nexus-bootstrap -am test \
    -Dtest=QueryAstPolicyCompilerTest,IsolationDataScopeInjectorTest,QueryAstScopedQueryTest,G1QueryDataScopeInAstTest,G1QueryDataScopeFieldPolicyAstTest,G1QueryAstProductOptInTest,G1QueryAstBoundSqlGuardTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# pending this run
```

| Suite | What it proved |
|---|---|
| `QueryCompilerTest` | Multi-value `IN` and mixed-column OR render beside `tenant_id`; empty `IN` is `P1.M02.007`; `tenant_id IN` / `tenant_id` OR branch is `P1.M02.003` before JDBC |
| `PostgresIsolationIT` | Same-tenant `department_id IN (d1,d2)` and SELF∨DEPT OR return only matching rows; empty / stolen-tenant `IN` / OR never reach JDBC |
| `QueryAstPolicyCompilerTest` | `DEPT_AND_CHILDREN` / SPECIFIED / same-column OR / SELF∨DEPT encode; empty `IN` and unencodable leftover stay on binder (`tenant_id` first, empty → `FALSE`) |
| `QueryAstScopedQueryTest` | Product-shaped list with SPECIFIED `IN` and SELF∨DEPT compile fully through `QueryCompiler` |
| `IsolationDataScopeInjectorTest` | Injector ANDs an `IN` list without stripping `tenant_id` |
| `G1QueryDataScopeInAstTest` | `DEPT_AND_CHILDREN` and SELF∨DEPT compile through `QueryCompiler`; leftover empty `IN` binder; hash chain intact |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close and **not** a full G1 close.

| Still open | Why |
|---|---|
| Unencodable leftovers | Columns outside `ENCODABLE_COLUMNS`, unsupported operators, empty `IN` stay on `PermissionQueryBinder`. |
| FieldPolicy mask / second-auth | Honesty leftover: [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md). AST / export drop hidden columns (`#134`). Mask merge and second-auth still run in `FieldPolicyProjector`. |
| `COUNT(*)` wrap | Totals still wrap compiled SELECT text. Not a Query AST node. |
| Callers that still skip | `PermissionService.scopedQuery`, hook `.query()` strings, and any adapter that still builds `BoundSql` without `QueryAstScopedQuery`. |
| Report UI / physical ledger dump | Unchanged. |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. Encoding uses `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. Guard-reject still uses `appendNewTransaction` only. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | Direct JDBC / raw SQL outside `QueryCompiler` is still a caller-wiring gap. |
