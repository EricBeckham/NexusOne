# NexusOne P1 Tenant (M0-03)

Java 21 module for **P1 星域** tenant lifecycle, reserved platform tenant, basic tenant config, default runtime entitlement projection, and baseline quota enforce/reject.

This is product code, not a PoC copy of `poc/`. Isolation kernel (M0-02) and Keycloak (M0-04) are **not** implemented here; ports and stubs are documented in [CONTRACTS.md](CONTRACTS.md).

Parent reactor: `one.nexus:nexusone:0.1.0-SNAPSHOT` with **Spring Boot 4.1.1** (same BOM as M0-01 / PR #46). This branch ships a thin aggregator `pom.xml` that only lists `nexus-p1-tenant` until M0-01 merges.

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

## Run (local JAR, PostgreSQL 15.x)

Credentials only from the environment. Never commit passwords. Canonical names match M0-01 (`NEXUS_DB_*`); `NEXUS_DATASOURCE_*` still works as a fallback.

```bash
export NEXUS_DB_URL=jdbc:postgresql://127.0.0.1:5432/nexus_p1
export NEXUS_DB_USER=nexus_p1
export NEXUS_DB_PASSWORD=...   # from your secret store
export NEXUS_P1_DEMO_CONTEXT_ENABLED=true   # demo only; strip in production entry

mvn -q -DskipTests package
java -jar nexus-p1-tenant/target/nexus-p1-tenant-0.1.0-SNAPSHOT.jar
```

Provisional quota env (not product max):

```bash
export NEXUS_P1_QUOTA_APP_LIMIT=5
export NEXUS_P1_QUOTA_CUSTOM_LEDGER_LIMIT=10
```

Two-tenant demo (create / stop / platform reject / quota reject):

```bash
./nexus-p1-tenant/scripts/demo-two-tenants.sh
```

Tests against PostgreSQL 15.x (uses database `nexus_p1_test` by default):

```bash
export NEXUS_DB_PASSWORD=...
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
| POST | `/tenants/{id}/disable` | stop; platform tenant → `P1.TENANT.0004` |
| GET/PUT | `/tenants/{id}/config` | basic config |
| GET | `/tenants/{id}/entitlement` | runtime projection |
| GET | `/tenants/{id}/quotas` | usage |
| POST | `/tenants/{id}/quotas/consume` | P3 SchemaService / app install |
| POST | `/tenants/{id}/quotas/release` | |
| GET | `/tenants/{id}/audit-events` | P1 staging until M0-11 |

Error body: `{ "code", "message", "trace_id", "details" }`. Error numbers are four digits (`P1.TENANT.0001`), matching M0-01.

## Flyway

M0-01 window for P1 is **V100–V199**. History table `platform.flyway_schema_history` (same as M0-01). Migrations create P1 static tables in schema `platform`. They do not create P3 ledger tables.

Standalone this module also `CREATE SCHEMA IF NOT EXISTS platform` so it can run before M0-01’s V001 lands. After merge, keep that statement (idempotent) or drop it; do not replay V20260922_* against this history table.

Catalog for M0-02 / M0-06 `table_registry`: `src/main/resources/p1-table-registry.json` (all `scope=platform`).

## Versions used here

| Item | Value | Notes |
|---|---|---|
| JDK | 21 | |
| Spring Boot | **4.1.1** | Same parent as M0-01 PR #46; Flyway auto-config (no custom `FlywayConfig`) |
| Flyway | **13.7.0** | P0 freeze; overrides Boot BOM via aggregator property |
| PostgreSQL | **15.x** | This run: **15.19**. See [EVIDENCE.md](EVIDENCE.md). Not PG 16. |
| Artifact | `one.nexus:nexus-p1-tenant:0.1.0-SNAPSHOT` | Child of `one.nexus:nexusone` |
