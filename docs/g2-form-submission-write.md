# G2 remainder — form fill / POST submissions as `form.write`

S0+S1 leftover from [g2-permission.md](./g2-permission.md): form fill and `POST /api/v1/p3/forms/{id}/submissions` now interpret the **same** `PermissionService.decide` `Decision` through `FormSurfaceGuard` → `SurfaceGuard`. **This is not a product-wide G2 close.** Path `POST /api/v1/p3/path/submit-start` is the G18 runtime walk owner; the leftover dual-check (`form.write` + process start when `form_id` is present) is [g2-submit-start-form-write.md](./g2-submit-start-form-write.md). **G18 stays closed.** AuditPort same-TX is unchanged.

Reuse only: M0-05 `PermissionEngine` / `PermissionService.decide` and the existing `FormSurfaceGuard` (`form` / `write`). M0-05 and M0-07 are not redone.

## What this package adds

| Surface | Entry | Denied (`no_grant` / `explicit_deny` / `tenant_boundary`) |
|---|---|---|
| Form fill | `FormController.submit` → `FormSurfaceGuard.requireFill` → `form` / `write` | 403 (`P2.PERM.0003` / `0002` / `0001`) **before** `FormService.submit` |
| Submission list | `FormController.submissions` → `FormSurfaceGuard.requireRead` → `form` / `read` | 403 (same codes) |

`requireFill()` is `requireWrite()`. Isolation-only hosts with no P2 `TenantContext` still return `tenant_only` and skip this engine (same leftover as list/detail). Demo `tenant_member` seed may still run so the closed G18 leave walk keeps working; it is not treated as G2-complete.

Granted `form.write` (`g2_form_designer` / `g2_dept_manager` example cells, or any GRANT on `form`/`write`) can POST a submission. `g2_self_reader` can list submissions (`form.read`) and cannot fill.

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G2 (physical dump) | Open. Report query/export is [g2-report-surface-guard.md](./g2-report-surface-guard.md). Physical dump / raw export is [g2-physical-dump-surface.md](./g2-physical-dump-surface.md). H5 `/m/*` list/detail/form/todo is [g2-h5-permission.md](./g2-h5-permission.md) |
| Path `submit-start` as `form.write` only | Dual-check leftover: [g2-submit-start-form-write.md](./g2-submit-start-form-write.md). Process start stays required |
| FormService.submit used by `BusinessPathService` | Path `submitAndStart` now requires `form.write` when `form_id` is present; HTTP `/submissions` stays `requireFill` |
| Isolation-only `FormApiTest` (no P2 context) | Still tenant-only |
| AuditPort same-TX / hash-chain rewrite | Unchanged |
| G18 reopen | No |

## Evidence

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=FormSubmissionWriteApiTest,FormSurfaceGuardTest,G2SurfaceAgreementTest,FormStudioPermissionApiTest,FormApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Recorded on this agent VM after the change (OpenJDK / Maven / Boot versions filled from the test run). Isolation-only `FormApiTest` stays green so fill without P2 is still tenant-only.

Failing-before / passing-after is `FormSubmissionWriteApiTest`:

1. `no_grant` on POST `/submissions` → `403 P2.PERM.0003`; GET submissions empty.
2. GRANT+DENY on `form.write` → `403 P2.PERM.0002`; GET submissions empty.
3. Subject tenant ≠ resource tenant → `403 P2.PERM.0001`; GET submissions empty.
4. `g2_form_designer` POST → `201`; GET submissions length 1.
5. `g2_self_reader` GET submissions `200` and POST `403 P2.PERM.0003`.
