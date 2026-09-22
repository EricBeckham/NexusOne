# M0-02 isolation kernel

Focused Java 21 module for NexusOne tenant isolation. It is not the M0-01 application skeleton. M0-01 should depend on this artifact (or copy the package) and bind `RequestIsolationPolicy` in a servlet/Spring filter after P2 membership is known.

## What other modules call

| Module | API |
|---|---|
| M0-01 | `RequestIsolationPolicy.bindTenant`, `AccessContextHolder`, `GuardedJdbc`, `SqlBypassScanner` / `QueryCompilerBypassGate` CI |
| M0-03 | Do not issue context from tenant admin UI; platform tenant still uses `TenantContextIssuer` / `PlatformContextIssuer` |
| M0-04 | Build `AuthenticatedMembership` from session + P2 membership, then call `bindTenant` |
| M0-05 | Implement `DataScopeInjector` and pass it to `IsolationKernel.create` — it cannot strip `tenant_id`; `andEquals` may AND SELF/DEPT atoms |
| M0-09 | `IsolatedExecutor`, `IsolatedEnvelope`, `ContextSnapshot` |
| M0-10 | `CacheKeyGuard`, `FileObjectKeyGuard` |
| Export / G1 | `ExportGuard`, `IsolatedExport` (`P1.M02.019`); FieldPolicy gate `P1.M02.021` |
| Query AST JOIN / G1 | `QueryJoin` INNER / OUTER / CROSS / `TenantInjector.injectJoin` (`P1.M02.020`) |
| Query AST remainder / G1 | subquery `P1.M02.022`, batch write `P1.M02.023`, sort `P1.M02.024`, page `P1.M02.025` |
| M0-11 | `IsolationAuditSink` / `IsolationAuditEvent` (static guards emit via `IsolationAuditorHolder`) |

Tenant predicate injection has no disable flag. The only audited cross-tenant path is a whitelist `PlatformContext`.

## Run

```bash
mvn -f modules/isolation-kernel/pom.xml test
```

PostgreSQL 15.18 is started in-process (zonky embedded binaries). No Docker. Pointing at a local `PG*` server is unnecessary for this kernel.

Static bypass demo (same fixtures the PoC used):

```bash
# positive: must pass
mvn -f modules/isolation-kernel/pom.xml -q exec:java \
  -Dexec.args="--source modules/isolation-kernel/src/test/resources/sql-bypass-fixtures/positive --infra modules/isolation-kernel/src/test/resources/sql-bypass-fixtures/positive/infrastructure"

# negative jdbc fixture: must fail
mvn -f modules/isolation-kernel/pom.xml -q exec:java \
  -Dexec.args="--source modules/isolation-kernel/src/test/resources/sql-bypass-fixtures/negative/jdbc"

# product src/main: QueryCompiler bypass gate must pass
mvn -f modules/isolation-kernel/pom.xml -q exec:java@querycompiler-bypass \
  -Dexec.args="--repo ."
```

Allowlisted modules and the intentional-violation fixtures are recorded in [docs/g1-querycompiler-bypass-ci.md](../../docs/g1-querycompiler-bypass-ci.md).

## Contracts

See [docs/CONTEXT_PROPAGATION.md](docs/CONTEXT_PROPAGATION.md).
