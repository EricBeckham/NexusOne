# G2 leftover: H5 every-page SurfaceGuard coverage honesty evidence

Recorded **pending-this-run** on this agent VM after rebase onto `origin/main` `e66518b` (Merge #162; also #153 at `6487b87`, #159 at `3c6dea2`, #155 at `bff7796`). **Not** a product G2 close. **Not** a redo of #116 H5 `/check`, #150 todo HTTP, #144 `tenant_only`/catalog, or #137 physical dump. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g2-h5-every-page.md](../g2-h5-every-page.md). Sibling leftover [g2-ops-pg-dump.md](../g2-ops-pg-dump.md) stays.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x / 3.9.x** | filled after the test run |
| Spring Boot parent | **4.1.1** | product BOM |
| Node | **22.x** | web inventory + assert |
| PostgreSQL | not required | static gate does not start PG |
| Runtime | local `mvn test` + `npm test` | No Docker / Compose |

## Commands

```bash
./scripts/g2-h5-every-page.sh
```

## Results (agent VM)

Filled after the contract suite on this VM.

| Check | Result | Notes |
|---|---|---|
| `H5PageSurfaceHonestyGateTest` | pending | unclassified `/m/p3/reports` fails; fake `g2_close=CLOSED` fails; product router passes |
| `G2H5PageHonestyTest` | pending | bootstrap product-tree scan empty |
| `H5PageSurfaceHonestyArchTest` | pending | `H5SurfaceGuard` still depends on `SurfaceGuard`; OPEN pages stay |
| `h5PageInventory.test.js` | pending | covered #116 set; open home/files/notify/login/host |
| `assert:h5-every-page` | pending | router ↔ catalog; sibling `g2-ops-pg-dump` row kept |
| Product G2 close | **NOT_CLAIMED** | leftovers stay open |
| G18 / AuditPort / PermissionEngine | unchanged | not reopened / not redesigned |

## Honest leftovers

- Putting SurfaceGuard on `/m/p3/home` / files / notify / login / host / harness
- Ops `pg_dump` (#155) — sibling leftover row kept
- Physical dump HTTP (#137)
- Isolation-only `tenant_only` / catalog (#144)
- Todo list HTTP (#150)
- H5 `/check` list/detail/form/todo (#116) — sibling leftover, not rewritten
- GaussDB / live Redis / live MinIO
