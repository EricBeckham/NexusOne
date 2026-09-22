# NexusOne Task Core (M0-09)

Quartz **2.5.2** JDBC JobStore on PostgreSQL 15 (`quartz` schema, `PostgreSQLDelegate`, clustered, misfire `FireNow`) and a database Task Queue with `SKIP LOCKED` leases.

Jobs and tasks persist `StoredMembership` and restore it with M0-02 `RequestIsolationPolicy.bindTenant` before `IsolatedEnvelope.dispatch`. Cross-tenant payloads are rejected (`P1.M02.016`) before the handler.

## Tests

```bash
mvn -pl nexus-task-core -am test
```

Embedded PostgreSQL 15 via zonky; no Docker hard dependency.
