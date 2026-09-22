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

Recorded on this agent VM. Java 21, Maven 3.8.7, Spring Boot **4.1.1**, in-process PostgreSQL **15.18** (Zonky) where a module already used it. No Docker.

Commands and counts are filled after the test run in this PR.

| Suite | What it proved |
|---|---|
| `IsolatedExportTest` | Same-tenant export may run file/cache/task sinks; cross-tenant export never does; ≥4 `ISOLATION_REJECT` / `P1.M02.019` |
| `PropagationContractTest` | Cache / file / async rejects emit guard records and do not call sinks |
| `CachePortIsolationTest` | Foreign Redis key rejected; command count unchanged; `P1.M02.014` recorded |
| `FileServiceIsolationTest` | Cross-tenant signed URL rejected; `presignGet` count stays 0; `P1.M02.015` recorded |
| `TaskQueueContractTest` | Cross-tenant envelope / `IsolatedExport.dispatch` rejected before handler; `P1.M02.016` / `P1.M02.019` recorded |
| `ExecutionIsolationTest` | Quartz restore of a foreign envelope rejected before handler; `P1.M02.016` recorded |
| `G1IsolationSurfacesTest` | All four codes land on `AuditPort` as `GUARD_REJECT` via `appendNewTransaction`; hash chain intact |

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” marketing close.

| Still open | Why |
|---|---|
| UI / H5 every page | Vue `assert:isolation` and the dual-tenant HTTP script already cover client headers / IAM / notify. This package does not re-walk the browser. |
| Query AST joins / subquery / batch | Still single-table equality + IS NULL (M0-02 `GAPS.md`). |
| FieldPolicy on export | `IsolatedExport` is the guarded surface + Task type `ledger.export.csv`. No report UI, no FieldPolicy projection of a physical ledger dump. |
| `P3RequestContext` | Some P3 HTTP still has a request snapshot beside `AccessContextHolder`. Export / file / cache / Job / Task do not add a third holder and do not use that snapshot as tenant truth. |
| G4 same-request chain | Login / permission / admin / export / guard-reject on one real browser request remains G4. This package only wires **guard-reject** onto `AuditPort.appendNewTransaction`. |
| GaussDB / live Redis 3.2 / live MinIO | Same as M0-10: memory / Fake Redis / counting storage prove reject-before-backend. |
| Platform-wide “every API” | IAM / P1 / notify / Query AST already had G1 rows on M0-12. Those stay as previously recorded; this package only closes the deferred export / file / cache / Job / Task guards. |
