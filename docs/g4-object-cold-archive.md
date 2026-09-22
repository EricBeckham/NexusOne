# G4 remainder: object-store / cold archive

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. This package adds a **cold copy** of aged audit archive (or archive-eligible hot rows) onto the existing File `ObjectStoragePort` / MinIO Fake (`CountingObjectStorage`). It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** DELETE `platform.audit_record`. It does **not** claim a product-wide G4 close.

365-day same-PG archive expiry is already on `main` ([g4-export-audit-archive.md](./g4-export-audit-archive.md)). This remainder does not redo that purge.

## What this close covers

1. **Cold copy Job.** `AuditColdArchiveJob` / `audit.archive.cold` copies eligible rows to object storage. Eligibility is the same-PG archive table **plus** hot rows older than 90 days (still present after the 365-day archive DELETE because the hot table is append-only). Re-runs are idempotent via `exists`.
2. **Tenant / platform prefix.** Keys follow `FileObjectKeyGuard`: `tenants/{tenantId}/audit/cold/{eventId}.json` or `platform/audit/cold/{eventId}.json`.
3. **Restore / list.** `listTenant` / `listPlatform` / `restore` / `restoreByTraceId` read back through the same `ObjectStoragePort`. Evidence uses `CountingObjectStorage` (Fake) — no live MinIO, no secrets in git.
4. **Platform Job pattern.** Runner / TaskHandler / opt-in Quartz reuse `PlatformContext` path `audit.archive` (not tenant `IsolatedJobScheduler` / `TaskQueue`). Quartz is opt-in (`nexus.audit.cold-archive.schedule-enabled`, default **false**, 02:45 UTC).

Same-TX `append` joining is unchanged (`JdbcAuditChainTransactionTest`).

## Evidence

Recorded **pending-this-run** on this agent VM. `./scripts/g4-object-cold-archive.sh` → **pending**. No Docker. No live `127.0.0.1:5432`. No `haopan_platform`.

| Item | Value |
|---|---|
| JDK | OpenJDK **21** |
| Maven | **3.8.x** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** |
| Object store | `CountingObjectStorage` Fake (`nexus-files` bucket). MinIO credentials stay on `SecretPort` / env; not in git |

```
./scripts/g4-object-cold-archive.sh
# pending
```

| Suite | Result | What it proved |
|---|---|---|
| `AuditColdArchiveJobTest` | pending | Counting storage puts under tenant + platform prefixes; restore/list; second run skips; purge leftover still cold-copied; hot chain intact |
| `JdbcAuditColdArchiveIT` | pending | Zonky PG 15: archive + aged hot copied; young row stays out; restore/list; hot count unchanged |
| `G4AuditColdArchiveJobTest` | pending | Platform runner + TaskHandler; tenant context is `CTX_INVALID_PLATFORM` and puts stay 0 |
| `JdbcAuditChainTransactionTest` | pending | Same-TX `append` rules unchanged |

## Honest leftover G4 rows

This is **not** a product-wide G4 close.

| Still open | Why |
|---|---|
| Hot purge after 90 days | `platform.audit_record` is append-only (`REVOKE DELETE`). Cold archive is a **copy** |
| Live MinIO cold drill | Fake/counting storage is the required evidence here; live S3 stays SecretPort + optional G1/G6 drills |
| Human browser Authorization Code + PKCE click | HTTP-driver PKCE is on `main` ([g4-pkce-login-audit.md](./g4-pkce-login-audit.md) / #108). HTTPS loopback leftover is [g4-pkce-https-browser.md](./g4-pkce-https-browser.md). Chromium / human laptop click still needs to trust that CA (or an insecure-origin exception) |
| Tenant TaskQueue / IsolatedQuartzJob for archive | Those APIs require membership; cold archive is a platform Job |
| G6 whole-DB / object restore RPO | `RecoveryPort` remains dry-run; this package lists/restores **audit cold objects** only |
| G17 / FieldPolicy report UI | Not this package |

G18 remains closed.
