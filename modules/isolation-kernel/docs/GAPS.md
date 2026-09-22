# M0-02 remaining gaps

Honest leftover work. These are not claimed as done.

- INNER JOIN equality + per-table `tenant_id` inject is in the Query AST. Subquery, batch write, sort/paging remain open.
- DataScope compilation still M0-05 (hostile injector that drops `tenant_id` is rejected). FieldPolicy is applied on IsolatedExport after the guarded query / before file write; compiling FieldPolicy into the AST is still M0-05.
- Generic NativeSqlPort beyond count templates.
- Static scanner is regex-based: aliases, reflection, generated code, and JNI are not covered. Must keep infrastructure-root allowlisting tight.
- No servlet/Spring filter here — M0-01 must call `RequestIsolationPolicy` from its filter/interceptor.
- Audit events are hash-chained only after the product `AuditPortIsolationSink` persists them. The kernel still only emits to `IsolationAuditSink`.
- Redis/MinIO adapters are not implemented here (M0-10); only key/prefix guards. Product cache/file modules prove reject-before-backend.
- GaussDB dialect is out of this PostgreSQL-only slice.
- P0 non-pass items stay non-pass: capacity not frozen; Windows is not a target Profile; Keycloak/Flowable/MinIO upgrade-rollback untouched.
