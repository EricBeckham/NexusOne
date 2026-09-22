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

`select version()`:

```
PostgreSQL 15.19 (Ubuntu 15.19-1.pgdg24.04+2) on x86_64-pc-linux-gnu, compiled by gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0, 64-bit
```

That is PostgreSQL **15.x**, not 16. The JDBC/golden tests **fail** if a reachable server is not 15.x and skip only when the database is unreachable.

## Tests

```bash
mvn -pl nexus-permission-core -am test
```

Result: **27 tests, 0 failures, 0 errors, 0 skipped** on OpenJDK 21.0.10 / Spring Boot **4.1.1** / Flyway **13.7.0**.

| Class | What it proved |
|---|---|
| `PermissionEngineTest` | P0-02 decision table: tenant boundary, explicit deny, user/role/position/department + inherited role union, scope OR share AND context, share without grant, field union + strictest mask, empty context |
| `SnapshotManagerTest` | Poll detects version change without notify; stale / missing DB version / regression fail closed |
| `DataScopeCompilerTest` | SELF / DEPT / DEPT_AND_CHILDREN / SPECIFIED / ALL |
| `PredicateSqlCompilerTest` | Golden parameterized SQL; unknown column rejected |
| `ListDetailTotalPaginationTest` | list / detail / total / page share `tenant_id = ? AND (scope)` |
| `IsolationDataScopeInjectorTest` | Extra AND filters; refuses missing/replaced `tenant_id` |
| `FieldPolicyProjectorTest` | Drop hidden; FULL mask without second auth |
| `GoldenPostgresDataScopeTest` | Rows Alpha + Beta; r4/r9 excluded; cross-tenant UPDATE of r9 rejected |
| `DualTenantIsolationTest` | Tenant A grants do not appear in B snapshot; evaluate(A, B) is `tenant_boundary` |
| `JdbcDualTenantPermissionTest` | Flyway V200+V300 on **PostgreSQL 15.19**; `perm_grant` for B stays empty |
| `RedisInvalidationTest` | Redis 3.2.13 PING / SET EX / GET / DEL / PUBLISH; publish failure is not truth; DB version failure still fail-closed |
| `PermissionApiTest` | Boot 4.1.1 MockMvc; `X-Tenant-Id` → `P2.PERM.0013`; check after grant allows `ledger.read` |
| `SchemaContractTest` | V300 lives in schema `iam`; tenant tables have `tenant_id NOT NULL` |

Flyway history on `nexus_p2_perm_test` (schema `iam`):

```
 version | description         | success
 200     | iam identity        | t
 300     | permission engine   | t
```

Redis `INFO server`: `redis_version:3.2.13`, `redis_mode:standalone`. No Sentinel/replica was started.

## Honest leftover

- Full admin UI / permission matrix / simulation (M0-12)
- Redis Sentinel/Cluster failover (explicitly not this round)
- Time/IP context evaluation (upstream Predicate only)
- Isolation-kernel QueryAst still cannot encode OR trees; product path uses `PermissionQueryBinder` AND-ed onto TenantInjector SQL
- Live Keycloak is M0-04; this module consumes issued `TenantContext`
