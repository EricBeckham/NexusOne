# G6 leftover — RecoveryPort still dry-run honesty

S0+S1 remainder after the #135 dry-run contract ([g6-recovery-port-dry-run.md](./g6-recovery-port-dry-run.md)). **G18 stays closed.** This leftover does **not** redo #135. It does **not** change `DryRunRecoveryPort`, `AuditPort.append` / `appendNewTransaction` same-TX rules, or ops restore scripts. It does **not** turn `RecoveryPort` into an executing restore. It does **not** claim a product RecoveryPort close.

#135 locked the product bean as `DryRunRecoveryPort` (`pg_dump --version` probe only). This leftover is **honesty only**: that dry-run is **not** product HA, **not** minute-level RPO, and **not** delivery-grade HA.

Keep both leftover rows in [docs/README.md](./README.md): `g6-recovery-port-dry-run` and this file.

## What this leftover states

| Claim | Status |
|---|---|
| #135 dry-run contract | Already on `main`. Do not redo. |
| Product `RecoveryPort` close | **NOT_CLAIMED.** Bean stays dry-run. |
| Minute-level RPO | **NOT_CLAIMED.** No WAL archive, no streaming replica, no delivery-grade RPO checklist. |
| Product HA | **NOT_CLAIMED.** No failover, no standby promotion, no multi-AZ. |
| Delivery-grade HA | **NOT_CLAIMED.** Ops drills and `--version` probes are not a delivery HA close. |

Optional thin contract: `G6RecoveryPortHonestyTest` reads leftover docs + claim-list and reuses the public dry-run API (`executed=false`, `profileAccepted=false`). It does **not** rerun the #135 Zonky mutation ITs.

## How to run (no Docker)

```bash
./scripts/g6-recovery-port-honesty.sh
```

Equivalent:

```bash
mvn -pl nexus-audit-core -am test \
  -Dtest=G6RecoveryPortHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Java **21** / Boot **4.1.1**. No Docker. No live `127.0.0.1:5432` required. Do **not** run `./scripts/g6-recovery-port-dry-run.sh` as this leftover — that is the #135 contract.

See [engineering/g6-recovery-port-honesty-EVIDENCE.md](./engineering/g6-recovery-port-honesty-EVIDENCE.md).

## What this does not claim

| Leftover | Why |
|---|---|
| Product `RecoveryPort` close | Still dry-run after #135. |
| Minute-level RPO | No WAL / replica / delivery-grade checklist. |
| Product HA / delivery-grade HA | No failover, standby, or multi-AZ. |
| Redo of #135 | Mutation / Zonky / product-bean ITs stay on that leftover. |
| V1.1 product single-tenant restore | Tenant export/import stays migration-only. |
| `haopan_platform` / product `:5432` | Forbidden. Not touched here. |
| G17 / target Linux/ARM Profile | Not this package. |
| G18 reopen | Closed; not walked again. |

## Honest leftovers (not claimed)

- Changing `RecoveryPort` from dry-run to execute
- Minute-level RPO against a delivery-grade deployment checklist
- Product HA / delivery-grade HA / standby / WAL shipping
- Pointing restore at `haopan_platform` or `127.0.0.1:5432`
- GaussDB, AIStor enterprise, classified / 密码机
