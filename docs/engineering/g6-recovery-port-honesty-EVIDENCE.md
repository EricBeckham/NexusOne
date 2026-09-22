# G6 leftover: RecoveryPort still dry-run honesty evidence

Recorded **pending-this-run** on this agent VM after rebase onto `origin/main` `2f2825a` (Merge #161; also #160 at `845df2b`). **Not** a product RecoveryPort close. **Not** minute-level RPO. **Not** product HA. **Not** delivery-grade HA. **Not** a redo of #135. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `haopan_platform` was not touched.

See [docs/g6-recovery-port-honesty.md](../g6-recovery-port-honesty.md). Sibling #135 row stays on [docs/g6-recovery-port-dry-run.md](../g6-recovery-port-dry-run.md). Keep both leftover rows in `docs/README.md`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x / 3.9.x** | filled after the test run |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | not required | honesty leftover does not start PG |
| Runtime | local `mvn test` | No Docker / Compose. No `haopan_platform` |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g6-recovery-port-honesty.sh
```

Do **not** treat `./scripts/g6-recovery-port-dry-run.sh` as this leftover. That script is the #135 contract.

## Results (agent VM)

Filled after the thin honesty suite on this VM.

| Check | Result | Notes |
|---|---|---|
| `G6RecoveryPortHonestyTest` | pending | leftover docs + claim-list stay honest; both README leftover rows kept |
| Thin dry-run API | pending | `executed=false`, `profileAccepted=false`; no Zonky redo |
| Minute-level RPO / product HA / delivery-grade HA / product RecoveryPort close | **NOT_CLAIMED** | #135 dry-run is not those closes |
| #135 dry-run contract | unchanged | not redone |
| `haopan_platform` / `:5432` | **NOT_TOUCHED** | honesty leftover only |
| G18 / AuditPort | unchanged | not reopened |

## Honest leftovers

- Minute-level RPO (WAL / replica / delivery-grade checklist)
- Product HA / delivery-grade HA / standby promotion
- Changing `RecoveryPort` from dry-run to execute
- V1.1 product single-tenant restore
- G17 classified / 密码机 / target Linux/ARM Profile
