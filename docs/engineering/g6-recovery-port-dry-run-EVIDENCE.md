# G6 leftover: RecoveryPort honesty / dry-run contract evidence

Recorded **pending-this-run** on this agent VM after rebase onto `origin/main` `46cbd58` (Merge #133). **Not** a product RecoveryPort close. **Not** minute-level RPO. **Not** product HA. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `haopan_platform` was not touched.

See [docs/g6-recovery-port-dry-run.md](../g6-recovery-port-dry-run.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x** | filled after the test run |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | Zonky **15.x** | dedicated embedded port; never `5432` |
| Runtime | local `mvn test` | No Docker / Compose. No `haopan_platform` |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g6-recovery-port-dry-run.sh
```

## Results (agent VM)

Filled after the contract suite on this VM.

| Check | Result | Notes |
|---|---|---|
| `DryRunRecoveryPortTest` | pending | plans stay `profileAccepted=false`; dry-run `executed=false` |
| `DryRunRecoveryPortContractTest` | pending | source stays `--version` only; leftover docs stay honest |
| `DryRunRecoveryPortDoesNotMutateDbIT` | pending | Zonky PG 15 witness row unchanged; no `haopan*` database |
| `G6RecoveryPortDryRunIT` | pending | product bean is `DryRunRecoveryPort`; Flyway / audit / `pg_database` unchanged |
| Minute-level RPO / product HA / product RecoveryPort close | **NOT_CLAIMED** | leftover only locks the dry-run contract |
| `haopan_platform` / `:5432` | **NOT_TOUCHED** | Zonky dedicated port |
| G18 / AuditPort | unchanged | not reopened |

## Honest leftovers

- Minute-level RPO (WAL / replica / delivery-grade checklist)
- Product HA / standby promotion
- Changing `RecoveryPort` from dry-run to execute
- V1.1 product single-tenant restore
- G17 classified / 密码机 / target Linux/ARM Profile
