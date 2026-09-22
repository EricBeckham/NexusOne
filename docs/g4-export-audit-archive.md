# G4 follow-up: export audit + 90-day hot / archive Job

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. This package extends the existing M0-11 hash chain and the G1 `IsolatedExport` surface. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim G6 or G17.

## What this close covers

1. **Export actions on the existing hash chain.** Accepted `IsolatedExport` submit / writeFile / dispatch emit `EXPORT_ACCEPTED`. Product `AuditPortIsolationSink` persists them with `AuditPort.append` (`EXPORT` / `p3.export.submitted` / `p3.export.file.written` / `p3.export.dispatched`). `AuditPort.findByTraceId` / `AuditTrace.correlate` return them. Rejects stay on `appendNewTransaction` (`GUARD_REJECT`).
2. **90-day hot → archive Job path.** `AuditArchiveJob` / `audit.archive.copy` copies `platform.audit_record` rows older than 90 days into `platform.audit_record_archive` (Flyway **V502**). Re-runs are idempotent on `event_id`. The runner issues `PlatformContext` path `audit.archive` (not tenant `IsolatedJobScheduler` / `TaskQueue`, which require membership). Daily Quartz is opt-in (`nexus.audit.archive.schedule-enabled`, default **false**).

Same-TX `append` joining is unchanged: an export `append` inside a Spring TX rolls back with the business row (see `JdbcAuditChainTransactionTest.exportAppendJoinsSpringTransactionAndRollsBack`).

## Evidence

Recorded **2026-09-22T15:43:00Z** on this agent VM. `./scripts/g4-export-audit-archive.sh` → **BUILD SUCCESS**. No Docker. No live `127.0.0.1:5432`.

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** (`starting PostgreSQL 15.18 on x86_64-pc-linux-gnu`) |
| Flyway | V501 + **V502** on schema `platform` |
| Result | isolation-kernel `IsolatedExportTest` **4/4** after `#87` FieldPolicy on `main`; nexus-audit-core specified **13/13**; nexus-bootstrap specified **4/4** |

```
./scripts/g4-export-audit-archive.sh
# BUILD SUCCESS  2026-09-22T15:43:00Z
```

| Suite | Result | What it proved |
|---|---|---|
| `IsolatedExportTest` | **4/4** | Same-tenant submit/writeFile/dispatch emit `EXPORT_ACCEPTED`; cross-tenant still reject-before; FieldPolicy `P1.M02.021` from `main` (#87) unchanged |
| `AuditCaptureAndChainTest` | **4/4** | Export + data on one trace; secrets redacted; chain intact |
| `AuditFlywayContractTest` | **2/2** | V501 append-only; V502 EXPORT + archive copy, no hot DELETE |
| `JdbcAuditChainTransactionTest` | **5/5** | Existing same-TX rules plus export `append` rolls back with the chain head |
| `JdbcAuditArchiveIT` | **1/1** | 91-day row copied on PG 15; 89-day stays hot-only; hot count unchanged; second run copies 0 |
| `AuditArchiveJobTest` | **1/1** | In-memory 90-day copy; `purgeImplemented=false` |
| `G4ExportAuditTest` | **1/1** | Accepted export events land on `AuditPort` and are queryable by trace |
| `G4AuditArchiveJobTest` | **2/2** | Platform runner + TaskHandler copy aged rows; tenant context is `CTX_INVALID_PLATFORM` |
| `G1IsolationSurfacesTest` | **1/1** | Guard-reject path unchanged |

## Honest leftover G4 rows

This is **not** a product-wide G4 close.

| Still open | Why |
|---|---|
| Login / permission / admin / guard-reject wiring | Already on `main` via #77 ([g4-audit-coverage.md](./engineering/g4-audit-coverage.md)); this PR does not redo it |
| Same-request login→permission→admin→export→guard-reject | Categories are wired; one real browser request is still open |
| Hot purge after 90 days | `platform.audit_record` is append-only (`REVOKE DELETE`). Archive is a **copy** |
| 365-day archive expiry | `AuditRetentionPolicy.ARCHIVE_RETENTION` is documented; `purgeExpiredArchive` returns `purgeImplemented=false` |
| Object-store / cold archive | Same-PG table only |
| Tenant TaskQueue / IsolatedQuartzJob for archive | Those APIs require membership; archive is a platform Job |
| FieldPolicy / report UI export | Same leftover as G1: `ledger.export.csv` is the guarded surface, not a physical ledger dump |
| G6 / G17 | Not this package |

G18 remains closed.
