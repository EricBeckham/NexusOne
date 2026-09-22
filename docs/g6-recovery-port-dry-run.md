# G6 leftover — RecoveryPort honesty / dry-run contract

S0+S1 remainder after the dual-tenant whole-DB + MinIO drill ([g6-recovery-drill-EVIDENCE.md](./engineering/g6-recovery-drill-EVIDENCE.md)) and the single-tenant leftover ops drill ([g6-single-tenant-logical-restore-EVIDENCE.md](./engineering/g6-single-tenant-logical-restore-EVIDENCE.md)). **G18 stays closed.** This slice does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** turn `RecoveryPort` into an executing restore. It does **not** claim a product RecoveryPort close.

The product bean remains `DryRunRecoveryPort`. It describes runbooks and may probe `pg_dump --version`. It does **not** execute dump/restore, does **not** open JDBC, does **not** mutate a live / product-shaped database, and does **not** touch `haopan_platform`. This is **not** minute-level RPO and **not** product HA.

Sibling honesty leftover (do **not** redo this #135 contract): [g6-recovery-port-honesty.md](./g6-recovery-port-honesty.md). Follow-on honest PASS/SKIP leftover (does **not** redo this contract): [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md). Follow-on remainder honesty leftover (does **not** redo this contract): [g6-recovery-port-dry-run-remainder-honesty.md](./g6-recovery-port-dry-run-remainder-honesty.md). Keep both leftover rows in [docs/README.md](./README.md): `g6-recovery-port-dry-run` and `g6-recovery-port-honesty`. Keep ALL sibling leftover rows.

## What this slice delivers

| Piece | Behaviour |
|---|---|
| Honesty contract | `profileAccepted` stays false. `executed` stays false. Status is `DRY_RUN` or `TOOL_MISSING`. Notes refuse G6 / Profile acceptance. |
| Source contract | `DryRunRecoveryPort` may spawn only `ProcessBuilder(command, "--version")`. No JDBC, no `pg_restore`, no `haopan_platform`. |
| Mutation contract | Zonky PostgreSQL **15.x** witness + product-wired bootstrap stand-in stay byte-identical across `dryRunPostgres()` / `dryRunObjectStore()`. Dedicated cluster only — never port `5432`, never `haopan_platform`. |
| Product wiring | `nexus-bootstrap` `RecoveryPort` bean is still `DryRunRecoveryPort`. |

Ops restore remains the existing scripts / runbooks. Those paths already refuse `haopan_platform` and port `5432`. This leftover does not re-run those drills.

## How to run (no Docker)

```bash
./scripts/g6-recovery-port-dry-run.sh
```

Equivalent:

```bash
mvn -pl nexus-audit-core,nexus-bootstrap -am test \
  -Dtest=DryRunRecoveryPortTest,DryRunRecoveryPortContractTest,DryRunRecoveryPortDoesNotMutateDbIT,G6RecoveryPortDryRunIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Java **21** / Boot **4.1.1** / Zonky PostgreSQL **15.x**. No Docker. No live `127.0.0.1:5432` required.

See [engineering/g6-recovery-port-dry-run-EVIDENCE.md](./engineering/g6-recovery-port-dry-run-EVIDENCE.md).

## What this does not claim

| Leftover | Why |
|---|---|
| Product `RecoveryPort` close | Still dry-run. This package only locks the contract. |
| Minute-level RPO | No WAL archive, no streaming replica, no delivery-grade RPO checklist. |
| Product HA | No failover, no standby promotion, no multi-AZ. |
| Executing dump/restore on the product bean | `DryRunRecoveryPort` still only probes `--version`. Real restore stays ops scripts. |
| `haopan_platform` / product `:5432` | Forbidden. Tests use a dedicated Zonky port. |
| V1.1 product single-tenant restore | Tenant export/import stays migration-only. Ops leftover is [g6-single-tenant-logical-restore-EVIDENCE.md](./engineering/g6-single-tenant-logical-restore-EVIDENCE.md). |
| G17 / target Linux/ARM Profile | Not this package. |
| G18 reopen | Closed; not walked again. |

## Honest leftovers (not claimed)

- Minute-level RPO against a delivery-grade deployment checklist
- Product HA / standby / WAL shipping
- Changing `RecoveryPort` from dry-run to execute
- Pointing restore at `haopan_platform` or `127.0.0.1:5432`
- GaussDB, AIStor enterprise, classified / 密码机
