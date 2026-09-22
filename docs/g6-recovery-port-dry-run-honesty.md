# G6 leftover — RecoveryPort still dry-run / not product HA honesty beyond #135/#164

S0+S1 remainder after the #135 dry-run contract ([g6-recovery-port-dry-run.md](./g6-recovery-port-dry-run.md)) and the #164 still-dry-run honesty leftover ([g6-recovery-port-honesty.md](./g6-recovery-port-honesty.md)). Rebased onto `origin/main` `f59f879` (after #185 live Redis+MinIO isolation honesty; includes #189/#187). **G18 stays closed.** This leftover does **not** redo #135. It does **not** redo #164. It does **not** change `DryRunRecoveryPort`, `AuditPort.append` / `appendNewTransaction` same-TX rules, or ops restore scripts. It does **not** mutate a product database. It does **not** touch `haopan_platform`. It does **not** turn `RecoveryPort` into an executing restore. It does **not** claim a product RecoveryPort close, product G6 close, minute-level RPO, or product HA. P0/M0 are **not** redone.

#135 locked the product bean as `DryRunRecoveryPort` (`pg_dump --version` probe only). #164 documented that that dry-run is **not** product HA / minute-level RPO / delivery-grade HA. The leftover here is the **honesty gate**: dry-run contract stays always-green **PASS**. Minute-level RPO, product HA, and executing restore are **PASS** only when those walks were actually observed. Missing WAL / standby / restore is honest **SKIP**. It never fabricates `PASS`.

Keep both leftover rows in [docs/README.md](./README.md): `g6-recovery-port-dry-run` (#135) and `g6-recovery-port-honesty` (#164). Keep ALL sibling leftover rows (including `s0s1-live-redis-minio-isolation-honesty` / `g17-separation-honesty` / `g4-hot-purge-90d-honesty` / `g1-fieldpolicy-mask-second-auth-honesty` / `g10-identity-drift-honesty` / `g15-native-bridge-honesty` / `g4-pkce-spa-verifier-honesty` / `s0-secret-getenv-snapshot-honesty`). This file is the honesty leftover beside them. Follow-on remainder honesty leftover (does **not** redo this leftover): [g6-recovery-port-dry-run-remainder-honesty.md](./g6-recovery-port-dry-run-remainder-honesty.md).

## What this leftover covers

| Path | Behavior |
|---|---|
| #135 dry-run contract | Already on `main`. `executed=false`, `profileAccepted=false`, status `DRY_RUN` / `TOOL_MISSING`. Do **not** redo Zonky mutation ITs. |
| #164 still-dry-run honesty | Already on `main`. Do **not** redo. |
| Dry-run contract | Always-green `dry_run_contract=PASS`. `#135` dry-run is **not** product HA (`dry_run_is_not_product_ha=true`) and **not** minute-level RPO. |
| Honest minute-level RPO SKIP | `minute_level_rpo=SKIP` unless a WAL archive / streaming replica / delivery-grade checklist walk was observed. |
| Honest product HA SKIP | `product_ha=SKIP` unless failover / standby promotion / multi-AZ was observed. |
| Honest executing-restore SKIP | `executing_restore=SKIP` unless dump/restore actually ran. `DryRunRecoveryPort` still only probes `--version`. |
| Mutation contract | `haopan_platform_touched=false`, `product_db_mutated=false`, `product_5432_targeted=false`. |
| Honesty gate | `--verify-report` rejects fake RPO/HA PASS, rejects `g6_close=CLOSED` / `recovery_port_close=CLOSED`, rejects #135/#164 redo flags, and requires both leftover rows kept. |

No Docker. No live `127.0.0.1:5432` required. Do **not** run `./scripts/g6-recovery-port-dry-run.sh` or `./scripts/g6-recovery-port-honesty.sh` as this leftover.

## How to run

```bash
./scripts/g6-recovery-port-dry-run-honesty.sh
./scripts/g6-recovery-port-dry-run-honesty.sh --verify-report <out>/report.env
./scripts/g6-recovery-port-dry-run-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g6-recovery-port-dry-run-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-honesty/fixtures/fake-minute-rpo-pass.env
./scripts/g6-recovery-port-dry-run-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-honesty/fixtures/fake-product-ha-pass.env
./scripts/g6-recovery-port-dry-run-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-honesty/fixtures/fake-g6-close.env
```

Always-green Java (does **not** rerun #135 Zonky ITs or #164 suite):

```bash
mvn -pl nexus-audit-core -am test \
  -Dtest=G6RecoveryPortDryRunHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The existing leftover scripts stay as reproduction commands:

```bash
# do not run for this leftover
# ./scripts/g6-recovery-port-dry-run.sh
# ./scripts/g6-recovery-port-honesty.sh
```

Java **21** / Boot **4.1.1**. No Docker. Product `:5432` / `haopan_platform` are **not** required and must not be targeted.

See [engineering/g6-recovery-port-dry-run-honesty-EVIDENCE.md](./engineering/g6-recovery-port-dry-run-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g6-recovery-port-dry-run-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G6 / `RecoveryPort` close | Explicitly `g6_close=NOT_CLAIMED`, `recovery_port_close=NOT_CLAIMED`. |
| Minute-level RPO | Honest SKIP unless WAL / replica / delivery-grade checklist was observed. |
| Product HA / delivery-grade HA | Honest SKIP unless failover / standby / multi-AZ was observed. |
| Executing dump/restore on the product bean | `DryRunRecoveryPort` still only probes `--version`. |
| Redo of #135 / #164 | Mutation / Zonky / thin honesty ITs stay on those leftovers. |
| `haopan_platform` / product `:5432` | Forbidden. Not touched here. |
| V1.1 product single-tenant restore | Tenant export/import stays migration-only. |
| G17 / target Linux/ARM Profile | Not this package. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- Minute-level RPO against a delivery-grade deployment checklist
- Product HA / delivery-grade HA / standby / WAL shipping
- Changing `RecoveryPort` from dry-run to execute
- Pointing restore at `haopan_platform` or `127.0.0.1:5432`
- GaussDB, AIStor enterprise, classified / 密码机
