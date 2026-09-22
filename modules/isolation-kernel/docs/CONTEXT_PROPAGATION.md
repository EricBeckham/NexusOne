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
QueryAst  (no SQL text; SELECT may INNER JOIN / IN-EXISTS including nested subquery-in-subquery / ORDER BY / LIMIT; INSERT may batch)
   → TenantInjector          (cannot be disabled; each join and every subquery level too; each batch row)
   → DataScopeInjector SPI   (M0-05; cannot remove tenant_id, join tenant, subquery/nested-subquery tenant, or batch tenant; may AND SELF/DEPT equality atoms or single-column IN lists; mixed-column OR sits on QueryAst.disjunctions())
   → SoftDeleteInjector SPI
   → TableAccessPlan Guard   (primary + each join + each subquery level)
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
| Cross-tenant INNER / OUTER / CROSS JOIN | P1.M02.020 |
| Export FieldPolicy | P1.M02.021 |
| Cross-tenant subquery | P1.M02.022 |
| Cross-tenant batch write | P1.M02.023 |
| Invalid sort | P1.M02.024 |
| Unbounded page | P1.M02.025 |

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

`ExportGuard` / `IsolatedExport` is the export surface. Cross-tenant export is `P1.M02.019` and must not submit a Task, write a file, or update export cache status. Task type `ledger.export.csv` still travels in `IsolatedEnvelope`. Accepted submit / writeFile / dispatch emit `EXPORT_ACCEPTED`; product `AuditPortIsolationSink` persists those with `AuditPort.append` on the existing hash chain.

`IsolatedExport.compile` / `writeProjected` also require FieldPolicy. Hidden Query AST columns are dropped before compile / file write. Unmapped fields, empty leftover projection, empty policy, and second-auth without grant are `P1.M02.021` and must not compile Query AST or call the file sink. Mask merge stays in `FieldPolicyProjector`. Cross-tenant INNER / OUTER / CROSS JOIN is `P1.M02.020` and must not reach JDBC. Cross-tenant IN/EXISTS subquery is `P1.M02.022`; stolen batch INSERT is `P1.M02.023`; unregistered ORDER BY is `P1.M02.024`; LIMIT/OFFSET outside `1..1000` / `offset >= 0` is `P1.M02.025`. None of those reach JDBC.

## 7. Audit

Every isolation reject (query, cache, file, export, async) and every `PlatformContext` issue is an `IsolationAuditEvent`. Static guards go through `IsolationAuditorHolder` installed by `IsolationKernel.create` — not a second tenant holder. Product `AuditPortIsolationSink` persists rejects with `AuditPort.appendNewTransaction` and accepted exports with `AuditPort.append`. Sensitive values are not logged — events carry actor, tenant/path, reason, trace, and error code.
