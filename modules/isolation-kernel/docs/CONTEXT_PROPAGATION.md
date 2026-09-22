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

`CacheKeyGuard.put` builds the key from the bound context, then checks it, then invokes the Redis/cache sink. A foreign tenant segment is `P1.M02.014` and must not write.

## 5. Files (M0-10)

```text
tenants/{tenantId}/...
platform/...
```

`FileObjectKeyGuard.putObject` rejects `tenants/{other}/...` with `P1.M02.015` before the S3/MinIO sink.

Platform context may read tenant prefixes (ops/reconciliation) but tenant context may not read `platform/` or another tenant prefix.

## 6. Audit

Every isolation reject and every `PlatformContext` issue is an `IsolationAuditEvent`. M0-11 persists the sink; this module only emits. Sensitive values are not logged — events carry actor, tenant/path, reason, trace, and error code.
