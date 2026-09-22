# M0-03 contracts and integration points

P1 is the source of truth for **Tenant**, **Runtime Entitlement Projection**, and **Quota execution**. Sibling modules must not read `platform.tenant` (and other P1 tables in schema `platform`) directly.

## M0-01 engineering base

This directory is a **runnable Spring Boot module** so M0-03 can be demonstrated before the M0-01 reactor is merged to `main`.

Re-parent (this follow-up):

- Aggregator `pom.xml` uses the same coordinates as PR #46: `one.nexus:nexusone:0.1.0-SNAPSHOT`, parent **Spring Boot 4.1.1**, `flyway.version=13.7.0`.
- This branch’s aggregator **only lists `nexus-p1-tenant`**. Merging with M0-01 will conflict on the root POM — keep M0-01’s full `<modules>` list and this module’s sources.
- Env: `NEXUS_DB_URL` / `NEXUS_DB_USER` / `NEXUS_DB_PASSWORD` (legacy `NEXUS_DATASOURCE_*` still accepted).
- Flyway: schema `platform`, table `flyway_schema_history`, versions **V100–V199**.
- Error codes: four-digit numbers (`P1.TENANT.0001`).

When M0-01 lands, preferred absorption:

```text
nexusone/
  nexus-bootstrap          <- start class, shared Flyway/datasource, security filter chain
  nexus-p1-tenant          <- this module, converted to a jar (non-boot) library
  nexus-data-core          <- M0-02
  nexus-p2-iam             <- M0-04
  nexus-audit-core         <- M0-11
```

Remaining absorption steps (do not require changes inside P1 domain):

1. Move `P1TenantApplication` into bootstrap or delete it; keep `@SpringBootApplication` scanning `one.nexus`.
2. Add this module’s `classpath:db/migration` to bootstrap Flyway locations (or move V100–V101 into `nexus-bootstrap`). History table is already `platform.flyway_schema_history`.
3. Datasource secrets stay env-only (`NEXUS_DB_*`).
4. Disable `nexus.p1.demo-context.enabled`. Wire M0-02 filters instead.
5. Replace duplicated `one.nexus.p1.domain` copies of `Actor` / `AuditEvent` / `ErrorCode` / `TenantContext` / `PlatformContext` with `nexus-common` types.
6. Health: this module exposes `/actuator/health` on `NEXUS_HTTP_PORT` / `NEXUS_P1_PORT` (default 18081). Bootstrap may relocate it.

Java package stays `one.nexus.p1`. Physical tables stay in schema `platform`.

## M0-02 isolation kernel

P1 **calls** these ports; it does not implement Query AST or guards:

| Type | Role |
|---|---|
| `IsolationGuard` | `requirePlatformContext(operation, duty)` for tenant admin APIs; `requireTenantOrPlatform` for quota consume |
| `PlatformContext` | server-issued; must include reason, actor, trace id |
| `TenantContext` | server-issued after login/switch; P1 never trusts client `tenant_id` as identity (ARCH-024) |
| `TableAccessPlan` | metadata next to each JDBC statement (`scope=platform`, `tenant_required=false`) |

Stub today: `one.nexus.p1.infra.isolation.ThreadLocalIsolationGuard` + demo header filter. Replace the stub by providing an `IsolationGuard` bean from `nexus-data-core` and deleting the demo filter.

Whitelist operations (must be audited PlatformContext paths):

- `tenant.create` / `tenant.enable` / `tenant.disable` / `tenant.list` / `tenant.get`
- `tenant.config.get` / `tenant.config.update` / `tenant.entitlement.get`
- quota consume/release when invoked by platform operators

P3 ledger create typically calls quota consume in the tenant's own context.

SQL in `one.nexus.p1.infra.jdbc` is parameterized `JdbcClient`. After M0-02, route it through `NativeSqlPort` + the recorded `TableAccessPlan` constants. Business packages under `application` / `domain` / `api` do not open JDBC.

## M0-04 identity / admin initialization

```text
P1 TenantLifecycleService
        |
        v
TenantAdminInitializer.initialize(AdminInitCommand)
        |
        v  (M0-04)
Person + Employment in target tenant
Keycloak Organization membership (P2 owns the Keycloak adapter)
Tenant-admin role assignment
MFA policy for tenant admin (P2)
```

Command fields: `tenantId`, `tenantCode`, `adminDisplayName`, `adminEmail`, `actor`, `traceId`, `correlationId`.

Result: `ACCEPTED` | `COMPLETED` | `FAILED`. M0-03 stub always returns `ACCEPTED` and stores `platform.admin_init_request`. Create rolls back if the hook returns `FAILED`.

P1 does not:

- create Keycloak users or realms
- store credentials
- assign RBAC beyond requesting "this email should become tenant admin"

Platform administrators live **in the platform tenant**. P2 maintains their persons/roles there. P1 only names duties it requires (`SYSTEM_ADMIN` for lifecycle writes, `AUDIT_ADMIN` must not mutate `platform.audit_event`).

## M0-06 SchemaService / apps

Call:

```http
POST /api/v1/p1/tenants/{id}/quotas/consume
{ "metric": "CUSTOM_LEDGER" | "APP", "amount": 1, "idempotency_key": "..." }
```

or inject `QuotaService` in-process. Over-limit → `P1.QUOTA.0001` + `p1.quota.rejected` audit. Disabled tenants cannot consume.

Release on compensating delete/uninstall.

## M0-11 audit

`AuditEvent` fields:

```text
event_id, event_type, event_version, occurred_at,
tenant_id, actor_subject_id, actor_tenant_id,
platform_context, reason, trace_id, correlation_id, causation_id,
outcome (ACCEPTED|REJECTED), resource_type, resource_id, details
```

Reject-path writes use a new transaction so they survive the business rollback.

M0-11 should implement `AuditPort` (hash chain, retention, CryptoPort) and take over `GET /tenants/{id}/audit-events`.

Notable event types:

- `p1.tenant.created` / `enabled` / `disabled`
- `p1.tenant.disable.rejected` (platform protection)
- `p1.quota.consumed` / `p1.quota.rejected` / `p1.quota.released`
- `p1.admin.init.accepted`

## P4 (later)

Same `platform.entitlement_projection` row. S3+ writer sets `source=SUBSCRIPTION` with `sku_code` + `subscription_id`. M0 only writes `DEFAULT_PACK`. There is no P1 API to author commercial SKUs.

## Error codes

| Code | Meaning |
|---|---|
| P1.TENANT.0001 | not found |
| P1.TENANT.0002 | code conflict |
| P1.TENANT.0003 | invalid state |
| P1.TENANT.0004 | platform tenant protected |
| P1.TENANT.0005 | invalid code/name |
| P1.TENANT.0006 | confirm required |
| P1.QUOTA.0001 | over limit rejected |
| P1.QUOTA.0002 | invalid metric/amount |
| P1.CONTEXT.0001 | platform context required |
| P1.CONTEXT.0002 | tenant context mismatch |
| P1.ADMIN.0001 | admin init failed |
| P1.DUTY.0001 | platform duty denied |
| P1.INTERNAL.0001 | unhandled (should not leak) |
