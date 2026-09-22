# G4 leftover: 90-day hot-row purge

S0+S1 PostgreSQL slice. Not an M0 redo. **Product G18 stays closed.** This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** DROP `platform.audit_record`. It does **not** claim a product-wide G4 close.

Same-PG archive copy (#86 / #90) and object-store cold copy (#104) already exist. The leftover here is a **safe DELETE of a hot prefix** that is already copied.

Sibling honesty leftover (do **not** redo this #123 contract): [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md). Keep both leftover rows in [docs/README.md](./README.md): this file and `g4-hot-purge-90d-honesty`. Live `:5432` / JAR / Quartz remaining window is an honest SKIP on that leftover, not a product G4 close.

## What this slice covers

1. **Eligibility.** A hot row may be deleted only when `occurred_at` is older than `AuditRetentionPolicy.HOT_RETENTION` (90 days) **and** a durable copy still exists:
   - object-store cold key exists, **or**
   - same-PG `platform.audit_record_archive` still holds the `event_id` **and** the row is still inside the 365-day archive window (so a later archive expiry cannot orphan it).
2. **Prefix only.** Walk from the lowest remaining `chain_index`. A younger or uncopied row stops the walk. That keeps the live suffix gapless.
3. **Flyway V504.** `GRANT DELETE` on `platform.audit_record` to the migrating / app role. `UPDATE` stays revoked. `PUBLIC` still has no DELETE (V501). **No `DROP TABLE`.**
4. **Platform Job.** `AuditHotPurgeJob` / `audit.hot.purge` reuses `PlatformContext` path `audit.archive`. Quartz is opt-in (`nexus.audit.hot-purge.schedule-enabled`, default **false**, 03:00 UTC, after archive 02:30 and cold 02:45).
5. **Suffix verify.** After a prefix delete, `AuditHashChain.verify` checks the remaining tail. Index 0 still requires `GENESIS`. A later suffix head accepts its stored `previousHash`. Genesis-to-head proof for purged rows lives in archive / cold.

`JdbcHashChainedAuditPort.append` is unchanged. The hot table remains; only eligible rows are removed.

Recommended operator order: archive copy → cold copy → hot purge → 365-day archive expiry.

## Evidence

Recorded **pending-this-run** on this agent VM. `./scripts/g4-hot-purge-90d.sh` → **pending**. No Docker. No live `127.0.0.1:5432`.

| Item | Value |
|---|---|
| JDK | OpenJDK **21** |
| Maven | **3.8.x** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** |
| Flyway | V501–V503 + **V504** on schema `platform` |

```
./scripts/g4-hot-purge-90d.sh
# pending
```

| Suite | Result | What it proved |
|---|---|---|
| `AuditHotPurgeJobTest` | pending | 91-day archived prefix gone; young stays; uncopied stays; 366-day archive-only waits for cold; append after purge keeps suffix; second run 0 |
| `JdbcAuditHotPurgeIT` | pending | Zonky PG 15: V504 applied; table still present; 91-day archived prefix deleted; 366-day waits for cold then deletes; append after purge; second run 0 |
| `AuditFlywayContractTest` | pending | V504 grants DELETE, no DROP, no UPDATE grant |
| `G4AuditHotPurgeJobTest` | pending | Platform runner + TaskHandler; tenant context is `CTX_INVALID_PLATFORM` |
| `JdbcAuditChainTransactionTest` | pending | Same-TX `append` rules unchanged |

## Honest leftover G4 rows

This is **not** a product-wide G4 close.

| Still open | Why |
|---|---|
| Human laptop Authorization Code + PKCE click | HTTP-driver + HTTPS loopback leftover are on `main`. An operator still has to trust the local CA (or use an insecure-origin exception). |
| HTTP export wizard / report UI | `ledger.export.csv` is the guarded surface, not a physical ledger dump |
| Switching P1 `GET /tenants/{id}/audit-events` off staging | Honesty leftover is [g4-p1-staging-audit-events.md](./g4-p1-staging-audit-events.md). GET still reads staging. |
| Live MinIO cold drill | Fake / counting storage is the required evidence for cold copy; live S3 stays SecretPort |
| Tenant TaskQueue / IsolatedQuartzJob for archive or purge | Those APIs require membership; this Job is platform-only |
| Hot `verifyChain()` after purge is suffix-only | Purged prefix hashes are not re-proved from `GENESIS` on the hot table. Full history is archive / cold. |
| Quartz default off | `nexus.audit.hot-purge.schedule-enabled=false` so ITs do not fire a platform Job |
| G6 `RecoveryPort` / G17 classified | Not this package |

G18 remains closed. Same-TX `append` joining is unchanged.
