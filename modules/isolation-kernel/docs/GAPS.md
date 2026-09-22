# M0-02 remaining gaps

Honest leftover work. These are not claimed as done.

- Subquery, batch write, sort/paging AST, and OUTER/CROSS JOIN. INNER JOIN of registered tables is compiled; each tenant table gets its own `tenant_id` predicate.
- DataScope / FieldPolicy compilation into the AST (M0-05). The DataScope seam still cannot strip `tenant_id` (including join predicates). Export uses a visibility/second-auth gate (`ExportFieldPolicyGuard`); mask merge stays in `FieldPolicyProjector`.
- Generic NativeSqlPort beyond count templates.
- Static scanner is regex-based: aliases, reflection, generated code, and JNI are not covered. Must keep infrastructure-root allowlisting tight.
- No servlet/Spring filter here — M0-01 must call `RequestIsolationPolicy` from its filter/interceptor.
- Audit events are hash-chained only after the product `AuditPortIsolationSink` persists them (`appendNewTransaction`). The kernel still only emits to `IsolationAuditSink` and does not depend on `nexus-audit-core`.
- Redis/MinIO adapters are not implemented here (M0-10); only key/prefix guards. Product cache/file modules prove reject-before-backend.
- GaussDB dialect is out of this PostgreSQL-only slice.
- P0 non-pass items stay non-pass: capacity not frozen; Windows is not a target Profile; Keycloak/Flowable/MinIO upgrade-rollback untouched.
