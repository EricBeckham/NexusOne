# M0-06 ledger Schema / SchemaService / CRUD

See [`nexus-p3-studio/README.md`](../../nexus-p3-studio/README.md) and [`nexus-p3-studio/EVIDENCE.md`](../../nexus-p3-studio/EVIDENCE.md).

- Metadata Flyway: `V400__business_meta_registry.sql` (`business_meta.application`, `ledger`, `ledger_field`).
- Dynamic physical tables: `one.nexus.p3.infra.schema.JdbcSchemaService` only.
- Isolation: M0-02 `IsolationKernel` / `QueryAst` / `TableRegistry.replace` for evolution.
- DataScope: M0-05 `PermissionQueryBinder` / `ScopedQuery` on list/detail/total.
- Quota: optional P1 `QuotaService` hook (`APP`, `CUSTOM_LEDGER`).
