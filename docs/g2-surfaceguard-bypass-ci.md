# G2 remaining: SurfaceGuard / PermissionEngine bypass CI gate

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. Role matrix, list/detail/flow/form studio/form fill/submit-start/H5 `/m/*`/export/Job/Task already share `SurfaceGuard` on `main`. This package adds the leftover **static gate** called out there: callers that skip `SurfaceGuard` / `PermissionEngine`.

It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining. It does **not** rewrite `AuditPort`. Product G18 stays closed.

This is **orthogonal** to the G1 QueryCompiler / JDBC bypass gate now on `main` (#119). This gate does not scan JDBC, MyBatis, or tenant-table SQL.

## What this close covers

A Checkstyle / SpotBugs-style source rule (`SurfaceGuardBypassGate`) plus an ArchUnit layer rule reject **raw permission skips** and **unguarded P3 surfaces** that should go through `SurfaceGuard`. The gate runs in `mvn test` and in the `permission-surfaceguard` workflow.

| Surface | Guard | Fail-before |
|---|---|---|
| Product `src/main` `PermissionEngine.evaluate` / `can` outside the engine | `SurfaceGuardBypassGate` rule `raw-engine-bypass` | merge / `mvn test` |
| IsolatedExport sink methods outside `AuthorizedExport` | rule `isolated-export-bypass` | merge / `mvn test` |
| `new Decision(true, …)` outside engine / documented `tenant_only` | rule `raw-permission-skip` | merge / `mvn test` |
| P3 RestController on `/records` `/forms` `/process` `/path` `/todos` without a SurfaceGuard token | rule `unguarded-p3-surface` | merge / `mvn test` |
| Application / API / domain / path / reserved modules | ArchUnit `NO_RAW_ENGINE` / `NO_RAW_EXPORT` | `mvn -pl nexus-bootstrap test` |

An **intentional** fixture (`PermissionEngine.evaluate`, `IsolatedExport.submit`, `new Decision(true, "always_allow")`, unguarded `/records` RestController, and `RawSurfaceGuardBypassFixture`) fails the gate. Current product `src/main` passes.

## Allowlisted paths

These files may call the engine, IsolatedExport sinks, or construct an allow `Decision` because they **are** the engine / the single interpretation / a documented leftover. They are not a license to skip `SurfaceGuard` from a new P3 API.

| Path | Why |
|---|---|
| `PermissionEngine` / `PermissionService` | Only evaluate / `can` path |
| `SurfaceGuard` / `SurfaceCatalog` / `AuthorizedSubmit` / `AuthorizedExport` | Single Decision interpretation |
| `PermissionController` | `POST /check` uses `decide` + `SurfaceGuard.asApi` |
| `FormSurfaceGuard` / `H5SurfaceGuard` / `ReportSurfaceGuard` / `DumpSurfaceGuard` / `CatalogSurfaceGuard` / `PermissionDataScopeHook` | Product wrappers around `SurfaceGuard.require` |
| `SequentialApprovalService` / `BusinessPathService` / `FormController` / `LedgerRecordService` | Guarded P3 surfaces |
| `AuditTraceController` / `PermissionSurfaceConfiguration` | `audit.read` + Job/Task `AuthorizedSubmit` |
| `IsolatedExport` | Isolation-only export; product callers use `AuthorizedExport` |
| `IsolationOnlyDecision` + isolation-only `new Decision(true, "tenant_only")` | Single constructor; fail-closes when a P2 `TenantContext` exists |
| `TenantOnlyDataScopeHook` | No-engine leftover when demo headers bind P2 without `PermissionModule` |
| `TodoController` | Now `TodoSurfaceGuard` (`process.task`/`read`). See [g2-todo-http-surface.md](./g2-todo-http-surface.md). |

**Not allowlisted** (must stay SurfaceGuard-free of raw engine / IsolatedExport): `nexus-p4-market`, `nexus-p5-connect`. Catalog controllers are product wrappers around `CatalogSurfaceGuard` (not `/records`).

Source of truth: `SurfaceGuardBypassAllowlist`.

## Evidence

Recorded on this agent VM. Java **21.0.10**, Maven **3.9.16**, Spring Boot **4.1.1**. No Docker. This static gate does not start PostgreSQL.

```
./scripts/g2-surfaceguard-bypass-ci.sh
```

See the evidence table filled after the gate run on this branch.

CLI (workflow):

```bash
mvn -f nexus-permission-core/pom.xml -q exec:java@surfaceguard-bypass \
  -Dexec.args="--repo ."
```

Intentional IsolatedExport fixture (must fail):

```bash
mvn -f nexus-permission-core/pom.xml -q exec:java@surfaceguard-bypass \
  -Dexec.args="--source nexus-permission-core/src/test/resources/surfaceguard-bypass-fixtures/negative/export --no-allowlist"
# expected non-zero
```

## Honest leftover G2 rows

This is **not** a product-wide G2 close.

| Still open | Why |
|---|---|
| `TodoController` / `TodoQueryService` | HTTP now `TodoSurfaceGuard` (`process.task`/`read`) when `PermissionModule` is present. See [g2-todo-http-surface.md](./g2-todo-http-surface.md). Isolation hosts without the bean stay the FormController-shaped skip. |
| Ledger / application / business-type catalog HTTP | Definition HTTP now goes through `CatalogSurfaceGuard` when `PermissionModule` is present. Isolation hosts without the bean stay the FormController-shaped skip. |
| Isolation-only `tenant_only` | Engine-backed wrappers use `IsolationOnlyDecision.allow()` and **fail-close** when a P2 `TenantContext` exists. See [g2-tenant-only-catalog.md](./g2-tenant-only-catalog.md). |
| Physical dump HTTP | Product dump HTTP is the sibling leftover [g2-physical-dump-surface.md](./g2-physical-dump-surface.md). The gate now also flags unguarded `/dumps` and raw `writeProjected`. `pg_dump` ops stay open. |
| Regex / ArchUnit limits | Aliases, reflection, generated code, and JNI are not covered. Keep the allowlist tight. |
| UI / H5 every page | Not re-walked. Desktop `/p3` unchanged. |
| G1 QueryCompiler / JDBC callers | Orthogonal open leftover (#119). This package does not scan JDBC. |
| G4 same-request chain | Unchanged. No `AuditPort` rewrite. |
| GaussDB / live Redis / live MinIO | Unchanged. |
