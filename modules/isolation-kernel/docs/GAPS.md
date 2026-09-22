# M0-02 remaining gaps

Honest leftover work. These are not claimed as done.

- Join, subquery, batch write, sort/paging AST (still single-table equality + IS NULL).
- DataScope / FieldPolicy compilation (M0-05). Only a seam is provided; a hostile injector that drops `tenant_id` is rejected.
- Generic NativeSqlPort beyond count templates.
- Static scanner is regex-based: aliases, reflection, generated code, and JNI are not covered. Must keep infrastructure-root allowlisting tight.
- No servlet/Spring filter here — M0-01 must call `RequestIsolationPolicy` from its filter/interceptor.
- Audit events are not hash-chained or stored (M0-11). Capture shape and `AuditPort` exist in `nexus-audit-core`; this kernel still only emits to `IsolationAuditSink`.
- Redis/MinIO adapters are not implemented (M0-10); only key/prefix guards.
- GaussDB dialect is out of this PostgreSQL-only slice.
- P0 non-pass items stay non-pass: capacity not frozen; Windows is not a target Profile; Keycloak/Flowable/MinIO upgrade-rollback untouched.
