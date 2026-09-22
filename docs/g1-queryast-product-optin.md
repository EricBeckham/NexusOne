# G1 leftover: product list/detail opt-in to Query AST policy encode

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. `#115` already landed `QueryAstPolicyCompiler.encode` for **safe SELF/DEPT equality** and **FieldPolicy visibility**. This package wires the high-traffic product list/detail path (`GuardedLedgerStore`) to opt in before `QueryCompiler`.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining. It does **not** claim a product-wide G1 close. Orthogonal to `#119` (QueryCompiler-bypass CI), `#122` (SurfaceGuard-bypass CI), `#123` (90-day hot purge), `#124` (report SurfaceGuard), `#125` (desktop PKCE click), `#126` (H5 PKCE), `#127` (Linux/ARM profile lifecycle), `#128` (G9 in-process partition), `#129` (Nginx/Tongsuo dual-stack), `#131` (claim-list sync), `#132` (WeChat/DingTalk host stub), and `#133` (TOTP enrollment UX).

## What this close covers

`PermissionQueryBinder` / `FieldPolicyProjector` still sit beside the AST for OR trees, multi-value `IN`, `DEPT_AND_CHILDREN`, masks, and second-auth. `QueryAstScopedQuery` encodes what the AST can represent, compiles through `QueryCompiler` (TenantInjector still owns `tenant_id`), then ANDs leftover scope onto the rendered SQL before `ORDER BY` / `LIMIT`.

| Surface | Guard | What is encoded | Leftover |
|---|---|---|---|
| Ledger list / detail | `GuardedLedgerStore` + `QueryAstScopedQuery` | SELF / single-DEPT equality + hidden columns dropped from SELECT | OR / IN / `DEPT_AND_CHILDREN` still `PermissionQueryBinder`; mask / second-auth still `FieldPolicyProjector` |
| Ledger total | `QueryAstScopedQuery.count` | Same encoded WHERE, wrapped as `SELECT count(*) FROM (…)` | `COUNT(*)` is not a Query AST node |
| Report query HTTP | Reuses `LedgerRecordService.list` → `GuardedLedgerStore` | Same opt-in as ledger list | Physical dump / report UI wizard stay open (`#124` is G2 SurfaceGuard only) |
| Hook `ScopedQuery` strings | `PermissionDataScopeHook.bind` | Unchanged string binder for callers that still read `.query()` | `PermissionService.scopedQuery` still skips the AST |

Fully encoded SELF/DEPT list/detail execute through `kernel.jdbc().executeQuery(RenderedSql)`. Leftover OR/IN and the count wrap still use `BoundSql` + `PreparedStatement` on the allowlisted `GuardedLedgerStore` adapter.

## Evidence

Recorded on this agent VM. Java **21**, Maven **3.9.x**, Spring Boot **4.1.1**. No Docker. Isolation-kernel compile path is in-process (no live PostgreSQL required for this package).

```
mvn -pl nexus-permission-core,nexus-bootstrap -am test \
    -Dtest=QueryAstScopedQueryTest,QueryAstPolicyCompilerTest,G1QueryAstProductOptInTest,G1QueryDataScopeFieldPolicyAstTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS (suite counts recorded after the Maven run)
```

| Suite | What it proved |
|---|---|
| `QueryAstScopedQueryTest` | Product-shaped list/detail/count compile SELF through `QueryCompiler`; leftover OR is attached before `ORDER BY`/`LIMIT`; `tenant_id` authorship still rejected |
| `G1QueryAstProductOptInTest` | Physical-table-shaped list/detail encode SELF + drop hidden; leftover OR stays on binder; mask/second-auth still `FieldPolicyProjector`; export hidden still `P1.M02.021`; hash chain intact |
| `QueryAstPolicyCompilerTest` | Encoder leftovers unchanged (OR / multi-value IN / `tenant_id` reject) |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close and **not** a full G1 close.

| Still open | Why |
|---|---|
| OR / multi-value IN / `DEPT_AND_CHILDREN` | Query AST filters are single-value equality. Leftover scope still ANDs via `PermissionQueryBinder`. |
| FieldPolicy mask / second-auth | AST drops hidden columns on the opted-in path. Mask merge and second-auth still run in `FieldPolicyProjector`. Export still rejects instead of dropping. |
| `COUNT(*)` wrap | Totals wrap compiled SELECT text. Not a Query AST node. |
| Callers that still skip | `PermissionService.scopedQuery`, `PermissionDataScopeHook` / `TenantOnlyDataScopeHook` `.query()` strings, G2 agreement tests that read `ScopedQuery.whereSql()`, and any adapter that still builds `BoundSql` without `QueryAstScopedQuery`. |
| Report UI / physical ledger dump | `#124` put report query/export on `SurfaceGuard`. Query HTTP now inherits this list opt-in. Physical dump / `pg_dump` / a report wizard stay open. |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. Encoding uses `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. No `AuditPort` rewrite. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | `#119` still gates new raw JDBC. Allowlisted leftover `BoundSql` on `GuardedLedgerStore` remains for OR/IN and count. |
