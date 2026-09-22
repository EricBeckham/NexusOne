# NexusOne P1 Tenant (M0-03)

Java 21 module for **P1 星域** tenant lifecycle, reserved platform tenant, basic tenant config, default runtime entitlement projection, and baseline quota enforce/reject.

This is product code, not a PoC copy of `poc/`. Isolation kernel (M0-02) and Keycloak (M0-04) are **not** implemented here; ports and stubs are documented in [CONTRACTS.md](CONTRACTS.md).

## What is in scope

| Capability | Behavior |
|---|---|
| Create tenant | Business tenant, unique `tenant_code`, default config, default entitlement projection, APP + CUSTOM_LEDGER quota rows, P2 admin-init hook |
| Enable / disable (stop) | High-risk `confirm=true`; disable is stop, not destroy |
| Platform tenant | Seeded `code=platform`, cannot be disabled; reject is audited |
| Config | locale, timezone, date format, currency, fiscal year, optional file ids |
| Entitlement | Runtime projection only (`p4.runtime-entitlement.v1`); source `DEFAULT_PACK` until P4 |
| Quota | Configurable limits; over-limit **REJECT** + audit event |

## What is out of scope (explicit)

- Isolation kernel internals (Query AST, TenantInjector, TableAccessPlan Guard, CI SQL bans) — **call** M0-02
- Keycloak, Organizations, MFA, SMTP, person/department/position data — M0-04
- Tenant freeze/unfreeze, cancel/soft-delete/destroy, import/export, relations, branding assets
- P4 Product/SKU/Subscription commercial definitions (P1 must not create a second entitlement catalog)
- Frozen capacity maxima (P0-08 left thresholds unfrozen). Defaults below are **provisional**
- GaussDB / G14 / target Profile install acceptance
- Docker / Compose as a runtime dependency
- Audit hash chain, CryptoPort, SecretPort — M0-11
- Vue UI — M0-12

## Run (local JAR, PostgreSQL 15)

Credentials only from the environment. Never commit passwords.

```bash
export NEXUS_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/nexus_p1
export NEXUS_DATASOURCE_USERNAME=nexus_p1
export NEXUS_DATASOURCE_PASSWORD=...   # from your secret store
export NEXUS_P1_DEMO_CONTEXT_ENABLED=true   # demo only; strip in production entry

cd nexus-p1-tenant
mvn -q -DskipTests package
java -jar target/nexus-p1-tenant-0.1.0-m0.jar
```

Provisional quota env (not product max):

```bash
export NEXUS_P1_QUOTA_APP_LIMIT=5
export NEXUS_P1_QUOTA_CUSTOM_LEDGER_LIMIT=10
```

Two-tenant demo (create / stop / platform reject / quota reject):

```bash
./scripts/demo-two-tenants.sh
```

Tests against PostgreSQL 15 (uses database `nexus_p1_test` by default):

```bash
export NEXUS_DATASOURCE_PASSWORD=...
mvn -q test
```

## API (prefix `/api/v1/p1`)

Demo headers (only when `NEXUS_P1_DEMO_CONTEXT_ENABLED=true`):

- `X-Nexus-Demo-Actor-Id` — UUID
- `X-Nexus-Demo-Reason` — required to open PlatformContext
- `X-Nexus-Trace-Id` — optional
- `X-Nexus-Demo-Actor-Tenant-Id` — defaults to platform tenant

| Method | Path | Notes |
|---|---|---|
| POST | `/tenants` | create + admin init hook + default projection |
| GET | `/tenants` | list |
| GET | `/tenants/{id}` | get |
| GET | `/platform-tenant` | reserved tenant |
| POST | `/tenants/{id}/enable` | body `{ "confirm": true }` |
| POST | `/tenants/{id}/disable` | stop; platform tenant → `P1.TENANT.004` |
| GET/PUT | `/tenants/{id}/config` | basic config |
| GET | `/tenants/{id}/entitlement` | runtime projection |
| GET | `/tenants/{id}/quotas` | usage |
| POST | `/tenants/{id}/quotas/consume` | P3 SchemaService / app install |
| POST | `/tenants/{id}/quotas/release` | |
| GET | `/tenants/{id}/audit-events` | P1 staging until M0-11 |

Error body: `{ "code", "message", "trace_id", "details" }`.

## Flyway

History table `p1.p1_flyway_history`. Migrations only create P1 static tables in schema `p1`. They do not create P3 ledger tables.

Catalog for M0-02 `table_registry`: `src/main/resources/p1-table-registry.json` (all `scope=platform`).

## Versions used here

Aligned with P0 local closure where applicable: Java 21, PostgreSQL 15, Flyway 13.7.0, PostgreSQL JDBC 42.7.8. Spring Boot **3.5.6** is a current stable GA stand-in until M0-01 freezes the parent BOM / Spring Boot minor. Flyway 13 removed APIs that Spring Boot 3.5 auto-config still calls, so this module migrates through `FlywayConfig` instead of Boot's Flyway auto-configuration.
