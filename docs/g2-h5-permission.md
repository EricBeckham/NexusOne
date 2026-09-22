# G2 leftover — H5 `/m/*` list / detail / form / todo on PermissionEngine

S0+S1 leftover from [g2-permission.md](./g2-permission.md): H5 permission surfaces were not on this engine. This package wires Vue `/m/*` list / detail / form / todo reads and writes through the **same** `PermissionService.decide` `Decision` as desktop (`SurfaceGuard` / `POST /api/v1/permission/check`). **This is not a product-wide G2 close.** Desktop `/p3` pages are unchanged. **G18 stays closed.** AuditPort is unchanged.

Reuse only: M0-05 `PermissionEngine` and the existing list / form / flow cells. G15 H5 routes stay under `web/src/mobile`. Java 21 / Boot **4.1.1**. No Docker.

## What this package adds

| H5 surface | Route | Cell | Denied (`no_grant` / `explicit_deny` / `tenant_boundary`) |
|---|---|---|---|
| List / query / record detail | `/m/p3/lists`, `/m/p3/lists/:id`, `/m/p3/lists/:id/records/:recordId`, `/m/p3/query` | `ledger` / `read` | 403 `P2.PERM.0003` / `0002` / `0001` **before** P3 list/detail HTTP |
| Form list / fill load | `/m/p3/forms`, `/m/p3/forms/:id` | `form` / `read` | 403 (same codes) **before** `GET /api/v1/p3/forms` |
| Form fill write | `/m/p3/forms/:id` submit | `form` / `write` | 403 (same codes) **before** `POST /api/v1/p3/path/submit-start` |
| Todo list / detail | `/m/p3/todos`, `/m/p3/todos/:id` | `process.task` / `read` | 403 (same codes) **before** todo HTTP |
| Todo approve / reject | inbox actions | `process.task` / `approve` or `reject` | 403 (same codes) **before** path approve/reject |
| Todo withdraw | 我发起 | `process.instance` / `withdraw` | 403 (same codes) **before** path withdraw |

Vue calls `perm.check` → `POST /api/v1/permission/check` and interprets `!allowed` the same way as Java `SurfaceGuard.requireAllowed`. In-process twin is `H5SurfaceGuard` (same isolation-only `tenant_only` skip as `FormSurfaceGuard` when no P2 `TenantContext`).

`g2_approver` example matrix now includes `process.task` / `read`. Demo `tenant_member` seed also gets that cell so the closed G18 leave walk can still open H5 todos. The seed is still ALL-scope bootstrap, not G2-complete.

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G2 | Open. Report UI / physical ledger dump stay off this engine. |
| Desktop `/p3` pages | Unchanged. Do not import the mobile helper. |
| Path `submit-start` as `form.write` | Closed on main by #112 ([g2-submit-start-form-write.md](./g2-submit-start-form-write.md)). H5 fill still **checks** `form.write` then calls that dual-check walk. |
| Todo list HTTP (`GET /api/v1/p3/todos`) | Still tenant-person filter only. H5 UI gates via `/check`. Flow writes stay `SequentialApprovalService.guard` (`P3.FLOW.0004` + reason string). |
| Ledger definition list HTTP (`GET /api/v1/p3/ledgers`) | Still unguarded at the controller. Record list/detail remain `PermissionDataScopeHook`. H5 UI gates definition list via `/check`. |
| Files / notify / host / login | Out of this leftover. |
| APP WebView / 小程序 / PKCE | G15 leftovers. |
| AuditPort same-TX / hash-chain rewrite | Unchanged |
| G18 reopen | No |

## Evidence

```bash
cd web
npm test -- src/mobile/permission/surfaceGuard.test.js
npm run assert:h5-permission
npm run assert:h5-runtime

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2H5SameEngineTest,H5SurfaceGuardTest,G2H5SurfaceAgreementTest,G2SameEngineTest,G2SurfaceAgreementTest,FormSurfaceGuardTest,TenantMemberGrantSeederTest,TenantMemberGrantSeedApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false

./scripts/g2-h5-permission.sh
```

Recorded **2026-09-22T17:39:26Z** (OpenJDK **21.0.10** / Maven **3.8.7** / Boot **4.1.1** / Node **22.14.0**, no Docker):

```
npm test -- src/mobile/permission/surfaceGuard.test.js   # 6/6
npm run assert:h5-permission                             # PASS
npm run assert:h5-runtime                                # PASS
npm test                                                 # 28/28 (4 files)
mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2H5SameEngineTest,H5SurfaceGuardTest,G2H5SurfaceAgreementTest,...
# BUILD SUCCESS  2026-09-22T17:39:26Z
# permission-core: 6 tests (G2H5SameEngine 2, G2SameEngine 2, TenantMemberGrantSeeder 1, TenantMemberGrantSeed 1)
# p3-studio: 21 tests (G2H5SurfaceAgreement 4, H5SurfaceGuard 5, G2SurfaceAgreement 6, FormSurfaceGuard 6)
```

`FormStudioPermissionApiTest` is **not** re-run here: its TestApp clashes with `FormSubmissionWriteApiTest$TestApp` when both sit on `one.nexus.p3.studio` component scan. That is a pre-existing studio-test leftover, not this H5 slice. Isolation-only form hosts stay tenant-only. Desktop P3 views still do not import the mobile shell or H5 permission helper. No live JAR + issued-context browser walk on this VM.
