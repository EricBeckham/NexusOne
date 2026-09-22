# G4 leftover — P1 staging audit-events honesty beyond #156 — evidence

Recorded on this branch after rebase onto `origin/main` (`514c9b7`, Merge #194 S0+S1 claim-list tip-sync; also #186 at `4c26d76` / #185 at `f59f879`). **Not** a product G4 close. **Not** a switch of `GET /tenants/{id}/audit-events` off staging. **Not** a redo of #147 / #152 / #156 / #166. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g4-p1-staging-audit-events.md](../g4-p1-staging-audit-events.md) (#156), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-export-wizard.md](../g4-export-wizard.md) (#152), [g4-export-wizard-deny.md](../g4-export-wizard-deny.md) (#166), [g4-pkce-spa-verifier.md](../g4-pkce-spa-verifier.md) (#147), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), and [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | live `127.0.0.1:5432` optional | Zonky / in-memory stay #156; this leftover does not start PG |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g4-p1-staging-audit-events-honesty.sh
./scripts/g4-p1-staging-audit-events-honesty.sh --verify-report <out>/report.env
./scripts/g4-p1-staging-audit-events-honesty.sh --verify-report \
  scripts/g4-p1-staging-audit-events-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-p1-staging-audit-events-honesty.sh --verify-report \
  scripts/g4-p1-staging-audit-events-honesty/fixtures/fake-live-pass.env
./scripts/g4-p1-staging-audit-events-honesty.sh --verify-report \
  scripts/g4-p1-staging-audit-events-honesty/fixtures/fake-g4-close.env
./scripts/g4-p1-staging-audit-events-honesty.sh --verify-report \
  scripts/g4-p1-staging-audit-events-honesty/fixtures/fake-switched-off-staging.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g4-p1-staging-audit-events-honesty/`. A sanitized copy is [`g4-p1-staging-audit-events-honesty/agent-vm.env`](./g4-p1-staging-audit-events-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g4_close=CLOSED` rejected / fake `switched_off_staging=true` rejected | after rebase onto `514c9b7` |
| Staging-like query/export | **PASS** (gate) | in-memory `AuditRepository`; collect `--skip-tests` on this VM (`mvn` not on PATH) |
| GET still on staging | **true** | `TenantController` listByTenant / listByType |
| Live `:5432` `platform.audit_event` | **SKIP** (expected) | credential unset; nothing on `:5432` |
| live_query_observed | **false** | honesty gate requires `false` on SKIP |
| leftover rows kept | **true** | includes #194 tip-sync pointers, #186 `g6-recovery-port-dry-run-honesty`, and #185 `s0s1-live-redis-minio-isolation-honesty` |
| #147/#152/#156/#166 redo | **false** | sibling leftovers |
| Product G4 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Switching P1 GET off staging onto M0-11
- Live `:5432` query+export on a host that actually has `platform.audit_event`
- Product export UI / #147 laptop CA click / product G4 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
