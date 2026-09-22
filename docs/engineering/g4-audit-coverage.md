# G4 audit coverage (login / permission-change / admin / guard-reject)

S0+S1 PostgreSQL release slice. Extends the **existing** M0-11 `AuditPort` JDBC hash chain. Does **not** redo P0/M0 packages, does **not** redesign transaction joining, and does **not** claim G6 / G17 or a full G4 product close (90-day hot + 365-day archive, export audit).

G18 (data + approval on the same chain) stays closed; see [g18-business-path.md](../g18-business-path.md).

## Same-TX rules (unchanged)

These rules already landed on `main` with G18 / M0-11. This package only **uses** them.

| Method | When | TX behavior |
|---|---|---|
| `AuditPort.append` | In-path writes: login accepted, logout, permission grant/assign, admin accepted (tenant create/enable/disable/config, quota consume/release) | Joins the ambient Spring TX via `DataSourceUtils`. Business rollback rolls the hash-chain INSERT and `audit_chain_meta` head back with the business row. |
| `AuditPort.appendNewTransaction` | Reject-path writes that must survive: login rejected, P1 disable/quota reject, isolation guard-reject | Private connection + commit. Must **not** be called from a TX that already holds the chain lock via `append` (deadlock on `audit_chain_meta`). |

`JdbcHashChainedAuditPort` itself is not redesigned. Evidence that the library rules still hold: `JdbcAuditChainTransactionTest` (4/4).

## Wiring (one store)

`platform.audit_record` / `platform.audit_chain_meta` only. No second audit store.

| Event | Emitter | Method | Event types |
|---|---|---|---|
| Login | `SessionService` → optional `AuditPort` | `append` accepted / logout; `appendNewTransaction` rejected | `p2.auth.login.accepted`, `p2.auth.login.rejected`, `p2.auth.logout` |
| Permission change | `PolicyAdminService.grant` / `assign` | `append` | `p2.permission.granted`, `p2.permission.denied` |
| Admin | P1 staging `JdbcAuditPort` mirrored by `HashChainedP1AuditPort` | same method the P1 caller used | `p1.tenant.created`, `p1.tenant.enabled`, `p1.tenant.disabled`, `p1.tenant.disable.rejected`, `p1.tenant.config.updated`, `p1.admin.init.accepted`, `p1.quota.*` |
| Guard-reject | G1 product `AuditPortIsolationSink` on M0-02 `IsolationAuditSink` | `appendNewTransaction` | `isolation.guard.reject` |

Isolation-kernel stays independent; it still only emits `IsolationAuditEvent`. G1's `AuditPortIsolationSink` (not a second store) maps `ISOLATION_REJECT` to `GuardRejectCapture`. This PR does not add another isolation sink. Tenant isolation remains M0-02 `AccessContextHolder` / `RequestIsolationPolicy` only.

P1 `GET /tenants/{id}/audit-events` still reads staging `platform.audit_event` (M0-03 unchanged). Trace lookup is `AuditPort.findByTraceId` / `GET /api/v1/audit/traces/{traceId}`.

## Proven vs still open

| Proven | Still open |
|---|---|
| Login accepted / rejected / logout append to the hash chain and are findable by `trace_id` | Full G4 product close (same-request browser chain / object-store cold / hot-row purge). 90-day copy + 365-day same-PG purge: [g4-export-audit-archive.md](../g4-export-audit-archive.md) |
| Permission grant / role assignment append (`append` joins ambient TX) | Export / file-download audit |
| Admin tenant create (and other P1 admin writes) land on the same chain; `append` rolls back with the business TX | One real request that is login→permission→admin→data→approval→guard-reject (G18 is data/approval; G4 events are wired but not one browser walk) |
| Guard-reject from `IsolationAuditor` / `RequestIsolationPolicy` uses `appendNewTransaction` and survives a surrounding rollback | Switching P1 `GET /tenants/{id}/audit-events` off staging |
| `JdbcAuditChainTransactionTest` 4/4 — library same-TX rules unchanged | G6 restore drill, G17 classified / GM delivery, target Linux/ARM Profile |
| `GET /api/v1/audit/traces/{traceId}` returns tenant-scoped G4 events | Browser Authorization Code + PKCE as the accepted login (stub IdP here) |

## Tests

Recorded **2026-09-22T15:29:50Z** on this branch (agent VM, no Docker). Zonky PostgreSQL **15.18**, OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**. Live `127.0.0.1:5432` was not required.

```bash
mvn -pl nexus-audit-core,nexus-p2-iam,nexus-permission-core,nexus-bootstrap -am test \
  -Dtest=JdbcAuditChainTransactionTest,SessionLoginAuditTest,PolicyAdminAuditTest,HashChainedCoverageTest,G4AuditCoverageIT,NexusOneProductBootTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`BUILD SUCCESS`.

| Suite | Result |
|---|---|
| `JdbcAuditChainTransactionTest` | **4/4** — `append` joins Spring TX / rolls back with chain head; commit intact; `appendNewTransaction` survives |
| `SessionLoginAuditTest` | **1/1** — accepted / logout / rejected findable by trace; chain intact |
| `PolicyAdminAuditTest` | **1/1** — grant + assign → `p2.permission.granted` by trace |
| `HashChainedCoverageTest` | **2/2** — P1 admin mirror uses the same `append` / `appendNewTransaction`; isolation reject only |
| `G4AuditCoverageIT` | **1/1** — product `NexusOneApplication`: HTTP login accepted+rejected, permission grant, tenant create, `GET /api/v1/audit/traces/{traceId}`, guard-reject, `append` rollback, guard-reject survives surrounding TX |
| `NexusOneProductBootTest` | **1/1** — product boot still wires JDBC Outbox after Flyway |

Regression on the same VM **2026-09-22T15:30:27Z** (did not redo G18 product close):

```bash
mvn -pl nexus-event-core,nexus-p2-iam,nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=EventCoreConfigurationTest,IdentityApiTest,PermissionApiTest,G18BusinessPathIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| Suite | Result |
|---|---|
| `EventCoreConfigurationTest` | **5/5** |
| `IdentityApiTest` | **4/4** |
| `PermissionApiTest` | **2/2** |
| `G18BusinessPathIT` | **2/2** — data/approval same-TX path still green |
