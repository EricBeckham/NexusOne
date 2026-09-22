# G2 leftover: ops-level `pg_dump` honesty evidence

Recorded **pending-this-run** on this agent VM after rebase onto `origin/main` `8e5cdc3` (Merge #152). **Not** a product G2 close. **Not** a redo of #137 physical dump SurfaceGuard, #144 `tenant_only`/catalog, or #150 todo HTTP. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g2-ops-pg-dump.md](../g2-ops-pg-dump.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x / 3.9.x** | filled after the test run |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | not required | static gate does not start PG |
| Runtime | local `mvn test` | No Docker / Compose |

## Commands

```bash
./scripts/g2-ops-pg-dump.sh
```

## Results (agent VM)

Filled after the contract suite on this VM.

| Check | Result | Notes |
|---|---|---|
| `OpsPgDumpHonestyGateTest` | pending | fixture `ProcessBuilder("pg_dump")` fails; product `src/main` + ops scripts pass |
| `G2OpsPgDumpHonestyTest` | pending | bootstrap product-tree scan empty |
| `OpsPgDumpHonestyArchTest` | pending | dump/report/surface packages do not construct `ProcessBuilder`; fixture fails the rule |
| `DryRunRecoveryPortContractTest` | pending | `--version` probe only; unchanged G6 leftover |
| Product dump surface source | pending | `DumpController` uses `AuthorizedExport.writeProjected`; no `ProcessBuilder` |
| Product G2 close / H5 every page | **NOT_CLAIMED** | leftovers stay open |
| G18 / AuditPort / PermissionEngine | unchanged | not reopened / not redesigned |

## Honest leftovers

- H5 every page
- Physical dump HTTP (#137) — sibling leftover, not rewritten
- Isolation-only `tenant_only` / catalog (#144)
- Todo list HTTP (#150)
- G6 RecoveryPort still dry-run
- GaussDB / live Redis / live MinIO
