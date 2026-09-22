# G2 remainder — path submit-start dual-check (`form.write` + process start)

S0+S1 leftover from [g2-form-submission-write.md](./g2-form-submission-write.md) **Not claimed: Path submit-start as form.write**. HTTP `POST /api/v1/p3/path/submit-start` after a form fill now requires **both** `form.write` (when `form_id` is present) and `process.instance` / `start`. **This is not a product-wide G2 close.** Submit-start remains the G18 runtime walk owner. **G18 stays closed.** AuditPort same-TX is unchanged.

Reuse only: M0-05 `PermissionEngine` / `PermissionService.decide`, existing `FormSurfaceGuard.requireFill` (`form` / `write`), and existing `SequentialApprovalService.guard` (`process.instance` / `start`). M0-05 / M0-07 / M0-08 are not redone.

## What this package adds

| Surface | Entry | Denied (`no_grant` / `explicit_deny` / `tenant_boundary`) |
|---|---|---|
| Path submit-start with `form_id` | `BusinessPathService.submitAndStart` → `FormSurfaceGuard.requireFill` → `form` / `write` **then** `SequentialApprovalService.start` → `process.instance` / `start` | 403 (`P2.PERM.0003` / `0002` / `0001`) **before** `FormService.submit`, physical `l_*` insert, and Flowable start |

`requireFill()` is still `requireWrite()`. Isolation-only hosts with no `FormSurfaceGuard` / no P2 `TenantContext` stay tenant-only. Demo `tenant_member` seed may still run so the closed G18 leave walk keeps working; it is not treated as G2-complete.

A subject with only `process.instance` / `start` (`g2_approver`) cannot HTTP-submit a filled form. A subject with only `form.write` (`g2_form_designer`) still fails the existing process-start guard. Both cells are required when `form_id` is present.

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G2 (physical dump) | Open. Report query/export is [g2-report-surface-guard.md](./g2-report-surface-guard.md). Physical dump / raw export is [g2-physical-dump-surface.md](./g2-physical-dump-surface.md). H5 is [g2-h5-permission.md](./g2-h5-permission.md) |
| Submit-start **is** `form.write` only | Not claimed. Process start stays required. This is a dual-check |
| `FormService.submit` in-process callers without HTTP / path | Not wrapped unless they go through `BusinessPathService.submitAndStart` |
| Isolation-only `FormApiTest` (no P2 context) | Still tenant-only |
| AuditPort same-TX / hash-chain rewrite | Unchanged |
| G18 reopen | No. Leave seed path remains |

## Evidence

```bash
./scripts/g2-submit-start-form-write.sh
```

Equivalent:

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=G2SubmitStartFormWriteIT,G2SurfaceAgreementTest,FormSurfaceGuardTest,G18BusinessPathIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Recorded on this agent VM after rebase onto `main@440a504` (**#113**). `./scripts/g2-submit-start-form-write.sh` **BUILD SUCCESS** at **2026-09-22T17:43:30Z**. OpenJDK **21.0.10**, Maven **3.8.7**, Boot **4.1.1**, Zonky PostgreSQL **15.18**. No Docker.

| Suite | Result |
|---|---|
| `G2SubmitStartFormWriteIT` | **3/3** at **2026-09-22T17:43:00Z** |
| `G2SurfaceAgreementTest` | **7/7** (includes dual-check: approver start / designer fill) |
| `FormSurfaceGuardTest` | **6/6** |
| `G18BusinessPathIT` | **4/4** — leave seed path still works |

Failing-before / passing-after is `G2SubmitStartFormWriteIT`:

1. GRANT `process.instance`/`start` only → POST submit-start `403 P2.PERM.0003`; `form_submission` / `process_instance` / physical `l_*` stay 0.
2. `TenantMemberGrantSeeder.ensure()` (same cells as product seed) → POST submit-start `200` `IN_APPROVAL` / `RUNNING`.
3. GRANT start+`form.write` then DENY `form.write` → `403 P2.PERM.0002`; counts stay 0.
