# G6 leftover: RecoveryPort still dry-run / not product HA honesty evidence

Recorded on this branch after rebase onto `origin/main` `0615095` (Merge #187 G17 SoD leftover honesty; includes #183 at `cf8fc8b`). **Not** a product RecoveryPort close. **Not** a product G6 close. **Not** minute-level RPO. **Not** product HA. **Not** delivery-grade HA. **Not** a redo of #135 or #164. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `haopan_platform` was not touched. Product DB was not mutated.

See [docs/g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md). Keep **both** leftover rows in [docs/README.md](../README.md): [g6-recovery-port-dry-run.md](../g6-recovery-port-dry-run.md) (#135) and [g6-recovery-port-honesty.md](../g6-recovery-port-honesty.md) (#164). Keep ALL sibling leftover rows.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x / 3.9.x** | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | not required | leftover does not start PG and does not target `:5432` |
| Runtime | local `mvn test` + collect script | No Docker / Compose. No `haopan_platform` |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

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

Do **not** treat `./scripts/g6-recovery-port-dry-run.sh` or `./scripts/g6-recovery-port-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g6-recovery-port-dry-run-honesty/`.

| Check | Result | Notes |
|---|---|---|
| Dry-run contract | **PASS** | `executed=false`, `profileAccepted=false`; no Zonky redo |
| Minute-level RPO | **SKIP** (expected) | no WAL / replica / delivery-grade checklist observed |
| Product HA | **SKIP** (expected) | no failover / standby / multi-AZ observed |
| Executing restore | **SKIP** (expected) | `--version` probe only |
| Product G6 / RecoveryPort close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #135 / #164 | not redone | `redone_135=false`, `redone_164=false` |
| `haopan_platform` / product DB / `:5432` | **NOT_TOUCHED** | leftover only |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |
| Both leftover rows | kept | `g6-recovery-port-dry-run` + `g6-recovery-port-honesty` |

## Honest leftovers

- Minute-level RPO (WAL / replica / delivery-grade checklist)
- Product HA / delivery-grade HA / standby promotion
- Changing `RecoveryPort` from dry-run to execute
- V1.1 product single-tenant restore
- G17 classified / 密码机 / target Linux/ARM Profile
