# M0-02 remaining gaps

Honest leftover work. These are not claimed as done.

- Query AST leftovers beyond this kernel: callers that skip `QueryCompiler`, report UI / physical dump, and mixed-column OR (SELF∨DEPT) DataScope. INNER / OUTER / CROSS JOIN and nested IN/EXISTS subquery-in-subquery are compiled; each tenant table at every join and nesting level gets its own `tenant_id` predicate. Safe SELF/DEPT equality, single-column multi-value `IN`, and FieldPolicy-visible projections can be encoded onto the AST ([g1-datascope-fieldpolicy-ast.md](../../../docs/g1-datascope-fieldpolicy-ast.md), [g1-datascope-or-in-ast.md](../../../docs/g1-datascope-or-in-ast.md)). Product ledger list/detail opts in ([g1-queryast-product-optin.md](../../../docs/g1-queryast-product-optin.md)); leftover BoundSql / count on `GuardedLedgerStore` is guarded ([g1-queryast-boundsql-guard.md](../../../docs/g1-queryast-boundsql-guard.md)); other `ScopedQuery` callers may still skip.
- DataScope / FieldPolicy leftovers (M0-05). The DataScope seam still cannot strip `tenant_id` (including join predicates) and may AND SELF/DEPT equality atoms or single-column `IN` lists. Mixed-column OR (SELF∨DEPT) stays outside the AST. Export drops hidden Query AST columns (`ExportFieldPolicyGuard.dropHidden`) and still rejects second-auth without grant; mask merge stays in `FieldPolicyProjector` ([g1-fieldpolicy-mask-second-auth.md](../../../docs/g1-fieldpolicy-mask-second-auth.md)).
- Generic NativeSqlPort beyond count templates.
- Static scanner is regex-based: aliases, reflection, generated code, and JNI are not covered. Must keep infrastructure-root allowlisting tight. Product `src/main` QueryCompiler-bypass allowlist is `QueryCompilerBypassAllowlist` ([docs/g1-querycompiler-bypass-ci.md](../../../docs/g1-querycompiler-bypass-ci.md)).
- No servlet/Spring filter here — M0-01 must call `RequestIsolationPolicy` from its filter/interceptor.
- Audit events are hash-chained only after the product `AuditPortIsolationSink` persists them (`appendNewTransaction`). The kernel still only emits to `IsolationAuditSink` and does not depend on `nexus-audit-core`.
- Redis/MinIO adapters are not implemented here (M0-10); only key/prefix guards. Product cache/file modules prove reject-before-backend in-process; optional live drills skip when Redis/MinIO are absent (`docs/g1-live-redis-minio.md`).
- GaussDB dialect is out of this PostgreSQL-only slice.
- P0 non-pass items stay non-pass: capacity not frozen; Windows is not a target Profile; Keycloak/Flowable/MinIO upgrade-rollback untouched.
