# G4 leftover — P1 staging audit-events query / export evidence

Recorded on this branch after rebase onto `origin/main` `8e5cdc3` (Merge #152). **Not** a product G4 close. **Not** a switch of `GET /tenants/{id}/audit-events` off staging. **Not** an HTTP export wizard redo. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g4-p1-staging-audit-events.md](../g4-p1-staging-audit-events.md). Keep both leftover rows: [g4-export-wizard.md](../g4-export-wizard.md) and this leftover.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | pending this run | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | Zonky **15.x** for staging-like GET + export | Live `127.0.0.1:5432` is optional SKIP |
| Runtime | local `mvn test` + leftover script | No Docker / Compose / OpenBao / KMS |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"
./scripts/g4-p1-staging-audit-events.sh
```

## Results (agent VM)

Filled after `./scripts/g4-p1-staging-audit-events.sh` on this VM. Live output is gitignored under `.local/g4-p1-staging-audit-events/`.

| Check | Result | Notes |
|---|---|---|
| In-memory query + JSON export | pending | `P1StagingAuditEventsHonestyTest` |
| Product GET still uses `AuditRepository` | pending | source honesty; not `AuditPort.findByTraceId` |
| Zonky GET reads `platform.audit_event` | pending | staging-only row on GET; hash-chain-only row not on GET |
| `JdbcAuditChainTransactionTest` | pending | same-TX `append` unchanged |
| Live `:5432` `platform.audit_event` | pending | SKIP if password / connection / table missing |
| HTTP export wizard | **not redone** | [g4-export-wizard.md](../g4-export-wizard.md) |
| Product G4 close | **NOT_CLAIMED** | honesty gate |

## Honest leftovers

- Switching P1 GET off staging (still open)
- Human laptop + live Keycloak CA / SPA verifier
- Product export UI / report BI (wizard leftover stays #152)
- Live `:5432` when credentials / table are missing
- G6 RecoveryPort / G17 classified
