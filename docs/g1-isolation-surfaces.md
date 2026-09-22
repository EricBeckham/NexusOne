# G1 remaining isolation surfaces (export / file / cache / Job / Task)

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. This package extends the M0-02 isolation kernel and the M0-09/10 file / cache / task / outbox boundaries already on `main`. It does **not** invent a second tenant context.

## What this close covers

Cross-tenant attempts on these four surfaces are **rejected before the sink / handler runs**, and each reject emits:

1. `IsolationAuditEvent` (`ISOLATION_REJECT`) through the existing `IsolationAuditor`
2. `AuditPort.appendNewTransaction(AuditCaptures.guardReject(...))` in product (`GUARD_REJECT` / `isolation.guard.reject`)

Same-TX `AuditPort.append` joining is unchanged (parallel G4).

| Surface | Guard | Code | Reject-before |
|---|---|---|---|
| Export | `ExportGuard` / `IsolatedExport` | `P1.M02.019` | Task submit, file write, cache status, handler body |
| File | `FileObjectKeyGuard` | `P1.M02.015` | S3 / `ObjectStoragePort` |
| Cache | `CacheKeyGuard` | `P1.M02.014` | Redis / memory map |
| Job / Task | `IsolatedEnvelope` / `SideEffectGuard` | `P1.M02.016` | Quartz handler / Task handler / Outbox dispatcher |

All four reuse `AccessContextHolder` + `IsolationKernel.create`. `ExecutionIsolation` wraps the product kernel when a DataSource exists; it does not install a second holder.

## Evidence

Recorded **2026-09-22T15:28:41Z** on this agent VM. Java **21.0.10**, Maven **3.9.16**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` and Task Queue use in-process PostgreSQL **15.18** (Zonky). No Docker.

```
mvn -f modules/isolation-kernel/pom.xml test
# Tests run: 22, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS  2026-09-22T15:28:16Z

mvn -pl nexus-cache-core,nexus-file-core,nexus-event-core,nexus-task-core,nexus-bootstrap -am test \
  -Dtest=CachePortIsolationTest,FileServiceIsolationTest,FileApiTest,TaskQueueContractTest,ExecutionIsolationTest,G1IsolationSurfacesTest,EventCoreConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T15:28:41Z
```

| Suite | Count | What it proved |
|---|---|---|
| `IsolatedExportTest` | 2/2 | Same-tenant export may run file/cache/task sinks; cross-tenant export never does; ≥4 `ISOLATION_REJECT` / `P1.M02.019` |
| `PropagationContractTest` | 5/5 | Cache / file / async rejects emit guard records and do not call sinks |
| `QueryCompilerTest` | 4/4 | Query AST rejects still recorded (unchanged) |
| `CachePortIsolationTest` | 5/5 | Foreign Redis key rejected; command count unchanged; `P1.M02.014` recorded |
| `FileServiceIsolationTest` | 5/5 | Cross-tenant signed URL rejected; `presignGet` count stays 0; `P1.M02.015` recorded |
| `FileApiTest` | 1/1 | HTTP signed-url still `P1.M02.015` |
| `TaskQueueContractTest` | 5/5 | Cross-tenant envelope / `IsolatedExport.dispatch` rejected before handler; `P1.M02.016` / `P1.M02.019` recorded |
| `ExecutionIsolationTest` | 3/3 | Quartz restore of a foreign envelope rejected before handler; `P1.M02.016` recorded |
| `EventCoreConfigurationTest` | 5/5 | Product auto-config still registers Outbox from the shared kernel |
| `G1IsolationSurfacesTest` | 1/1 | All four codes land on `AuditPort` as `GUARD_REJECT` via `appendNewTransaction`; hash chain intact |

## G1 remainder: Query AST joins + FieldPolicy on export

S0+S1 overnight follow-up on #71. Reuses M0-02 only. Does **not** change `AuditPort.append` same-TX joining. Not a product-wide G1 zero-leak close.

| Surface | Guard | Code | Reject-before |
|---|---|---|---|
| Query AST INNER JOIN | `TenantInjector.injectJoin` / `TableAccessPlanGuard` | `P1.M02.003` | `Connection.prepareStatement` |
| Export Query compile (foreign resource) | `ExportGuard` then compiler | `P1.M02.019` | Query compile / JDBC |
| Export Query JOIN (foreign join tenant) | `IsolatedExport.compileQuery` / `query` | `P1.M02.003` | Query compile / JDBC |
| FieldPolicy on export | `IsolatedExport.applyFieldPolicy` / `writeProjected` | `P1.M02.019` / `P1.M02.020` | File / Task sink |

Recorded **2026-09-22T15:41:16Z** on this agent VM. Java **21.0.10**, Maven **3.8.7**, Spring Boot **4.1.1**. Isolation-kernel `PostgresIsolationIT` uses in-process PostgreSQL **15.18** (Zonky). No Docker. `AuditPort.append` same-TX joining is unchanged.

```
mvn -f modules/isolation-kernel/pom.xml test
# Tests run: 28, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS  2026-09-22T15:39:51Z

mvn -pl nexus-cache-core,nexus-file-core,nexus-event-core,nexus-task-core,nexus-bootstrap -am test \
  -Dtest=CachePortIsolationTest,FileServiceIsolationTest,FileApiTest,TaskQueueContractTest,ExecutionIsolationTest,G1IsolationSurfacesTest,EventCoreConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T15:41:16Z
```

| Suite | Count | What it proved |
|---|---|---|
| `QueryCompilerTest` | 7/7 | INNER JOIN injects `tenant_id` on both tables; foreign join filter is `P1.M02.003` with a guard record; hostile DataScope cannot strip the join tenant predicate |
| `IsolatedExportTest` | 4/4 | Existing export guards plus join compile reject (`P1.M02.019` / `P1.M02.003`) and FieldPolicy drop-hidden / missing-policy (`P1.M02.020`) / row-tenant reject before file sink |
| `PostgresIsolationIT` | 2/2 | Same-tenant join returns only alpha rows on PG 15.18; foreign join / export query never reach JDBC (`unusable` Connection); FieldPolicy drops `hidden` after the guarded query |
| `G1IsolationSurfacesTest` | 2/2 | Join `P1.M02.003` and FieldPolicy export `P1.M02.019` land on `AuditPort` as `GUARD_REJECT` via `appendNewTransaction`; hash chain intact |
| `#71` file/cache/task suites | unchanged | Cache 5/5, File 5+1, Task 5/5, Execution 3/3, EventCore 5/5 |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” marketing close.

| Still open | Why |
|---|---|
| UI / H5 every page | Vue `assert:isolation` and the dual-tenant HTTP script already cover client headers / IAM / notify. This package does not re-walk the browser. |
| Query AST subquery / batch / sort / paging | INNER JOIN equality + per-table `tenant_id` is in. Subquery, batch write, sort/paging stay open (M0-02 `GAPS.md`). |
| Report UI / physical ledger dump | IsolatedExport now compiles joins and applies FieldPolicy before file write. There is still no report UI and no product HTTP export endpoint. |
| FieldPolicy AST compilation | Export uses the M0-05 projector seam. Compiling FieldPolicy into Query AST remains M0-05. |
| `P3RequestContext` | Some P3 HTTP still has a request snapshot beside `AccessContextHolder`. Export / file / cache / Job / Task do not add a third holder and do not use that snapshot as tenant truth. |
| G4 same-request chain | Login / permission / admin / export / guard-reject on one real browser request remains G4. This package only wires **guard-reject** onto `AuditPort.appendNewTransaction`. |
| GaussDB / live Redis 3.2 / live MinIO | Same as M0-10: memory / Fake Redis / counting storage prove reject-before-backend. |
| Platform-wide “every API” | IAM / P1 / notify / single-table Query AST already had G1 rows on M0-12. Join + FieldPolicy export are evidenced here; that is not every API. |
