# G6 leftover — RecoveryPort dry-run / non-product-HA remainder honesty beyond #186/#135/#164

S0+S1 remainder after the #186 dry-run / non-product-HA honesty leftover ([g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md)), the #164 still-dry-run honesty leftover ([g6-recovery-port-honesty.md](./g6-recovery-port-honesty.md)), and the #135 dry-run contract ([g6-recovery-port-dry-run.md](./g6-recovery-port-dry-run.md)). Rebased onto `origin/main` `bd00ca3` (Merge #224 S0+S1 claim-list tip-sync after #223). **G18 stays closed.** This leftover does **not** redo #135. It does **not** redo #164. It does **not** redo #186. It does **not** change `DryRunRecoveryPort`, `AuditPort.append` / `appendNewTransaction` same-TX rules, or ops restore scripts. It does **not** mutate a product database. It does **not** touch `haopan_platform`. It does **not** turn `RecoveryPort` into an executing restore. It does **not** claim a product RecoveryPort close, product G6 close, minute-level RPO, or product HA. P0/M0 are **not** redone. `g6_close=NOT_CLAIMED`. `recovery_port_close=NOT_CLAIMED`. `ha_close=NOT_CLAIMED`.

#186 already inventories the dry-run contract as always-green **PASS** and minute-level RPO / product HA / executing restore as honest **SKIP**. That #186 dry-run PASS is **not** product HA, **not** minute-level RPO, and **not** a live restore. Dedicated-cluster ops drills and `--version` probes are **not** product HA. The leftover here is the **honesty gate beyond those three**: the #186 dry-run contract stays **PASS** and is explicitly **not** live restore (`dry_run_is_not_live_restore=true`). Ops dedicated-cluster drill is **not** product HA (`ops_drill_is_not_product_ha=true`) and **not** a live product restore (`ops_drill_is_not_live_restore=true`). Minute-level RPO and live restore are **PASS** only when those walks were actually observed. Missing WAL / replica / live restore is honest **SKIP**. It never fabricates `PASS`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `g6-recovery-port-dry-run` (#135), `g6-recovery-port-honesty` (#164), `g6-recovery-port-dry-run-honesty` (#186), `s0-secret-rotate-getenv-remainder-honesty` (#223), `g4-pkce-spa-verifier-remainder-honesty` (#222), `g17-separation-leftover-honesty` (#216), `g15-wx-dt-native-bridge-remainder` (#220), `g1-queryast-export-honesty` (#215), `g17-gm-tls-nginx-handshake-honesty` (#210), `g5-publishhook-ddl-honesty` (#217), `g9-sentinel-cluster-remainder-honesty` (#209), `g2-ops-pg-dump-honesty` (#188), `g17-mfa-recovery-vue-otp-honesty` (#205), `g9-redis-down-invalidate-remainder` (#197), `g2-h5-every-page-form-write-honesty` (#204), `g4-export-wizard-deny-honesty` (#203), `g10-leftover-honesty` (#202), `g5-mid-publish-failure-honesty` (#196), `g3-outbox-consume-crash-honesty` (#195), `g1-queryast-boundsql-honesty` (#191), `g15-wx-dt-native-bridge-honesty` (#198), `s0-secret-rotate-without-restart-honesty` (#200), `linux-arm-profile-leftover-honesty` (#193), `g7-query-expense-travel-honesty` (#192), `g4-p1-staging-audit-events-honesty` (#190), `s0s1-live-redis-minio-isolation-honesty` (#185), `g17-separation-honesty` (#187), `g4-hot-purge-90d-honesty` (#183), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g4-pkce-spa-verifier-honesty` (#177), `s0-secret-getenv-snapshot-honesty` (#180), `g7-withdraw-browser-honesty` (#175), `g9-redis-down-invalidate-honesty` (#174), `g10-file-reconcile-honesty` (#169), `g5-mid-publish-failure` (#145), `g5-browser-failed-publish` (#171), `g17-gm-tls-nginx-handshake` (#160), and `g17-gm-tls-nginx-dual-stack` (#129). Keep this file.

## What this leftover covers

| Path | Behavior |
|---|---|
| #186 dry-run / non-product-HA honesty | Already on `main`. Dry-run contract stays **PASS**. Do **not** redo. That PASS is **not** product HA / minute-level RPO / live restore. |
| #164 still-dry-run honesty | Already on `main`. Do **not** redo. |
| #135 dry-run contract | Already on `main`. `executed=false`, `profileAccepted=false`. Do **not** redo Zonky mutation ITs. |
| Dry-run vs live restore | Always-green `dry_run_186_contract=PASS` **and** `dry_run_is_not_live_restore=true`. `#186` dry-run is **not** a live restore. |
| Ops drill vs product HA | `ops_drill_is_not_product_ha=true` and `ops_drill_is_not_live_restore=true`. Dedicated-cluster ops drill is **not** product HA. |
| Honest minute-level RPO SKIP | `minute_level_rpo=SKIP` unless a WAL archive / streaming replica / delivery-grade checklist walk was observed. |
| Honest product HA SKIP | `product_ha=SKIP` unless failover / standby promotion / multi-AZ was observed. |
| Honest live restore SKIP | `live_restore=SKIP` unless a live product restore was observed. Ops dedicated-cluster drill / `--version` probe is **not** live restore. |
| Mutation contract | `haopan_platform_touched=false`, `product_db_mutated=false`, `product_5432_targeted=false`. |
| Honesty gate | `--verify-report` rejects fake RPO/HA/live-restore PASS, rejects treating #186 dry-run or ops drill as product HA / live restore, rejects `g6_close=CLOSED` / `recovery_port_close=CLOSED` / `ha_close=CLOSED`, rejects #135/#164/#186 redo flags, and requires sibling leftover rows kept (including #186, #210, #215, #216, #217, #220, #222, and #223). |

No Docker. No live `127.0.0.1:5432` required. Do **not** run `./scripts/g6-recovery-port-dry-run.sh`, `./scripts/g6-recovery-port-honesty.sh`, or `./scripts/g6-recovery-port-dry-run-honesty.sh` as this leftover.

## How to run

```bash
./scripts/g6-recovery-port-dry-run-remainder-honesty.sh
./scripts/g6-recovery-port-dry-run-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g6-recovery-port-dry-run-remainder-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g6-recovery-port-dry-run-remainder-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-minute-rpo-pass.env fail
./scripts/g6-recovery-port-dry-run-remainder-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-product-ha-pass.env fail
./scripts/g6-recovery-port-dry-run-remainder-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-live-restore-pass.env fail
./scripts/g6-recovery-port-dry-run-remainder-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-dry-run-as-ha.env fail
./scripts/g6-recovery-port-dry-run-remainder-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-g6-close.env fail
./scripts/g6-recovery-port-dry-run-remainder-honesty.sh --verify-report \
  scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-redone.env fail
```

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g6-recovery-port-dry-run.sh
# ./scripts/g6-recovery-port-honesty.sh
# ./scripts/g6-recovery-port-dry-run-honesty.sh
```

Java **21** / Boot **4.1.1**. No Docker. Product `:5432` / `haopan_platform` are **not** required and must not be targeted.

See [engineering/g6-recovery-port-dry-run-remainder-honesty-EVIDENCE.md](./engineering/g6-recovery-port-dry-run-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g6-recovery-port-dry-run-remainder-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G6 / `RecoveryPort` close | Explicitly `g6_close=NOT_CLAIMED`, `recovery_port_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Product HA / delivery-grade HA | Explicitly `ha_close=NOT_CLAIMED`. `#186` dry-run PASS and ops drills are not product HA. |
| #186 dry-run as live restore | `dry_run_is_not_live_restore=true`. `--version` probe is not a restore. |
| Ops dedicated-cluster drill as product HA / live restore | `ops_drill_is_not_product_ha=true`, `ops_drill_is_not_live_restore=true`. |
| Minute-level RPO | Honest SKIP unless WAL / replica / delivery-grade checklist was observed. |
| Live restore | Honest SKIP unless a live product restore was observed. This leftover does not run one. |
| Redo of #135 / #164 / #186 | `redone_135=false`, `redone_164=false`, `redone_186=false`. |
| `haopan_platform` / product `:5432` | Forbidden. Not touched here. |
| V1.1 product single-tenant restore | Tenant export/import stays migration-only. |
| G17 / target Linux/ARM Profile | Not this package. Sibling leftover #210 stays beside this file. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- Minute-level RPO against a delivery-grade deployment checklist
- Product HA / delivery-grade HA / standby / WAL shipping
- Live product restore (not a dedicated-cluster ops drill, not a `--version` probe)
- Changing `RecoveryPort` from dry-run to execute
- Pointing restore at `haopan_platform` or `127.0.0.1:5432`
- GaussDB, AIStor enterprise, classified / 密码机
- Redo of #135 / #164 / #186
