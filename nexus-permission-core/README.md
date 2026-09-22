# M0-05 P2 permission engine (`nexus-permission-core`)

Product-path authorization for NexusOne. This module owns **RBAC merge**, **position/department grants**, **Explicit Deny**, **DataScope**, **FieldPolicy**, and **PolicySnapshot** versioning. It reuses `poc/p0-02/` as the semantic source of truth and consumes M0-04 `TenantContext` / identity APIs. It does **not** fork a second tenant context.

Spring Boot parent: **4.1.1**. Flyway: **13.7.0**, window **V300–V399**, schema **`iam`**. Java 21. PostgreSQL 15.x. Redis invalidation talks **RESP2** for Redis **3.2.x**. No Docker.

## What this delivers

| Capability | Where |
|---|---|
| Unified evaluate: TenantBoundary → Deny → grant union → DataScope OR share AND context → FieldPolicy merge | `PermissionEngine` / `PermissionService` |
| Principals: user, role (with inheritance), position, department | `PermissionEngine.principals` |
| DataScope compile: SELF / DEPT / DEPT_AND_CHILDREN / SPECIFIED / ALL | `DataScopeCompiler` |
| FieldPolicy projection / masking / second-auth | `FieldPolicyProjector` |
| list / detail / total / pagination share one WHERE (`tenant_id` first) | `PermissionQueryBinder` / `ScopedQuery` (OR leftover); product list/detail/count opt-in is `QueryAstScopedQuery` (leftover BoundSql cannot bypass) |
| Isolation kernel seam: AND extra filters, never strip `tenant_id` | `IsolationDataScopeInjector` |
| Query AST SELF/DEPT + single-column IN + FieldPolicy visibility (opt-in) | `QueryAstPolicyCompiler` / `QueryAstScopedQuery` |
| PolicySnapshot version + LRU + fail-closed | `SnapshotManager` |
| Short-cycle version poll (between requests) | `PolicySnapshotPoller` |
| Redis 3.2 PUBLISH/SUBSCRIBE invalidate-only (P0-05 key contract) | `Redis32Client` / `RedisPolicyInvalidation` |
| Role / assignment / grant writes | `PolicyAdminService` + `/api/v1/permission/*` |
| G2 role matrix (not `tenant_member` seed) | `RoleMatrixService` + `GET /matrix` + `POST /matrix/example` |
| G17 three-power duties (system / security / audit) | `PlatformDutyCatalog` + `GET /duties` + `POST /duties/example` |
| Same Decision for list / detail / flow / form studio / form fill / submit-start / H5 / report query/export / physical dump / export / Job / Task | `SurfaceGuard` / `AuthorizedExport` / `AuthorizedSubmit` |

`tenant_id` on the wire is **rejected** (`P2.PERM.0013`). Use `X-Nexus-Context-Id` from M0-04.

## Evaluate order (P0-02)

```
1. TenantBoundary      mismatch → deny (before Deny/grant)
2. Duty conflict       two+ platform admin duties in one tenant → deny
3. Explicit Deny       any matching deny wins
4. ResourceActionGrant union of user/role/position/department (+ inherited roles)
5. DataScope           OR of grant scopes OR record shares; AND limited context
6. FieldPolicy         visible/editable union; mask + second-auth take strictest
```

Record share cannot grant access when there is no resource-action grant.

## Isolation kernel hook

M0-02 injects `tenant_id` first. Pass this module's injector into `IsolationKernel.create`:

```java
IsolationKernel.create(registry, platformPaths, audit, injector::inject, SoftDeleteInjector.passthrough());
```

`IsolationDataScopeInjector` only ANDs extra equality atoms or single-column `IN` lists and refuses to drop or replace `tenant_id`. Mixed-column OR DataScope is compiled by `PermissionQueryBinder.bind(tenantId, decision)` to

```text
tenant_id = ? AND (<scope>)
```

and reused for `COUNT(*)`, `SELECT … LIMIT/OFFSET`, and detail-by-id. That happens **before** the database returns rows. The binder will not attach scope to SQL that lacks `tenant_id`.

## PolicySnapshot + Redis 3.2

- Authoritative clock: `iam.perm_policy_version` (PostgreSQL).
- `SnapshotManager.get` always re-reads that version. Notify (Redis `PUBLISH nexus:v1:permission:invalidate`) only asks for an early reload of the LRU entry.
- `PolicySnapshotPoller` (default `nexus.permission.snapshot-poll-interval=2s`) re-reads versions for cached tenants between requests. Interval `0` disables the scheduler; request-path `get` still polls.
- Lost pub/sub messages cannot leave a process on a stale snapshot — the next `get` or poll tick reloads from the database.
- Redis is **invalidate-only**: PUBLISH + optional SUBSCRIBE + a short-lived version hint key. The snapshot body is never written to Redis.
- Redis GET/SET/PUBLISH/SUBSCRIBE failure is logged; evaluation continues from the database. Redis is not a truth source.
- Version read or snapshot load failure on the request path is **fail-closed** (`SnapshotUnavailable` / `P2.PERM.0005`). Background poll failures are skipped.
- G9 Redis-down / decide==durable evidence: [docs/g9-policysnapshot-invalidate.md](../docs/g9-policysnapshot-invalidate.md). Version poll remainder: [docs/g9-policy-snapshot.md](../docs/g9-policy-snapshot.md). Fail-closed DENY leftover: [docs/g9-failover-remainder.md](../docs/g9-failover-remainder.md). In-process dual-reader / lost-invalidate version-poll leftover: [docs/g9-multinode-partition.md](../docs/g9-multinode-partition.md). Sentinel / Cluster honesty leftover: [docs/g9-sentinel-cluster-honesty.md](../docs/g9-sentinel-cluster-honesty.md). Live multi-JVM / replica promotion honesty leftover: [docs/g9-live-multi-jvm-replica.md](../docs/g9-live-multi-jvm-replica.md).
- High-risk writes fail closed if the loaded version is not current.
- Key shape (P0-05): `nexus:v1:{tenant-id}:cache:permission:snapshot` (reserved; unused as a payload).
- Channel: `nexus:v1:permission:invalidate`.
- Request-path version/load is bounded by `nexus.permission.snapshot-load-timeout` (default 2s). Hang past the clock fail-closes `P2.PERM.0005`. See [docs/g9-p2-timeout-reject.md](../docs/g9-p2-timeout-reject.md).
- **Not claimed:** Redis Sentinel/Cluster failover, replica promotion, or a live multi-JVM network partition. In-process dual-reader / lost-invalidate version-poll is [docs/g9-multinode-partition.md](../docs/g9-multinode-partition.md). Sentinel / Cluster honesty SKIP is [docs/g9-sentinel-cluster-honesty.md](../docs/g9-sentinel-cluster-honesty.md). Live multi-JVM / replica promotion honesty SKIP is [docs/g9-live-multi-jvm-replica.md](../docs/g9-live-multi-jvm-replica.md). Those leftovers are not a full G9 failover close.
- Demo `tenant_member` ALL-scope seed is **not** treated as permission-complete. Propagation tests write a real `g9_self_reader` SELF grant.

## Run

Memory (default, no Keycloak / Redis / PostgreSQL):

```bash
mvn -pl nexus-permission-core -am test
```

PostgreSQL 15.x golden + JDBC isolation (skips only when the database is unreachable; **fails** if a reachable server is not 15.x):

```bash
export NEXUS_PERM_TEST_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/nexus_p2_perm_test'
export NEXUS_PERM_TEST_JDBC_USER='ubuntu'
# password via env if the role has one
mvn -pl nexus-permission-core -am test
```

Redis 3.2 invalidation tests talk to `127.0.0.1:6379` when a 3.2 process is up; they skip the live client if it is not.

Env (never commit secrets):

```bash
export NEXUS_PERM_PERSISTENCE=jdbc
export NEXUS_DB_URL='jdbc:postgresql://127.0.0.1:5432/nexusone'
export NEXUS_DB_USER='nexus'
export NEXUS_DB_PASSWORD='...'
export NEXUS_REDIS_ENABLED=true
export NEXUS_REDIS_HOST=127.0.0.1
export NEXUS_REDIS_PORT=6379
```

## Explicitly out of scope

- Full G17 (GM TLS / classified / 密码机). Duty catalog is one slice; admin MFA is a separate slice on main ([docs/g17-admin-mfa.md](../docs/g17-admin-mfa.md))
- Product-wide G2. Form studio + form fill / POST submissions + path submit-start dual-check (`form.write` + process start) + H5 `/m/*` list/detail/form/todo/query + report query/export + physical dump / raw export HTTP + catalog definition HTTP + todo list HTTP + export Task type + Job/Task submit + export cacheStatus are on SurfaceGuard; general cache, H5 home/files/notify/login/host, and `pg_dump` ops are not. H5 every-page honesty is [docs/g2-h5-every-page.md](../docs/g2-h5-every-page.md). Raw `PermissionEngine` / IsolatedExport / unguarded P3 surface callers fail the static gate ([docs/g2-surfaceguard-bypass-ci.md](../docs/g2-surfaceguard-bypass-ci.md)).
- Generic ABAC / policy DSL
- Time/IP condition evaluation beyond an upstream-supplied context Predicate
- Redis failover / Cluster / Sentinel (P2-down timeout-then-reject is on `SnapshotManager`, not Sentinel; honesty SKIP is [docs/g9-sentinel-cluster-honesty.md](../docs/g9-sentinel-cluster-honesty.md); live multi-JVM / replica promotion honesty SKIP is [docs/g9-live-multi-jvm-replica.md](../docs/g9-live-multi-jvm-replica.md))
- Query AST mixed-column OR (SELF∨DEPT) DataScope (SELF/DEPT equality + single-column `IN` + FieldPolicy visibility can be encoded via `QueryAstPolicyCompiler`; export hidden drop is [docs/g1-export-fieldpolicy-drop.md](../docs/g1-export-fieldpolicy-drop.md); mask / second-auth honesty leftover is [docs/g1-fieldpolicy-mask-second-auth.md](../docs/g1-fieldpolicy-mask-second-auth.md); OUTER/CROSS JOIN and nested subquery isolation is M0-02)
- SQL Guard implementation (owned by M0-02)
- Live Keycloak (owned by M0-04)
- Docker / Compose as a hard dependency
