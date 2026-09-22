# G1 leftover: export-path FieldPolicy drop on Query AST projection

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. SELF/DEPT equality + FieldPolicy visibility encoding already landed in [#115](https://github.com/EricBeckham/NexusOne/pull/115). This leftover makes **export compile / writeProjected drop hidden columns** instead of only rejecting them, using the same visibility encoding as `QueryAstPolicyCompiler`.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining. It does **not** claim a product-wide G1 close. Product list/detail Query AST opt-in is already on `main` ([#130](https://github.com/EricBeckham/NexusOne/pull/130)); this leftover does not redo it. Residual BoundSql / COUNT / export FieldPolicy drop PASS/SKIP beyond #191/#146 is [g1-queryast-export-honesty.md](./g1-queryast-export-honesty.md).

## What this leftover covers

| Surface | Guard | What changed | Still reject (`P1.M02.021`) |
|---|---|---|---|
| Export Query AST compile | `ExportFieldPolicyGuard.dropHidden` + `IsolatedExport.compile` | Hidden columns dropped from `QueryAst.projection()` before `QueryCompiler` | Empty leftover, unmapped field, empty policy, second-auth without grant |
| Export file write | `IsolatedExport.writeProjected` | Hidden columns omitted from CSV | Same leftover rejects as compile |
| Mask / second-auth | `FieldPolicyProjector` | Unchanged | Second-auth without grant still fail-closes on export |

`tenant_id` stays on `TenantInjector` / `AccessContextHolder`. DataScope must not author it. Drop is silent (no `ISOLATION_REJECT`); leftover rejects still emit `IsolationAuditEvent` + product `AuditPort.appendNewTransaction` (`GUARD_REJECT` / `P1.M02.021`).

## Evidence

Recorded on this agent VM after rebase onto `main@47d04c7` (#125). Java **21.0.10**, Maven **3.9.16**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` uses in-process PostgreSQL **15.18** (Zonky). No Docker.

```
mvn -f modules/isolation-kernel/pom.xml test
# evidence pending this agent run

mvn -pl nexus-permission-core,nexus-bootstrap -am test \
    -Dtest=ExportFieldPolicyGuardTest,IsolatedExportTest,QueryAstPolicyCompilerTest,G1ExportFieldPolicyDropTest,G1QueryDataScopeFieldPolicyAstTest,G1QueryJoinFieldPolicyTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# evidence pending this agent run
```

| Suite | What it proved |
|---|---|
| `ExportFieldPolicyGuardTest` | Hidden drop; qualified-name drop; second-auth / unmapped / empty leftover / missing policy / missing context still reject |
| `IsolatedExportTest` | Mixed hidden Query AST compiles without `secret`; CSV omits hidden; second-auth / empty leftover / unmapped still `P1.M02.021` |
| `G1ExportFieldPolicyDropTest` | Encoded SELF + export drop through `QueryCompiler`; second-auth write still `GUARD_REJECT`; `FieldPolicyProjector` mask leftover; hash chain intact |
| `G1QueryDataScopeFieldPolicyAstTest` | #115 encode still drops hidden onto AST; export mixed hidden now compiles; hidden-only still `P1.M02.021` |
| `G1QueryJoinFieldPolicyTest` | Stolen join still `P1.M02.020`; mixed hidden export now drops; second-auth / hidden-only still `P1.M02.021` |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close and **not** a full G1 close.

| Still open | Why |
|---|---|
| OR / multi-value IN DataScope | Query AST filters are single-value equality. `DEPT_AND_CHILDREN`, SPECIFIED lists, and SELF∨DEPT stay on `PermissionQueryBinder`. |
| FieldPolicy mask / second-auth | Honesty leftover: [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md). AST / export can drop hidden columns. Mask merge and second-auth still run in `FieldPolicyProjector`. Export still rejects second-auth without grant. |
| Product list / detail SQL | Ledger list/detail now opt in ([g1-queryast-product-optin.md](./g1-queryast-product-optin.md), #130). `PermissionDataScopeHook` / `PermissionService.scopedQuery` still bind `ScopedQuery` strings. Not this leftover. |
| Report UI / physical ledger dump | Unchanged. `ledger.export.csv` is still the Task type. |
| Silent drop on API reads | Unchanged for callers that still project rows after fetch. AST / export drop is opt-in (`IsolatedExport.compile` / `encode`). |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. Export FieldPolicy uses `AccessContextHolder` only. |
| G4 same-request chain | Unchanged. Guard-reject still uses `appendNewTransaction` only. |
| GaussDB / live Redis / live MinIO | Unchanged. |
| Platform-wide “every API” | Direct JDBC / raw SQL outside `QueryCompiler` is still a caller-wiring gap. |
