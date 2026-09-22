# G2 permission — role matrix, DataScope, field policy

S0+S1 PostgreSQL release work on M0-05 `PermissionEngine` / `PolicySnapshot`. **G18 stays closed.** This is not a redo of P0/M0 packages and does not fork TenantContext or rewrite AuditPort.

## What closed on this slice

List, detail, flow, form studio, form fill, export, Job, and Task interpret the **same** `PermissionService.decide` `Decision` through `SurfaceGuard`:

| Surface | Entry | `no_grant` / `explicit_deny` / `tenant_boundary` |
|---|---|---|
| UI / API evaluate | `POST /api/v1/permission/check` | returns `{allowed, reason, policy_version, fields, engine}` |
| Ledger list / detail | `PermissionDataScopeHook.bind` → `SurfaceGuard.require` | 403 (`P2.PERM.0003` for `no_grant`) |
| Ledger write | same hook `require("ledger","write")` + `FieldPolicyProjector.assertWritable` | 403 |
| Flow start / approve / reject / withdraw | `SequentialApprovalService.guard` → `SurfaceGuard.require` | 403 (`P3.FLOW.0004`, same reason string) |
| Form definition list / detail | `FormSurfaceGuard.requireRead` → `form` / `read` | 403 (`P2.PERM.0003` / `0002` / `0001`) |
| Form create / update / validate / publish | `FormSurfaceGuard.requireWrite` → `form` / `write` | 403 (same codes) |
| Form fill / `POST …/submissions` | `FormSurfaceGuard.requireFill` → `form` / `write` | 403 (same codes) **before** `form_submission` insert |
| Path submit-start with `form_id` | `BusinessPathService` → `requireFill` then `SequentialApprovalService.guard` → `process.instance` / `start` | 403 (`P2.PERM.*` for missing `form.write`) **before** submit / start. G18 leave seed still works. |
| Form submission list | `FormSurfaceGuard.requireRead` → `form` / `read` | 403 (same codes) |
| Export Task type `ledger.export.csv` | `AuthorizedExport` → `SurfaceGuard` then `IsolatedExport` | 403 (`P2.PERM.0003`) **before** file / cache / dispatch sinks. Cross-tenant remains `P1.M02.019`. |
| Job / Task submit | `TaskSubmitGuard` → `AuthorizedSubmit` then enqueue | 403 **before** `ExecutionIsolation` / JDBC / Quartz. Isolation-only (no P2 context) still skips this engine. |
| Export cache status | same export Task grant via `AuthorizedExport.cacheStatus` | cheap ride on `ledger.export.csv` / `submit`. General cache keys stay isolation-only. |

DataScope (SELF / DEPT / DEPT_AND_CHILDREN / SPECIFIED / ALL) still compiles in `DataScopeCompiler` and binds with `PermissionQueryBinder` so list / detail / total share `tenant_id = ? AND (scope)`. FieldPolicy drops hidden fields on read and rejects non-editable fields on write.

Role matrix is a first-class read model, not the demo seed:

- `GET /api/v1/permission/roles|assignments|grants|matrix`
- `POST /api/v1/permission/matrix/example` installs `g2_self_reader` (SELF + salary hidden, `form.read`), `g2_dept_manager` (DEPT_AND_CHILDREN + salary masked / not editable, `form.read`+`form.write` + export/Job/Task submit), `g2_approver` (process start/approve/reject), `g2_form_designer` (`form.read`+`form.write`). **Does not assign to the current person.**
- Vue `/p2/permissions/{roles|datascope|fields}` reads that matrix. Ledger detail shows the same `/check` decision.

## `tenant_member` seed is not permission-complete

Product default `nexus.permission.seed-tenant-member-grants=true` still exists so the closed G18 leave walk keeps working. That role is **ALL-scope, empty field map, auto-assigned to the current person**. G2 treats it as demo bootstrap.

A tenant is **differentiated** (matrix `differentiated_policy=true`) only when at least one grant has a DataScope other than ALL or a non-empty field map. `completeness_note` on `GET /matrix` states this.

Turn the seed off with `NEXUS_PERMISSION_SEED_TENANT_MEMBER_GRANTS=false`. Permission-core ITs leave it unset so they still observe `no_grant`.

## Honest leftovers (not claimed)

This remainder does **not** close product-wide G2.

- Report UI / physical ledger dump / H5 permission surfaces are **not** on this engine.
- Isolation-only jobs with no P2 `TenantContext` still skip `AuthorizedSubmit` / list-detail tenant-only fallback (including studio-only form hosts that never bind P2). Form fill HTTP without P2 still stays tenant-only (`FormApiTest`).
- General cache (`CacheKeyGuard` / Redis) is still isolation-only; only IsolatedExport `cacheStatus` rides the export Task grant.
- Direct `IsolatedExport` / `JdbcTaskQueue` callers that skip `AuthorizedExport` / `TaskSubmitGuard` are not a platform-wide “every API” close.
- Isolation Query AST still cannot encode OR trees; OR DataScope stays on `PermissionQueryBinder`.
- G9 stale-snapshot / Redis-down evidence is the parallel package [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md). Sentinel/Cluster is still not claimed.
- Full simulation / governance admin (time-bound, IP, ABAC DSL) is out of scope.
- **AuditPort is unchanged.** Isolation reject-before-sink (`P1.M02.019` / `P1.M02.016`) stays; this slice only adds authorization deny (`P2.PERM.*`).
- G18 stays closed.

## Evidence

```bash
mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2SameEngineTest,G2ExportJobTaskEngineTest,G2AuthorizedExportTest,G2AuthorizedSubmitTest,G2RoleMatrixApiTest,G2SurfaceAgreementTest,PermissionDataScopeHookTest,FormSurfaceGuardTest,FormStudioPermissionApiTest,FormSubmissionWriteApiTest,G2SubmitStartFormWriteIT,FieldPolicyProjectorTest,PermissionApiTest,TenantMemberGrantSeedApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Form studio recorded **2026-09-22T16:43:49Z** (OpenJDK 21.0.10 / Maven 3.8.7 / Boot 4.1.1, no Docker): permission-core targeted **7** run / **0** fail; p3-studio targeted **18** run / **0** fail (`FormStudioPermissionApiTest` 3, `FormSurfaceGuardTest` 5, `G2SurfaceAgreementTest` 5, `FormApiTest` 1, hook 4). Isolation-only `FormPublishFailureApiTest` **2/2** at **2026-09-22T16:44:11Z**.

Export / Job / Task remainder on this branch (Boot **4.1.1** / OpenJDK **21.0.10** / Maven **3.8.7** / no Docker):

```
./scripts/g2-export-job-task.sh
# BUILD SUCCESS  2026-09-22T16:44:16Z
# permission-core: 14 tests (G2SameEngine 2, G2ExportJobTaskEngine 2, G2AuthorizedExport 2,
#   G2AuthorizedSubmit 3, FieldPolicy 1, G2RoleMatrixApi 1, PermissionApi 2, TenantMemberGrantSeed 1)
# p3-studio: 8 tests (PermissionDataScopeHook 4, G2SurfaceAgreement 4)
```

Form fill / POST submissions remainder: [g2-form-submission-write.md](./g2-form-submission-write.md). Path submit-start dual-check: [g2-submit-start-form-write.md](./g2-submit-start-form-write.md) (`G2SubmitStartFormWriteIT` **3/3** at **2026-09-22T17:38:56Z**; `G18BusinessPathIT` **4/4** at **2026-09-22T17:39:09Z**). See `nexus-permission-core/EVIDENCE.md`. This is **not** a product-wide G2 close. G18 stays closed.
