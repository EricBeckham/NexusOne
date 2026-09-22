# G4 follow-up: export audit + 90-day hot / archive Job

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. This package extends the existing M0-11 hash chain and the G1 `IsolatedExport` surface. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim G6 or G17.

## What this close covers

1. **Export actions on the existing hash chain.** Accepted `IsolatedExport` submit / writeFile / dispatch emit `EXPORT_ACCEPTED`. Product `AuditPortIsolationSink` persists them with `AuditPort.append` (`EXPORT` / `p3.export.submitted` / `p3.export.file.written` / `p3.export.dispatched`). `AuditPort.findByTraceId` / `AuditTrace.correlate` return them. Rejects stay on `appendNewTransaction` (`GUARD_REJECT`).
2. **90-day hot → archive Job path.** `AuditArchiveJob` / `audit.archive.copy` copies `platform.audit_record` rows older than 90 days into `platform.audit_record_archive` (Flyway **V502**). Re-runs are idempotent on `event_id`. The runner issues `PlatformContext` path `audit.archive` (not tenant `IsolatedJobScheduler` / `TaskQueue`, which require membership). Daily Quartz is opt-in (`nexus.audit.archive.schedule-enabled`, default **false**).

Same-TX `append` joining is unchanged: an export `append` inside a Spring TX rolls back with the business row (see `JdbcAuditChainTransactionTest.exportAppendJoinsSpringTransactionAndRollsBack`).

## Evidence

Recorded after the test run on this agent VM. Java **21**, Spring Boot **4.1.1**, Zonky PostgreSQL **15.18**. No Docker.

```
./scripts/g4-export-audit-archive.sh
```

| Suite | What it proved |
|---|---|
| `AuditCaptureAndChainTest` | Export + data on one trace; secrets redacted; chain intact |
| `JdbcAuditChainTransactionTest` | Export `append` joins Spring TX and rolls back with the chain head |
| `IsolatedExportTest` | Same-tenant submit/writeFile/dispatch emit `EXPORT_ACCEPTED`; cross-tenant still reject-before |
| `G4ExportAuditTest` | Accepted export events land on `AuditPort` and are queryable by trace |
| `AuditArchiveJobTest` / `JdbcAuditArchiveIT` | 91-day row copied; 89-day row stays hot-only; hot count unchanged; second run copies 0; `purgeImplemented=false` |
| `G4AuditArchiveJobTest` | Platform runner + TaskHandler copy aged rows; tenant context is `CTX_INVALID_PLATFORM` |
| `G1IsolationSurfacesTest` | Guard-reject path unchanged |

## Honest leftover G4 rows

This is **not** a product-wide G4 close.

| Still open | Why |
|---|---|
| Login / permission / admin on one real request | P2/P1 still do not emit those categories through `AuditPort` on a browser walk |
| Same-request login→permission→admin→export→guard-reject | Export + guard-reject are wired; the rest are not |
| Hot purge after 90 days | `platform.audit_record` is append-only (`REVOKE DELETE`). Archive is a **copy** |
| 365-day archive expiry | `AuditRetentionPolicy.ARCHIVE_RETENTION` is documented; `purgeExpiredArchive` returns `purgeImplemented=false` |
| Object-store / cold archive | Same-PG table only |
| Tenant TaskQueue / IsolatedQuartzJob for archive | Those APIs require membership; archive is a platform Job |
| FieldPolicy / report UI export | Same leftover as G1: `ledger.export.csv` is the guarded surface, not a physical ledger dump |
| G6 / G17 | Not this package |

G18 remains closed.
