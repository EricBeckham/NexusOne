# G2 leftover: todo list HTTP SurfaceGuard evidence

Recorded **pending-this-run** on this agent VM after rebase onto `origin/main` `f89e2f1` (Merge #149). **Not** a product G2 close. **Not** a catalog / `tenant_only` rewrite (#144). **Not** a physical dump close (#137). **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `PermissionEngine` was not redesigned.

See [docs/g2-todo-http-surface.md](../g2-todo-http-surface.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x** | filled after the test run |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | not required | contract tests do not start PG |
| Runtime | local `mvn test` | No Docker / Compose |

## Commands

```bash
./scripts/g2-todo-http-surface.sh
```

## Results (agent VM)

Filled after the contract suite on this VM.

| Check | Result | Notes |
|---|---|---|
| `G2TodoSameEngineTest` | pending | `process.task`/`read` shares H5 todo cells; `g2_self_reader` deny; `g2_approver` allow |
| `TodoSurfaceGuardTest` | pending | isolation leftover; `no_grant` / `explicit_deny` / `tenant_boundary`; issued P2 cannot emit `tenant_only` |
| `G2TodoSurfaceAgreementTest` | pending | todo HTTP + H5 `requireTodoRead` same engine |
| `TodoSurfaceApiTest` | pending | HTTP 403 **before** `TodoQueryService` pending/done/initiated/get |
| `SurfaceGuardBypassGateTest` | pending | `TodoController` no longer allowlisted; `TodoSurfaceGuard` is a guard token |
| Product G2 close / H5 every page / `pg_dump` | **NOT_CLAIMED** | leftovers stay open |
| G18 / AuditPort / PermissionEngine | unchanged | not reopened / not redesigned |

## Honest leftovers

- Isolation-only `tenant_only` / catalog whitelist (#144) — sibling leftover; reused, not rewritten
- Physical ledger dump (#137)
- H5 every page / desktop `/p3/todos` Vue
- Catalog HTTP without a `CatalogSurfaceGuard` bean (isolation host)
- `TenantOnlyDataScopeHook` no-engine leftover
- `pg_dump` ops
- GaussDB / live Redis / live MinIO
