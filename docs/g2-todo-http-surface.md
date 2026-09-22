# G2 leftover — todo list HTTP on PermissionEngine

S0+S1 leftover from [g2-permission.md](./g2-permission.md) and [g2-h5-permission.md](./g2-h5-permission.md): H5 Vue already gates `/m/p3/todos` via `POST /api/v1/permission/check`, but `GET /api/v1/p3/todos` (and aliases) stayed a tenant-person filter. This package wires that leftover HTTP through the **same** `PermissionService.decide` `Decision` as H5 todo (`SurfaceGuard` / `process.task` / `read`). **This is not a product-wide G2 close.** Desktop `/p3/todos` Vue is unchanged. **G18 stays closed.** AuditPort is unchanged.

Reuse only: M0-05 `PermissionEngine` and the existing H5 todo cell. Isolation-only `tenant_only` / catalog honesty is already on `main` via #144 ([g2-tenant-only-catalog.md](./g2-tenant-only-catalog.md)) — this leftover **does not redo it**. Physical dump is the sibling leftover [g2-physical-dump-surface.md](./g2-physical-dump-surface.md). Java 21 / Boot **4.1.1**. No Docker.

## What this package adds

| Surface | Entry | Cell | Denied (`no_grant` / `explicit_deny` / `tenant_boundary`) |
|---|---|---|---|
| Todo pending / alias | `GET /api/v1/todos/pending`, `GET /api/v1/p3/todos` | `process.task` / `read` | 403 `P2.PERM.0003` / `0002` / `0001` **before** `TodoQueryService` |
| Todo done | `GET /api/v1/todos/done` | same cell | same 403 **before** done query |
| Todo initiated | `GET /api/v1/todos/initiated` | same cell | same 403 **before** initiated query |
| Todo detail | `GET /api/v1/todos/{todoId}` | same cell | same 403 **before** find |

Vue H5 still calls `perm.check` first. In-process twin is `TodoSurfaceGuard` (same `IsolationOnlyDecision` skip as Form / H5 / catalog when no P2 `TenantContext`). Issued P2 context cannot emit `tenant_only`. Controllers without a `TodoSurfaceGuard` bean (no `PermissionModule`) stay the FormController-shaped skip so isolation ITs keep working.

`g2_approver` can read todos. `g2_self_reader` cannot. Demo `tenant_member` seed still includes `process.task` / `read` so the closed G18 leave walk can open the list. The seed is still ALL-scope bootstrap, not G2-complete.

Approve / reject / withdraw stay `SequentialApprovalService.guard` (`P3.FLOW.0004` + reason string). This leftover does not redo #116 H5 `/check` or flow writes.

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G2 | Open. H5 every page and `pg_dump` ops stay off this close. |
| Isolation-only `tenant_only` / catalog HTTP | Closed on main by #144. This leftover reuses `IsolationOnlyDecision`. It does not rewrite catalog controllers. |
| Physical dump / raw export | Sibling leftover on main (#137). |
| H5 `/m/*` Vue `/check` | Closed on main by #116. This leftover does not redo mobile pages. |
| Desktop `/p3/todos` Vue | Unchanged. HTTP 403 is the evidence. |
| Approve / reject / withdraw HTTP | Already `SequentialApprovalService.guard`. Not this leftover. |
| AuditPort same-TX / hash-chain rewrite | Unchanged |
| G18 reopen | No |

## Evidence

```bash
./scripts/g2-todo-http-surface.sh
```

Equivalent:

```bash
mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2TodoSameEngineTest,TodoSurfaceGuardTest,G2TodoSurfaceAgreementTest,TodoSurfaceApiTest,SurfaceGuardBypassGateTest,G2H5SameEngineTest,G2SameEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

See [engineering/g2-todo-http-surface-EVIDENCE.md](./engineering/g2-todo-http-surface-EVIDENCE.md). Isolation-only hosts without P2 stay tenant-only. Desktop P3 todo Vue is unchanged. No live JAR + issued-context browser walk on this VM.
