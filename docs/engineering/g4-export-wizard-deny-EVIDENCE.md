# G4 leftover — HTTP export wizard complete-deny evidence

Recorded on this branch after rebase onto `origin/main` `fce63de` (Merge #164). **Not** a product G4 close. **Not** a redo of #152 begin/complete. **Not** a switch of `GET /tenants/{id}/audit-events` off staging. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g4-export-wizard-deny.md](../g4-export-wizard-deny.md). Keep leftover rows: [g4-export-wizard.md](../g4-export-wizard.md), [g4-p1-staging-audit-events.md](../g4-p1-staging-audit-events.md), [g17-vue-otp-writeback.md](../g17-vue-otp-writeback.md), [g17-gm-tls-nginx-handshake.md](../g17-gm-tls-nginx-handshake.md), [g9-live-multi-jvm-replica.md](../g9-live-multi-jvm-replica.md), [g6-recovery-port-honesty.md](../g6-recovery-port-honesty.md), [g1-fieldpolicy-mask-second-auth.md](../g1-fieldpolicy-mask-second-auth.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | pending this run | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | Zonky **15.x** for product IT | No Docker |
| Runtime | local `mvn test` + leftover script | Stub IdP; not live Keycloak |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"
./scripts/g4-export-wizard-deny.sh
```

## Results (agent VM)

Filled after `./scripts/g4-export-wizard-deny.sh` on this VM.

| Check | Result | Notes |
|---|---|---|
| In-memory complete-deny after begin | pending | `G4ExportWizardDenyAuditTest` |
| HTTP begin then complete 403 | pending | `ExportWizardApiTest` |
| Zonky JDBC deny pair / no completed | pending | `G4ExportWizardDenyIT` |
| `JdbcAuditChainTransactionTest` | pending | same-TX `append` unchanged |
| Vue HTTP deny surfaces | pending | `export-wizard.browser.test.js` |
| Docs leftover rows kept | pending | `g4-export-wizard` + `g17-vue-otp-writeback` + `g1-fieldpolicy-mask-second-auth` |
| Product G4 close | **NOT_CLAIMED** | honesty gate |
| P1 GET off staging | **not switched** | still open |

## Honest leftovers

- Switching P1 GET off staging (still open)
- Human laptop + live Keycloak CA / SPA verifier (already a leftover; not redone)
- Product export UI / report BI
- Browser wizard deny click (honest SKIP)
- G6 RecoveryPort / G17 classified
