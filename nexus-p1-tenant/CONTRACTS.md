# M0-03 contracts and integration points

P1 is the source of truth for **Tenant**, **Runtime Entitlement Projection**, and **Quota execution**. Sibling modules must not read `p1.*` tables directly.

## M0-01 engineering base

This directory is a **runnable Spring Boot module** so M0-03 can be demonstrated before the monolith skeleton exists.

When M0-01 lands, preferred absorption:

```text
nexusone/
  nexus-bootstrap          <- start class, shared Flyway/datasource, security filter chain
  nexus-p1-tenant          <- this module, converted to a jar (non-boot) library
  nexus-data-core          <- M0-02
  nexus-p2-iam             <- M0-04
  nexus-audit-core         <- M0-11
```

Integration steps for M0-01 (do not require changes inside P1 domain):

1. Move `P1TenantApplication` into bootstrap or delete it; keep `@SpringBootApplication` scanning `one.nexus.p1`.
2. Keep Flyway location `classpath:db/migration` **or** remap to `classpath:db/migration/p1` and retain history table `p1_flyway_history` (do not replay V20260922_* against a different history table without baselining).
3. Datasource URL/user/password stay env-only (`NEXUS_DATASOURCE_*`).
4. Disable `nexus.p1.demo-context.enabled`. Wire M0-02 filters instead.
5. Health: this module exposes `/actuator/health` on `NEXUS_P1_PORT` (default 18081). Bootstrap may relocate it.

If M0-01 uses a different module name (`tenant` / `p1`), keep the Java package `one.nexus.p1` and schema `p1` so table ownership stays stable.

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

Result: `ACCEPTED` | `COMPLETED` | `FAILED`. M0-03 stub always returns `ACCEPTED` and stores `p1.admin_init_request`. Create rolls back if the hook returns `FAILED`.

P1 does not:

- create Keycloak users or realms
- store credentials
- assign RBAC beyond requesting "this email should become tenant admin"

Platform administrators live **in the platform tenant**. P2 maintains their persons/roles there. P1 only names duties it requires (`SYSTEM_ADMIN` for lifecycle writes, `AUDIT_ADMIN` must not mutate `p1.audit_event`).

## M0-06 SchemaService / apps

Call:

```http
POST /api/v1/p1/tenants/{id}/quotas/consume
{ "metric": "CUSTOM_LEDGER" | "APP", "amount": 1, "idempotency_key": "..." }
```

or inject `QuotaService` in-process. Over-limit → `P1.QUOTA.001` + `p1.quota.rejected` audit. Disabled tenants cannot consume.

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

Same `p1.entitlement_projection` row. S3+ writer sets `source=SUBSCRIPTION` with `sku_code` + `subscription_id`. M0 only writes `DEFAULT_PACK`. There is no P1 API to author commercial SKUs.

## Error codes

| Code | Meaning |
|---|---|
| P1.TENANT.001 | not found |
| P1.TENANT.002 | code conflict |
| P1.TENANT.003 | invalid state |
| P1.TENANT.004 | platform tenant protected |
| P1.TENANT.005 | invalid code/name |
| P1.TENANT.006 | confirm required |
| P1.QUOTA.001 | over limit rejected |
| P1.QUOTA.002 | invalid metric/amount |
| P1.CONTEXT.001 | platform context required |
| P1.CONTEXT.002 | tenant context mismatch |
| P1.ADMIN.001 | admin init failed |
| P1.DUTY.001 | platform duty denied |
