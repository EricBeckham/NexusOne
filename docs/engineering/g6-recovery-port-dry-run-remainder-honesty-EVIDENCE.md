# G6 leftover: RecoveryPort dry-run / non-product-HA remainder honesty evidence

Recorded on this branch after rebase onto `origin/main` `bd00ca3` (Merge #224 S0+S1 claim-list tip-sync after #223). **Not** a product RecoveryPort close. **Not** a product G6 close. **Not** minute-level RPO. **Not** product HA. **Not** delivery-grade HA. **Not** a live restore. **Not** a redo of #135, #164, or #186. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `haopan_platform` was not touched. Product DB was not mutated.

See [docs/g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md). Keep **all sibling leftover rows** in [docs/README.md](../README.md), including [g6-recovery-port-dry-run.md](../g6-recovery-port-dry-run.md) (#135), [g6-recovery-port-honesty.md](../g6-recovery-port-honesty.md) (#164), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), and [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x / 3.9.x** | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | not required | leftover does not start PG and does not target `:5432` |
| Runtime | collect script + honesty fixtures | No Docker / Compose. No `haopan_platform` |

## Commands

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

Do **not** treat `./scripts/g6-recovery-port-dry-run.sh`, `./scripts/g6-recovery-port-honesty.sh`, or `./scripts/g6-recovery-port-dry-run-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g6-recovery-port-dry-run-remainder-honesty/`. A sanitized copy is [`g6-recovery-port-dry-run-remainder-honesty/agent-vm.env`](./g6-recovery-port-dry-run-remainder-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake RPO PASS rejected / fake HA PASS rejected / fake live-restore PASS rejected / fake dry-run-as-HA rejected / fake `g6_close=CLOSED` rejected / fake redo rejected | `2026-09-22T21:26:51Z` |
| #186 dry-run contract | **PASS** (inventory) | `--version` probe; **not** live restore |
| Dry-run is live restore | **false** | honesty gate enforces `dry_run_is_not_live_restore=true` |
| Ops drill is product HA | **false** | dedicated cluster ≠ product HA |
| Minute-level RPO | **SKIP** (expected) | no WAL / replica / delivery-grade checklist observed |
| Product HA | **SKIP** (expected) | no failover / standby / multi-AZ observed |
| Live restore | **SKIP** (expected) | unobserved; ops drill / `--version` is not live restore |
| Product G6 / RecoveryPort / HA close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #135 / #164 / #186 | not redone | `redone_135=false`, `redone_164=false`, `redone_186=false` |
| `haopan_platform` / product DB / `:5432` | **NOT_TOUCHED** | leftover only |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |
| Sibling leftover rows | kept | includes #186 + #220 `g15-wx-dt-native-bridge-remainder` + #215 `g1-queryast-export-honesty` + #210 `g17-gm-tls-nginx-handshake-honesty` + #217 `g5-publishhook-ddl-honesty` |

## Honest leftovers

- Minute-level RPO (WAL / replica / delivery-grade checklist)
- Product HA / delivery-grade HA / standby promotion
- Live product restore (not ops dedicated-cluster drill, not `--version`)
- Changing `RecoveryPort` from dry-run to execute
- V1.1 product single-tenant restore
- G17 classified / 密码机 / target Linux/ARM Profile
- Redo of #135 / #164 / #186
