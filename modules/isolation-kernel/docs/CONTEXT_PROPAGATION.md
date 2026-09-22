# Isolation context propagation contract (M0-02)

This is the server-side contract other M0 modules must call. Client-supplied tenant ids are never a source of truth.

## 1. Issuance

```text
HTTP request
   │
   ├─ client may send X-Nexus-Tenant-Id   ← hint only
   │
   v
P2 / session membership (M0-04)
   │  AuthenticatedMembership { actor, session tenant, authorized tenant ids }
   v
RequestIsolationPolicy.bindTenant(...)
   │  • session tenant required and must be a membership
   │  • client hint matching session is ignored as input (session wins)
   │  • client hint for another authorized tenant = explicit switch, still membership-backed
   │  • client hint outside membership = P1.M02.009, audited, no DB
   v
TenantContext  (ThreadLocal via AccessContextHolder)
```

`PlatformContext` is not a header. Only `PlatformContextIssuer.issue(path, actor, reason, traceId)` after a whitelist hit. Every successful issue emits `PLATFORM_CONTEXT_ISSUED`.

## 2. Query / DB

```text
QueryAst  (no SQL text)
   → TenantInjector          (cannot be disabled)
   → DataScopeInjector SPI   (M0-05; cannot remove tenant_id)
   → SoftDeleteInjector SPI
   → TableAccessPlan Guard
   → parameterized PostgreSQL renderer
   → GuardedJdbc (PreparedStatement only)
```

Rejects happen in compile, before `Connection.prepareStatement`.

| Event | Code |
|---|---|
| Missing context | P1.M02.001 |
| Cross-tenant write | P1.M02.002 |
| Cross-tenant read | P1.M02.003 |
| Illegal / non-whitelist platform access | P1.M02.004 |
| Client tenant untrusted | P1.M02.009 |

## 3. Async / Job / Outbox (M0-09)

- Capture `ContextSnapshot` at submit time (`IsolatedExecutor` / `AccessContextHolder.capture()`).
- Missing context is rejected **before** the delegate executor runs the body.
- `IsolatedEnvelope` must carry `tenantId` + `traceId`. Dispatch under a different `TenantContext` is `P1.M02.016` and must not call the dispatcher.

## 4. Cache (M0-10)

Key format:

```text
nexus:{env}:{tenant_id}:{module}:{key}
nexus:{env}:platform:{module}:{key}
```

`CacheKeyGuard.put` builds the key from the bound context, then checks it, then invokes the Redis/cache sink. A foreign tenant segment is `P1.M02.014` and must not write. The reject is an `IsolationAuditEvent`.

## 5. Files (M0-10)

```text
tenants/{tenantId}/...
platform/...
```

`FileObjectKeyGuard.putObject` rejects `tenants/{other}/...` with `P1.M02.015` before the S3/MinIO sink. The reject is an `IsolationAuditEvent`.

Platform context may read tenant prefixes (ops/reconciliation) but tenant context may not read `platform/` or another tenant prefix.

## 6. Export

`ExportGuard` / `IsolatedExport` is the export surface. Cross-tenant export is `P1.M02.019` and must not submit a Task, write a file, or update export cache status. Task type `ledger.export.csv` still travels in `IsolatedEnvelope`.

## 7. Audit

Every isolation reject (query, cache, file, export, async) and every `PlatformContext` issue is an `IsolationAuditEvent`. Static guards go through `IsolationAuditorHolder` installed by `IsolationKernel.create` — not a second tenant holder. Product `AuditPortIsolationSink` persists rejects with `AuditPort.appendNewTransaction`. Sensitive values are not logged — events carry actor, tenant/path, reason, trace, and error code.
