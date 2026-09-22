# G2 leftover: isolation-only `tenant_only` / catalog whitelist honesty evidence

Recorded **pending-this-run** on this agent VM after rebase onto `origin/main` `6fce50d` (Merge #134). **Not** a product G2 close. **Not** a physical dump close (#137). **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `PermissionEngine` was not redesigned. Orthogonal to #136 guard-deny audit.

See [docs/g2-tenant-only-catalog.md](../g2-tenant-only-catalog.md).

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
./scripts/g2-tenant-only-catalog.sh
```

## Results (agent VM)

Filled after the contract suite on this VM.

| Check | Result | Notes |
|---|---|---|
| `IsolationOnlyDecisionTest` | pending | no P2 → `tenant_only`; issued P2 → `tenant_only_forbidden` / `P2.PERM.0003` |
| `G2CatalogSameEngineTest` | pending | catalog cells share desktop `ledger/read` + `ledger/write` |
| `CatalogSurfaceGuardTest` | pending | isolation leftover; `no_grant` / `explicit_deny` / `tenant_boundary` |
| `G2CatalogSurfaceAgreementTest` | pending | catalog + list/detail same engine; issued context cannot emit `tenant_only` |
| `CatalogSurfaceApiTest` | pending | HTTP 403 before application list/create sinks |
| `TenantOnlyDataScopeHookTest` | pending | no-engine leftover still allows with demo-bound P2; `IsolationOnlyDecision.allow()` fail-closes |
| `FormSurfaceGuardTest` / `H5SurfaceGuardTest` / `ReportSurfaceGuardTest` / `PermissionDataScopeHookTest` | pending | wrappers use `IsolationOnlyDecision`; #136 deny audit unchanged |
| `SurfaceGuardBypassGateTest` | pending | `IsolationOnlyDecision` is the `Decision(true)` constructor; product `src/main` empty |
| Product G2 close / physical dump / todo HTTP | **NOT_CLAIMED** | leftovers stay open |
| G18 / AuditPort / PermissionEngine | unchanged | not reopened / not redesigned |

## Honest leftovers

- Physical ledger dump (in-flight #137)
- Todo HTTP (`TodoController`) tenant-person filter
- `TenantOnlyDataScopeHook` no-engine leftover when demo headers bind P2 without `PermissionModule`
- Catalog HTTP without a `CatalogSurfaceGuard` bean (isolation host, FormController-shaped skip)
- UI / H5 every page not re-walked
- GaussDB / live Redis / live MinIO
