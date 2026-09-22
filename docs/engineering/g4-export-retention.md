# G4 follow-up: export audit + 90-day hot retention Job

S0+S1 PostgreSQL slice. Does **not** redo P0/M0. Does **not** redesign `AuditPort` same-TX joining. Does **not** claim G6 / G17 or a full G4 product close (login / permission / admin on one real request stay with the parallel coverage PR; 365-day archive is deferred).

## Same-TX rules (unchanged)

| Method | Use | Behavior |
|---|---|---|
| `append` | accepted export (`p3.export.submitted` / `.file_written` / `.dispatched`) | Joins the ambient Spring TX (`DataSourceUtils`). Rollback clears the record + chain head. |
| `appendNewTransaction` | cross-tenant export reject (`P1.M02.019`) | Private commit so the guard-reject survives business rollback. Must not be called from a TX that already holds the chain lock via `append`. |

`JdbcHashChainedAuditPort` is not redesigned. Category CHECK stays V501 (`LOGIN` / `PERMISSION` / `ADMIN` / `DATA` / `APPROVAL` / `GUARD_REJECT`). Export events use **`DATA`** + distinct event types so no Flyway change is required.

## Export audit

Product `AuditedExport` calls G1 `IsolatedExport` first, then `AuditPort.append`. Details are ids / codes / byte counts only — no ledger field values (FieldPolicy dump remains a G1 leftover).

| Operation | Event type | Query |
|---|---|---|
| `submit` | `p3.export.submitted` | `AuditPort.findByTraceId` / `GET /api/v1/audit/traces/{traceId}` |
| `writeFile` | `p3.export.file_written` | same |
| `dispatch` | `p3.export.dispatched` | same |

HTTP: `POST /api/v1/p3/exports` (`{"ledger_code":"leave"}`). Isolation is the existing `IssuedTenantIsolationFilter` on `/api/v1/p3/*`. CSV body is the ledger code only.

## 90-day hot retention Job

`AuditRetentionPolicy`: hot **90** days, archive **365** days (ARCH-008 / ARCH-CMP-003). Not the application-log cleanup policy.

`AuditHotRetentionJob` (`audit.retention.hot-scan`) is a **runnable stub**:

- Classifies `platform.audit_record` / in-memory chain as hot vs past-hot
- Never `DELETE` / `UPDATE` append-only rows
- Never copies to a 365-day archive (`ARCHIVE_IMPLEMENTED = false`)
- Registered as a Spring bean + `TaskHandler` so product boot stays green
- **Not** scheduled through `IsolatedJobScheduler` at boot (that API requires a tenant membership)

## Proven vs still open

**Proven** (agent VM **2026-09-22T15:41:02Z**, OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**, Zonky PG **15.18**):

- Accepted export events on the hash chain, findable by `trace_id` (`G4ExportAuditIT` 2/2; `AuditCaptureAndChainTest` 4/4)
- Export `append` rolls back with the business TX; commit is queryable by trace (`JdbcAuditChainTransactionTest` **5/5**, including the original 4 same-TX cases)
- Cross-tenant export still writes `GUARD_REJECT` via `appendNewTransaction` and does not write `p3.export.*`
- Retention Job classifies 90-day hot / past-hot, does not drop rows, reports archive deferred (`AuditHotRetentionJobTest` 2/2; handler 1/1)
- Product boot still wires `AuditHotRetentionJob` + `AuditedExport` (`NexusOneProductBootTest` 1/1)
- `IssuedTenantIsolationFilter` handles `POST /api/v1/p3/exports` (5/5)

**Still open:**

- Full G4 one-request chain (login → permission → admin → data → approval → export → guard-reject)
- 365-day archive store / Job
- FieldPolicy projection of a physical `l_*` dump
- Isolated Quartz cron for the platform scan
- G6 / G17 / target Linux/ARM Profile

## Tests

Recorded **2026-09-22T15:41:02Z**. No Docker. Live `127.0.0.1:5432` not required (Zonky).

```bash
mvn -pl nexus-audit-core,nexus-bootstrap -am test \
  -Dtest=AuditCaptureAndChainTest,AuditHotRetentionJobTest,JdbcAuditChainTransactionTest,AuditFlywayContractTest,G4ExportAuditIT,AuditHotRetentionHandlerTest,NexusOneProductBootTest \
  -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T15:41:02Z
# JdbcAuditChainTransactionTest 5/5
# G4ExportAuditIT 2/2
# NexusOneProductBootTest 1/1

mvn -pl nexus-p3-studio -am test \
  -Dtest=IssuedTenantIsolationFilterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T15:41:20Z  (5/5)
```
