# G2 leftover — isolation-only `tenant_only` / catalog whitelist honesty

S0+S1 leftover from [g2-permission.md](./g2-permission.md) and [g2-surfaceguard-bypass-ci.md](./g2-surfaceguard-bypass-ci.md): isolation-only hosts and catalog definition HTTP could skip `SurfaceGuard` even after a P2 `TenantContext` was bound. This package **documents and fail-closes** that skip. **This is not a product-wide G2 close.**

Physical ledger dump is the sibling leftover already on `main` (#137, [g2-physical-dump-surface.md](./g2-physical-dump-surface.md)). Guard-deny audit is already on `main` (#136) and is **not** redesigned here. `AuditPort.append` / `appendNewTransaction` same-TX rules are unchanged. `PermissionEngine` is not redesigned. **G18 stays closed.**

Reuse only: M0-05 `PermissionEngine` / `PermissionService.decide` and the existing list cells (`ledger` / `read` + `ledger` / `write`). Java 21 / Boot **4.1.1**. No Docker.

## What this package adds

| Surface | Entry | Cell | Issued P2 `TenantContext` |
|---|---|---|---|
| Isolation leftover constructor | `IsolationOnlyDecision.allow()` | `tenant_only` | **403** `P2.PERM.0003` / `tenant_only_forbidden` — cannot emit allow |
| Form / H5 / report / list-detail wrappers | existing guards | existing cells | `SurfaceGuard.require(permission, …)` (#136 audit path). No `tenant_only` |
| Catalog decision | `CatalogSurfaceGuard` | `ledger` / `read` or `write` | 403 `P2.PERM.0003` / `0002` / `0001` **before** catalog sinks |
| Ledger definition HTTP | `GET/POST /api/v1/p3/ledgers…` | same cells | same 403 **before** `LedgerDefinitionService` |
| Application catalog HTTP | `GET/POST /api/v1/p3/applications` | same cells | same 403 **before** `ApplicationService` |
| Business-type catalog HTTP | `GET/POST /api/v1/p3/business-types…` | same cells | same 403 **before** provisioner |

Isolation-only hosts with **no** P2 `TenantContext` still return `tenant_only` (same leftover as list/detail). Catalog controllers without a `CatalogSurfaceGuard` bean (no `PermissionModule`) stay the FormController-shaped skip so isolation ITs keep working.

`g2_self_reader` can read catalog and cannot write. `g2_dept_manager` can read and write. Demo `tenant_member` seed is still ALL-scope bootstrap, not G2-complete.

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G2 | Open. Todo HTTP stays leftover. Physical dump is the sibling leftover [g2-physical-dump-surface.md](./g2-physical-dump-surface.md). |
| Todo HTTP (`TodoController`) | Still tenant-person filter. Allowlisted leftover. H5 UI still gates via `/check`. |
| `TenantOnlyDataScopeHook` with demo-bound P2 and no engine | Documented no-engine leftover. Product wrappers fail-close through `IsolationOnlyDecision`. |
| Physical ledger dump | Sibling leftover on `main` (#137). `DumpSurfaceGuard` uses the same `IsolationOnlyDecision` constructor. |
| Guard-deny audit / AuditPort same-TX | Unchanged (#136). This package does not rewrite `emitDenied`. |
| PermissionEngine redesign | No |
| G18 reopen | No |

## Evidence

```bash
./scripts/g2-tenant-only-catalog.sh
```

Equivalent:

```bash
mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=IsolationOnlyDecisionTest,G2CatalogSameEngineTest,CatalogSurfaceGuardTest,G2CatalogSurfaceAgreementTest,CatalogSurfaceApiTest,TenantOnlyDataScopeHookTest,FormSurfaceGuardTest,H5SurfaceGuardTest,ReportSurfaceGuardTest,PermissionDataScopeHookTest,SurfaceGuardBypassGateTest,G2SameEngineTest,G2SurfaceAgreementTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

See [engineering/g2-tenant-only-catalog-EVIDENCE.md](./engineering/g2-tenant-only-catalog-EVIDENCE.md). Recorded on this agent VM after the change. Isolation-only hosts without P2 stay tenant-only. No live JAR + issued-context browser walk on this VM.
