# G1 remaining: QueryCompiler bypass CI gate

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. Query AST INNER / OUTER / CROSS JOIN, subquery, batch, sort, page, and export FieldPolicy already landed on `main`. This package adds the leftover **static gate** called out there: callers that skip `QueryCompiler` / `IsolationKernel.jdbc()` for tenant tables.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining. It does **not** rewrite `AuditPort`. Product G18 stays closed.

## What this close covers

A Checkstyle / SpotBugs-style source rule (`QueryCompilerBypassGate`) plus an ArchUnit layer rule reject **raw JDBC / native SQL** that bypasses the kernel for tenant tables. The gate runs in `mvn test` and in the isolation-kernel workflow.

| Surface | Guard | Fail-before |
|---|---|---|
| Product `src/main` JDBC outside the allowlist | `QueryCompilerBypassGate` rule `raw-jdbc-outside-allowlist` | merge / `mvn test` |
| Tenant physical / Query AST table SQL (`l_*`, `a_*`, `ledger_entry` / `line` / `note`) plus JDBC | rule `tenant-table-sql-bypass` | merge / `mvn test` |
| Application / API / domain / reserved modules | ArchUnit `NO_JDBC` | `mvn -pl nexus-bootstrap test` |

An **intentional** fixture (`l_demo_leave` + `JdbcTemplate`, and `RawTenantSqlFixture`) fails the gate. Current product `src/main` passes.

## Allowlisted modules

These modules may use JDBC for **platform / catalog / identity** tables. They are not a license to issue raw DML against tenant physical tables. Tenant-table SQL + JDBC is allowed only on the compiler path and SchemaService DDL.

| Module | Why JDBC is allowed | Prefixes |
|---|---|---|
| `isolation-kernel` | `QueryCompiler`, `GuardedJdbc`, `NativeSqlPort` — only public tenant-table JDBC path | `modules/isolation-kernel/src/main/` |
| `nexus-audit-core` | `platform.audit_*` hash chain / archive | `one.nexus.audit/` |
| `nexus-event-core` | `platform.exec_*` outbox / idempotency / dead-letter | `event/jdbc/`, `event/support/` |
| `nexus-task-core` | `platform.exec_task` | `task/jdbc/` |
| `nexus-p1-tenant` | P1 tenant catalog / quota | `p1/infra/jdbc/` |
| `nexus-p2-iam` | `iam.*` identity store | `p2/persist/`, `IdentityConfiguration` |
| `nexus-permission-core` | `iam.perm_*` snapshot | `permission/persist/`, `PermissionConfiguration` |
| `nexus-file-core` | `files.file_object`; IsolationKernel file key guard | `file/infra/`, `FileConfiguration` |
| `nexus-notification-core` | `notify.inbox`; IsolationKernel notify filter | `notification/infra/`, `NotificationConfiguration` |
| `nexus-p3-studio` | catalog / form / flow metadata; SchemaService DDL; `GuardedLedgerStore` | `p3/infra/jdbc/`, `p3/infra/schema/`, `JdbcFormStore`, flow persist/engine, `StudioConfiguration` |
| `nexus-bootstrap` | `platform.runtime_meta` health only | `RuntimeMetaController` |

**Not allowlisted** (must stay JDBC-free): `nexus-common`, `nexus-data-core`, `nexus-expression-core`, `nexus-cache-core`, `nexus-crypto-core`, `nexus-p4-market`, `nexus-p5-connect`. Application / API / domain / path packages inside allowlisted modules are also JDBC-free.

Tenant-table SQL allowlist (compiler + SchemaService + product adapter only):

- `GuardedJdbc`, `NativeSqlPort`, `QueryCompiler`, `PostgresRenderer`, `TenantInjector`
- `GuardedLedgerStore` (writes via `kernel.jdbc()`; list/detail still use M0-05 `BoundSql`)
- `JdbcSchemaService`, `PostgresDialectRenderer` (DDL from `SchemaAst`, not caller SQL)

Source of truth: `QueryCompilerBypassAllowlist`.

## Evidence

Recorded on this agent VM. Java **21.0.10**, Maven **3.9.16**, Spring Boot **4.1.1**. No Docker. Isolation-kernel tests stay in-process (Zonky PostgreSQL **15.18** is unused by this static gate).

```
mvn -f modules/isolation-kernel/pom.xml test -Dtest=QueryCompilerBypassGateTest,SqlBypassScannerTest
# Tests run: 9 (7 gate + 2 scanner), Failures: 0
# full isolation-kernel suite: Tests run: 55, Failures: 0  BUILD SUCCESS  2026-09-22T17:41:54Z
# rebased onto main@440a504 (#113 G7 browser withdraw)

mvn -f modules/isolation-kernel/pom.xml -q exec:java@querycompiler-bypass -Dexec.args="--repo ."
# PASS QueryCompiler bypass gate: /workspace

mvn -pl nexus-bootstrap -am test \
    -Dtest=G1QueryCompilerBypassTest,QueryCompilerBypassArchTest \
    -Dsurefire.failIfNoSpecifiedTests=false
# BUILD SUCCESS  2026-09-22T17:42:01Z
# G1QueryCompilerBypassTest 1/1; QueryCompilerBypassArchTest 2/2
# (ArchUnit product layers pass; intentional RawTenantSqlFixture fails the rule)
```

| Suite | What it proved |
|---|---|
| `QueryCompilerBypassGateTest` | `l_demo_leave` + `JdbcTemplate` is `tenant-table-sql-bypass`; API-layer `prepareStatement` is `raw-jdbc-outside-allowlist`; mini-product market dump fails and catalog JDBC is allowed; real product `src/main` is empty |
| `QueryCompilerBypassArchTest` | `one.nexus.p3.application` / `api` / `domain` / `path` / `studio.api` and reserved modules do not depend on `java.sql` / Spring JDBC; `RawTenantSqlFixture` throws `AssertionError` |
| `G1QueryCompilerBypassTest` | Same product-tree scan from the boot module |

CLI (workflow):

```bash
mvn -f modules/isolation-kernel/pom.xml -q exec:java@querycompiler-bypass \
  -Dexec.args="--repo ."
```

## Honest leftover G1 rows

This is **not** a product-wide “zero leak” close and **not** a full G1 close.

| Still open | Why |
|---|---|
| `GuardedLedgerStore` reads | List/detail still execute M0-05 `BoundSql` on `PreparedStatement`. Writes already go through `kernel.jdbc()`. This package does not rewrite that read path. |
| Allowlisted platform JDBC | Audit / outbox / IAM / file / notify / catalog stay on raw JDBC for non-tenant-physical tables. |
| Regex / ArchUnit limits | Aliases, reflection, generated code, and JNI are not covered. Keep the allowlist tight. |
| Report UI / physical ledger dump | Unchanged. The gate blocks new raw dump callers; it does not add a report screen. |
| UI / H5 every page | Not re-walked. |
| `P3RequestContext` | Unchanged. The gate does not add a holder. |
| G4 same-request chain | Unchanged. No `AuditPort` rewrite. |
| GaussDB / live Redis / live MinIO | Unchanged. |
