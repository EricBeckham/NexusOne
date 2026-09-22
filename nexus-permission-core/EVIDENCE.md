# M0-05 P2 permission engine evidence

Recorded on the agent VM against latest `main` (includes M0-01～04 + Keycloak smoke). **Full admin UI was not built.** **Redis Sentinel/Cluster failover is still not claimed.** G9 Redis-down / invalidate-only evidence is in [docs/g9-policysnapshot-invalidate.md](../docs/g9-policysnapshot-invalidate.md).

## G2 (S0+S1) — role matrix + same engine

G2 adds a role matrix, DataScope/field cells that are **not** the `tenant_member` ALL-scope seed, and one `SurfaceGuard` for list / detail / flow / form studio. Product-wide G2 (report / export / H5) stays open. See [docs/g2-permission.md](../docs/g2-permission.md).

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

Result (M0-05 baseline): **27 tests, 0 failures, 0 errors, 0 skipped** on OpenJDK 21.0.10 / Spring Boot **4.1.1** / Flyway **13.7.0**.

G2 additions: `G2SameEngineTest`, `G2RoleMatrixApiTest`, `G2SurfaceAgreementTest` (p3). Form studio remainder: `FormSurfaceGuardTest`, `FormStudioPermissionApiTest`.

Recorded **2026-09-22T15:29:11Z** on this agent VM (OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**). PostgreSQL was not listening; JDBC/golden tests skipped as designed.

```bash
mvn -pl nexus-permission-core -am test
# BUILD SUCCESS — Tests run: 32, Failures: 0, Errors: 0, Skipped: 4

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2SameEngineTest,G2RoleMatrixApiTest,G2SurfaceAgreementTest,PermissionDataScopeHookTest,FieldPolicyProjectorTest,PermissionApiTest,TenantMemberGrantSeedApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS at 2026-09-22T15:28:46Z
# permission-core: G2SameEngine 2, FieldPolicy 1, G2RoleMatrixApi 1, PermissionApi 2, TenantMemberGrantSeed 1
# p3-studio: PermissionDataScopeHook 4, G2SurfaceAgreement 3
```

Form studio remainder **2026-09-22T16:43:49Z** (same VM, OpenJDK **21.0.10** / Maven **3.8.7** / Boot **4.1.1**):

```bash
mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2SameEngineTest,G2RoleMatrixApiTest,G2SurfaceAgreementTest,PermissionDataScopeHookTest,FormSurfaceGuardTest,FormStudioPermissionApiTest,FieldPolicyProjectorTest,PermissionApiTest,TenantMemberGrantSeedApiTest,FormApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS
# permission-core: 7 run / 0 fail
# p3-studio: 18 run / 0 fail (FormStudioPermissionApiTest 3, FormSurfaceGuardTest 5, G2SurfaceAgreementTest 5)
```

G9 failover evidence after rebase onto `main@215308f` (this VM, OpenJDK **21.0.10** / Maven **3.8.7** / Boot **4.1.1**):

```bash
./scripts/g9-policysnapshot-invalidate.sh
# BUILD SUCCESS 2026-09-22T15:55:05Z — Tests run: 21, Failures: 0, Errors: 0, Skipped: 3

mvn -pl nexus-permission-core -am test
# BUILD SUCCESS 2026-09-22T15:55:20Z — Tests run: 47, Failures: 0, Errors: 0, Skipped: 5
```

| Class | What it proved |
|---|---|
| `PermissionEngineTest` | P0-02 decision table: tenant boundary, explicit deny, user/role/position/department + inherited role union, scope OR share AND context, share without grant, field union + strictest mask, empty context |
| `SnapshotManagerTest` | Poll detects version change without notify; stale / missing DB version / regression fail closed |
| `G9PolicySnapshotFailoverTest` | Redis-down grant still decides from durable snapshot via `PermissionEngine`; lost invalidate channel reloads; Redis snapshot key is not truth; durable miss is `P2.PERM.0005`; no second TenantContext |
| `DataScopeCompilerTest` | SELF / DEPT / DEPT_AND_CHILDREN / SPECIFIED / ALL |
| `PredicateSqlCompilerTest` | Golden parameterized SQL; unknown column rejected |
| `ListDetailTotalPaginationTest` | list / detail / total / page share `tenant_id = ? AND (scope)` |
| `IsolationDataScopeInjectorTest` | Extra AND filters; refuses missing/replaced `tenant_id` |
| `FieldPolicyProjectorTest` | Drop hidden; FULL mask without second auth |
| `GoldenPostgresDataScopeTest` | Rows Alpha + Beta; r4/r9 excluded; cross-tenant UPDATE of r9 rejected |
| `DualTenantIsolationTest` | Tenant A grants do not appear in B snapshot; evaluate(A, B) is `tenant_boundary` |
| `JdbcDualTenantPermissionTest` | Flyway V200+V300 on **PostgreSQL 15.19**; `perm_grant` for B stays empty |
| `RedisInvalidationTest` | Redis 3.2.13 PING / SET EX / GET / DEL / PUBLISH when present; publish failure is not truth; Redis-down still loads durable version; DB version failure still fail-closed |
| `PermissionApiTest` | Boot 4.1.1 MockMvc; `X-Tenant-Id` → `P2.PERM.0013`; check after grant allows `ledger.read` |
| `G2SameEngineTest` | Role-matrix cells (SELF + hidden salary, DEPT + masked salary, approver, form designer). List/detail share WHERE; flow / form.write `no_grant` denied by `SurfaceGuard` |
| `G2RoleMatrixApiTest` | `GET /matrix` notes tenant_member is not complete; example install is differentiated; check uses `engine=PermissionEngine`; `form.read` allowed for self_reader, `form.write` is `no_grant` |
| `FormSurfaceGuardTest` / `FormStudioPermissionApiTest` (p3) | Form list/create 403 `P2.PERM.0003`/`0002`/`0001`; isolation-only stays tenant-only; `g2_form_designer` can write |
| `SchemaContractTest` | V300 lives in schema `iam`; tenant tables have `tenant_id NOT NULL` |

Flyway history on `nexus_p2_perm_test` (schema `iam`):

```
 version | description         | success
 200     | iam identity        | t
 300     | permission engine   | t
```

Redis `INFO server`: `redis_version:3.2.13`, `redis_mode:standalone`. No Sentinel/replica was started.

## G9 remainder

Product poller + invalidate listener is on `main` (#89). **Not** a full G9 failover close. **Not** a `tenant_member` completeness close. G2 matrix is on `main` (#73). Redis-down / decide==durable evidence stays on open [#83](https://github.com/EricBeckham/NexusOne/pull/83) (`docs/g9-policysnapshot-invalidate.md`).

Recorded **2026-09-22T15:55:20Z** on this agent VM after rebase onto `main@215308f`. OpenJDK **21.0.10**, Maven **3.8.7**, Spring Boot **4.1.1**. Zonky in-process PostgreSQL **15.18**. No Docker. No live `127.0.0.1:5432` / Redis 3.2 listener.

```
mvn -pl nexus-permission-core -am test
# BUILD SUCCESS  2026-09-22T15:46:14Z (on main@26d1f94, #89) — Tests run: 42, Failures: 0, Errors: 0, Skipped: 5
# BUILD SUCCESS  2026-09-22T15:55:20Z (this PR + #89 on 215308f) — Tests run: 47, Failures: 0, Errors: 0, Skipped: 5
```

Skipped 5: `RedisInvalidationTest` 3 (no Redis on `:6379`), `GoldenPostgresDataScopeTest` 1, `JdbcDualTenantPermissionTest` 1 (no live PG listener). JDBC poll proof is `JdbcPolicySnapshotPollIT` on Zonky **15.18** (Flyway V200 + V300, schema `iam`).

| Class | Count | What it proved |
|---|---|---|
| `SnapshotManagerTest` | 5/5 | Poll-on-get; `pollCached` reloads LRU without a caller `get`; background poll swallows load failure |
| `G9PolicySnapshotPropagationTest` | 4/4 | Two instances, shared repo: lost channel still converges on next `get`; shared invalidate marks the reader; poller tick reloads; role is `g9_self_reader` not `tenant_member` |
| `G9PolicySnapshotApiTest` | 1/1 | Boot 4.1.1 MockMvc: grant bumps snapshot `version`; next `/check` allows `ledger/read` |
| `JdbcPolicySnapshotPollIT` | 1/1 | Zonky PostgreSQL **15.18**: `iam.perm_policy_version` is the clock; reader without the channel reloads on poll tick |
| `RedisInvalidationTest` | 1/1 (+3 skip) | Dead Redis publish is observed and is not truth; live PING/SUBSCRIBE skipped |

## Honest leftover

- Product-wide G2 (report / export / H5 / form-definition list)
- Simulation / time-IP ABAC admin
- Redis Sentinel/Cluster failover (explicitly not this round). Redis-down / lost-channel decide==durable evidence is [docs/g9-policysnapshot-invalidate.md](../docs/g9-policysnapshot-invalidate.md)
- G9 bounded timeout when P2 is down (ordinary query still fail-closes; no retry-deadline)
- Time/IP context evaluation (upstream Predicate only)
- Isolation-kernel QueryAst still cannot encode OR trees; product path uses `PermissionQueryBinder` AND-ed onto TenantInjector SQL
- Live Keycloak is M0-04; this module consumes issued `TenantContext`
- Demo `tenant_member` ALL-scope seed remains for the closed G18 walk and is not permission-complete
