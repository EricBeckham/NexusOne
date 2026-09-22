# M0-05 P2 permission engine evidence

Recorded on the agent VM against latest `main` (includes M0-01～04 + Keycloak smoke). **Full admin UI was not built.** **Redis failover was not claimed or tested.**

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10 | `java -version` |
| Maven | 3.8.7 | |
| Spring Boot parent | **4.1.1** | same BOM as M0-01 |
| Flyway | **13.7.0** | window V300–V399, schema `iam` |
| PostgreSQL | **15.19** (`Ubuntu 15.19-1.pgdg24.04+2`) | `select version()` on `nexus_p2_perm_test` |
| Redis | **3.2.13** | native `redis-server`, RESP2 only, `127.0.0.1:6379` |
| Runtime | local `mvn test` | No Docker / Compose |

## Tests

```bash
mvn -pl nexus-permission-core -am test
```

Covered:

- P0-02 decision table: tenant boundary, explicit deny, user/role/position/department + inherited role union, scope OR share AND context, share without grant, field union + strictest mask, empty context
- Snapshot poll detects version change without notify; stale / missing DB version / regression fail closed
- DataScope compile: SELF / DEPT / DEPT_AND_CHILDREN / SPECIFIED / ALL
- Golden PostgreSQL: rows Alpha + Beta; r4/r9 excluded; cross-tenant UPDATE of r9 rejected by tenant predicate
- list / detail / total / pagination share the same `tenant_id = ? AND (scope)` WHERE
- Isolation injector refuses missing/replaced `tenant_id`
- FieldPolicy projection: drop hidden, FULL mask without second auth
- Dual-tenant in-memory: tenant A grants do not appear in B snapshot; evaluate(A, B) is `tenant_boundary`
- JDBC dual-tenant on PostgreSQL 15.19: `perm_grant` for B stays empty
- Redis 3.2 PING / SET EX / GET / DEL / PUBLISH; publish failure does not become deny-or-allow; DB version failure still fail-closed
- HTTP: `/api/v1/permission/health`; `X-Tenant-Id` → `P2.PERM.0013`; check after grant allows ledger.read

## Honest leftover

- Full admin UI / permission matrix / simulation (M0-12)
- Redis Sentinel/Cluster failover (explicitly not this round)
- Time/IP context evaluation (upstream Predicate only)
- Isolation-kernel QueryAst still cannot encode OR trees; product path uses `PermissionQueryBinder` AND-ed onto TenantInjector SQL
- Live Keycloak is M0-04; this module consumes issued `TenantContext`
