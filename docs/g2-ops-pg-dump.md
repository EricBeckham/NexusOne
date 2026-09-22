# G2 leftover — ops-level `pg_dump` honesty (out of SurfaceGuard)

S0+S1 leftover from [g2-permission.md](./g2-permission.md) and [g2-physical-dump-surface.md](./g2-physical-dump-surface.md): product physical dump HTTP is already on `SurfaceGuard` via #137 (`AuthorizedExport.writeProjected`). Ops-level `pg_dump` is **not** that surface. This package **documents and statically guards** that boundary. **This is not a product-wide G2 close.**

It does **not** redo #137 dump HTTP, #144 isolation-only `tenant_only` / catalog, or #150 todo HTTP. It does **not** put G6 runbooks through `SurfaceGuard`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. **G18 stays closed.**

Reuse only: the existing #137 dump path and the #135 `DryRunRecoveryPort` `--version` probe. Java 21 / Boot **4.1.1**. No Docker. Orthogonal to #152 HTTP export wizard, #148 Sentinel/Cluster honesty, #156 P1 staging audit-events, #151 native-bridge stub, and #147 SPA PKCE verifier.

## What this leftover covers

| Path | Goes through SurfaceGuard? | Guard |
|---|---|---|
| Product dump HTTP `POST /api/v1/p3/dumps/export` | **Yes** (#137) | `DumpSurfaceGuard` then `AuthorizedExport.writeProjected` |
| Product report / wizard export | **Yes** (#124 / #152) | `ReportSurfaceGuard` then `AuthorizedExport.submit` |
| Product Java `pg_dump` process | **Forbidden** except `--version` | `OpsPgDumpHonestyGate` rule `ops-pg-dump-in-product` |
| `DryRunRecoveryPort.dryRunPostgres()` | **No** (G6 leftover) | Probe `pg_dump --version` only. Does not dump. |
| Ops scripts / G6 runbooks | **No** | Documented out-of-path. Must not claim `SurfaceGuard` / `AuthorizedExport` |

Thin wrapper: `OpsPhysicalDumpCatalog`. ArchUnit note: `one.nexus.p3.dump` / `one.nexus.p3.report` / `one.nexus.permission.surface` must not construct `ProcessBuilder`.

An intentional fixture (`ProcessBuilder("pg_dump", "--format=custom")`) fails the gate. Current product `src/main` plus the documented ops scripts pass.

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G2 | Open. H5 every page stays off this close. |
| Physical dump HTTP (#137) | Sibling leftover on `main`. This leftover does not rewrite `DumpController`. |
| Isolation-only `tenant_only` / catalog (#144) | Sibling leftover. Not rewritten. |
| Todo list HTTP (#150) | Sibling leftover. Not rewritten. |
| G6 RecoveryPort / minute-level RPO | Still dry-run (#135). Ops `pg_dump` stays a runbook. |
| HTTP export wizard (#152) | G4 leftover. Reuses `AuthorizedExport.submit`. Not `pg_dump`. |
| AuditPort same-TX / hash-chain rewrite | Unchanged |
| G18 reopen | No |

## Evidence

```bash
./scripts/g2-ops-pg-dump.sh
```

Equivalent:

```bash
mvn -pl nexus-permission-core,nexus-bootstrap -am test \
  -Dtest=OpsPgDumpHonestyGateTest,G2OpsPgDumpHonestyTest,OpsPgDumpHonestyArchTest,DryRunRecoveryPortContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -f nexus-permission-core/pom.xml -q exec:java@ops-pg-dump-honesty \
  -Dexec.args="--repo ."
```

See [engineering/g2-ops-pg-dump-EVIDENCE.md](./engineering/g2-ops-pg-dump-EVIDENCE.md). No live `pg_dump` of a product database. No Docker.
