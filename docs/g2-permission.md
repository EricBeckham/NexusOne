# G2 permission — role matrix, DataScope, field policy

S0+S1 PostgreSQL release work on M0-05 `PermissionEngine` / `PolicySnapshot`. **G18 stays closed.** This is not a redo of P0/M0 packages and does not fork TenantContext or rewrite AuditPort.

## What closed on this slice

List, detail, flow, and form studio interpret the **same** `PermissionService.decide` `Decision` through `SurfaceGuard`:

| Surface | Entry | `no_grant` / `explicit_deny` / `tenant_boundary` |
|---|---|---|
| UI / API evaluate | `POST /api/v1/permission/check` | returns `{allowed, reason, policy_version, fields, engine}` |
| Ledger list / detail | `PermissionDataScopeHook.bind` → `SurfaceGuard.require` | 403 (`P2.PERM.0003` for `no_grant`) |
| Ledger write | same hook `require("ledger","write")` + `FieldPolicyProjector.assertWritable` | 403 |
| Flow start / approve / reject / withdraw | `SequentialApprovalService.guard` → `SurfaceGuard.require` | 403 (`P3.FLOW.0004`, same reason string) |
| Form definition list / detail | `FormSurfaceGuard.requireRead` → `form` / `read` | 403 (`P2.PERM.0003` / `0002` / `0001`) |
| Form create / update / validate / publish | `FormSurfaceGuard.requireWrite` → `form` / `write` | 403 (same codes) |

DataScope (SELF / DEPT / DEPT_AND_CHILDREN / SPECIFIED / ALL) still compiles in `DataScopeCompiler` and binds with `PermissionQueryBinder` so list / detail / total share `tenant_id = ? AND (scope)`. FieldPolicy drops hidden fields on read and rejects non-editable fields on write.

Role matrix is a first-class read model, not the demo seed:

- `GET /api/v1/permission/roles|assignments|grants|matrix`
- `POST /api/v1/permission/matrix/example` installs `g2_self_reader` (SELF + salary hidden, `form.read`), `g2_dept_manager` (DEPT_AND_CHILDREN + salary masked / not editable, `form.read`+`form.write`), `g2_approver` (process start/approve/reject), `g2_form_designer` (`form.read`+`form.write`). **Does not assign to the current person.**
- Vue `/p2/permissions/{roles|datascope|fields}` reads that matrix. Ledger detail shows the same `/check` decision.

## `tenant_member` seed is not permission-complete

Product default `nexus.permission.seed-tenant-member-grants=true` still exists so the closed G18 leave walk keeps working. That role is **ALL-scope, empty field map, auto-assigned to the current person**. G2 treats it as demo bootstrap.

A tenant is **differentiated** (matrix `differentiated_policy=true`) only when at least one grant has a DataScope other than ALL or a non-empty field map. `completeness_note` on `GET /matrix` states this.

Turn the seed off with `NEXUS_PERMISSION_SEED_TENANT_MEMBER_GRANTS=false`. Permission-core ITs leave it unset so they still observe `no_grant`.

## Honest leftovers (not claimed)

- Report / export / H5 / cache / Job / Task are **not** on this engine (product-wide G2 stays open).
- Form fill / `POST …/submissions` is still not claimed as `form.write` (path submit-start owns the runtime walk).
- Isolation-only jobs with no P2 `TenantContext` still use tenant-only list/detail (including studio-only form hosts that never bind P2).
- Isolation Query AST still cannot encode OR trees; OR DataScope stays on `PermissionQueryBinder`.
- G9 stale-snapshot / Redis-down evidence is the parallel package [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md). Sentinel/Cluster is still not claimed.
- Full simulation / governance admin (time-bound, IP, ABAC DSL) is out of scope.

## Evidence

```bash
mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2SameEngineTest,G2RoleMatrixApiTest,G2SurfaceAgreementTest,PermissionDataScopeHookTest,FormSurfaceGuardTest,FormStudioPermissionApiTest,FieldPolicyProjectorTest,PermissionApiTest,TenantMemberGrantSeedApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Form studio remainder recorded after this slice (OpenJDK 21 / Maven 3.8.7 / Boot 4.1.1). See `nexus-permission-core/EVIDENCE.md`. This is **not** a product-wide G2 close. G18 stays closed.
