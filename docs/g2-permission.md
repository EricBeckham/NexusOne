# G2 permission — role matrix, DataScope, field policy

S0+S1 PostgreSQL release work on M0-05 `PermissionEngine` / `PolicySnapshot`. **G18 stays closed.** This is not a redo of P0/M0 packages and does not fork TenantContext or rewrite AuditPort.

## What closed on this slice

List, detail, and flow interpret the **same** `PermissionService.decide` `Decision` through `SurfaceGuard`:

| Surface | Entry | `no_grant` / `explicit_deny` / `tenant_boundary` |
|---|---|---|
| UI / API evaluate | `POST /api/v1/permission/check` | returns `{allowed, reason, policy_version, fields, engine}` |
| Ledger list / detail | `PermissionDataScopeHook.bind` → `SurfaceGuard.require` | 403 (`P2.PERM.0003` for `no_grant`) |
| Ledger write | same hook `require("ledger","write")` + `FieldPolicyProjector.assertWritable` | 403 |
| Flow start / approve / reject / withdraw | `SequentialApprovalService.guard` → `SurfaceGuard.require` | 403 (`P3.FLOW.0004`, same reason string) |

DataScope (SELF / DEPT / DEPT_AND_CHILDREN / SPECIFIED / ALL) still compiles in `DataScopeCompiler` and binds with `PermissionQueryBinder` so list / detail / total share `tenant_id = ? AND (scope)`. FieldPolicy drops hidden fields on read and rejects non-editable fields on write.

Role matrix is a first-class read model, not the demo seed:

- `GET /api/v1/permission/roles|assignments|grants|matrix`
- `POST /api/v1/permission/matrix/example` installs `g2_self_reader` (SELF + salary hidden), `g2_dept_manager` (DEPT_AND_CHILDREN + salary masked / not editable), `g2_approver` (process start/approve/reject). **Does not assign to the current person.**
- Vue `/p2/permissions/{roles|datascope|fields}` reads that matrix. Ledger detail shows the same `/check` decision.

## `tenant_member` seed is not permission-complete

Product default `nexus.permission.seed-tenant-member-grants=true` still exists so the closed G18 leave walk keeps working. That role is **ALL-scope, empty field map, auto-assigned to the current person**. G2 treats it as demo bootstrap.

A tenant is **differentiated** (matrix `differentiated_policy=true`) only when at least one grant has a DataScope other than ALL or a non-empty field map. `completeness_note` on `GET /matrix` states this.

Turn the seed off with `NEXUS_PERMISSION_SEED_TENANT_MEMBER_GRANTS=false`. Permission-core ITs leave it unset so they still observe `no_grant`.

## Honest leftovers (not claimed)

- Report / export / H5 / cache / Job / Task are **not** on this engine (product-wide G2 stays open).
- Form definition list/studio does not call `form.read` / `form.write`.
- Isolation-only jobs with no P2 `TenantContext` still use tenant-only list/detail.
- Isolation Query AST still cannot encode OR trees; OR DataScope stays on `PermissionQueryBinder`.
- G9 stale-snapshot / Redis failover is unchanged from M0-05.
- Full simulation / governance admin (time-bound, IP, ABAC DSL) is out of scope.

## Evidence

```bash
mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2SameEngineTest,G2RoleMatrixApiTest,G2SurfaceAgreementTest,PermissionDataScopeHookTest,FieldPolicyProjectorTest,PermissionApiTest,TenantMemberGrantSeedApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Recorded **2026-09-22T15:29:11Z** (OpenJDK 21.0.10 / Maven 3.8.7 / Boot 4.1.1): permission-core **32** run / **0** fail / **4** skip (no local PG). Targeted G2 + hook suite **BUILD SUCCESS** at **2026-09-22T15:28:46Z** (14 tests). See `nexus-permission-core/EVIDENCE.md`.
