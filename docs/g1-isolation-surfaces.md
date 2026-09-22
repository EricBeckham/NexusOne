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

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” marketing close.

| Still open | Why |
|---|---|
| UI / H5 every page | Vue `assert:isolation` and the dual-tenant HTTP script already cover client headers / IAM / notify. This package does not re-walk the browser. |
| Query AST joins / subquery / batch | INNER JOIN is [g1-query-join-field-policy.md](./g1-query-join-field-policy.md). Subquery / batch / sort / page are [g1-query-ast-remainder.md](./g1-query-ast-remainder.md). OUTER JOIN / CROSS JOIN remain open. |
| FieldPolicy on export | Visibility + second-auth reject-before-execute is evidenced in [g1-query-join-field-policy.md](./g1-query-join-field-policy.md). No report UI; API reads still drop/mask via `FieldPolicyProjector`. |
| `P3RequestContext` | Some P3 HTTP still has a request snapshot beside `AccessContextHolder`. Export / file / cache / Job / Task do not add a third holder and do not use that snapshot as tenant truth. |
| G4 same-request chain | Login / permission-change / admin / guard-reject wiring is on `main` ([g4-audit-coverage.md](./engineering/g4-audit-coverage.md)). Accepted export events + 90-day archive Job + 365-day same-PG purge: [g4-export-audit-archive.md](./g4-export-audit-archive.md). Same-trace MockMvc walk: [g4-same-trace-walk.md](./g4-same-trace-walk.md). Remaining G4 is browser PKCE / object-store cold archive / hot-row purge. This package only wires **guard-reject** onto `AuditPort.appendNewTransaction`. |
| GaussDB | PostgreSQL-only slice. |
| Live Redis 3.2 / live MinIO | Product optional drills: [g1-live-redis-minio.md](./g1-live-redis-minio.md). Ops dedicated-port drill (`ops/scripts/live-redis-minio-isolation-drill.sh`, [runbook](./engineering/runbooks/live-redis-minio-isolation.md)) — not a G1 kernel change. Skip when the service is absent. Memory / Fake Redis / counting storage remain the always-on reject-before-backend proof. |
| Platform-wide “every API” | IAM / P1 / notify / Query AST already had G1 rows on M0-12. Those stay as previously recorded; this package only closes the deferred export / file / cache / Job / Task guards. |
