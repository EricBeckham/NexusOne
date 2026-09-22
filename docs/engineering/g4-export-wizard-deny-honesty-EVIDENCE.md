# G4 leftover — export-wizard deny / guard-deny honesty beyond #166/#136 — evidence

Recorded on this branch after rebase onto `origin/main` `5109245` (Merge #196 G5 mid-publish/browser leftover honesty; also #195 at `c8cd853`, #191 at `fb333a3`, #198 at `724f5f0`, #200 at `f8e5f15`). **Not** a product G4 close. **Not** a redo of #136 / #152 / #166 / #190. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g4-export-wizard-deny.md](../g4-export-wizard-deny.md) (#166), [g4-guard-deny-audit.md](../g4-guard-deny-audit.md) (#136), [g4-export-wizard.md](../g4-export-wizard.md) (#152), [g4-p1-staging-audit-events.md](../g4-p1-staging-audit-events.md) (#156), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g4-hot-purge-90d.md](../g4-hot-purge-90d.md) (#123), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g4-pkce-spa-verifier.md](../g4-pkce-spa-verifier.md) (#147), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md) (#143), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g15-wx-dt-prod-gap-honesty.md](../g15-wx-dt-prod-gap-honesty.md) (#167), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g1-queryast-boundsql-guard.md](../g1-queryast-boundsql-guard.md) (#146), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), and [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | live `127.0.0.1:5432` optional | Zonky / in-memory stay #136/#166; this leftover does not start PG |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g4-export-wizard-deny-honesty.sh
./scripts/g4-export-wizard-deny-honesty.sh --verify-report <out>/report.env
./scripts/g4-export-wizard-deny-honesty.sh --verify-report \
  scripts/g4-export-wizard-deny-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-export-wizard-deny-honesty.sh --verify-report \
  scripts/g4-export-wizard-deny-honesty/fixtures/fake-live-pass.env
./scripts/g4-export-wizard-deny-honesty.sh --verify-report \
  scripts/g4-export-wizard-deny-honesty/fixtures/fake-g4-close.env
./scripts/g4-export-wizard-deny-honesty.sh --verify-report \
  scripts/g4-export-wizard-deny-honesty/fixtures/fake-redo.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g4-export-wizard-deny-honesty/`. A sanitized copy is [`g4-export-wizard-deny-honesty/agent-vm.env`](./g4-export-wizard-deny-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g4_close=CLOSED` rejected / fake redo rejected | after rebase onto `5109245` |
| Guard deny harness | **PASS** (gate) | in-memory `SurfaceGuard` #136 pair; collect `--skip-tests` on this VM (unrelated `DualStackTlsProbe` / BouncyCastle compile) |
| Export wizard deny harness | **PASS** (gate) | begin then revoke/DENY; no IsolatedExport / `p3.export.completed`; collect `--skip-tests` on this VM |
| Same-TX append | **PASS** | reject uses `appendNewTransaction` |
| Live JAR wizard deny | **SKIP** (expected) | no product JAR / walk not observed |
| Live browser wizard | **SKIP** (expected) | Playwright / issued context missing |
| Live `:5432` JDBC | **SKIP** (expected) | nothing on `:5432`; Zonky is not a live walk |
| Live Keycloak | **SKIP** (expected) | no issued-context walk |
| leftover rows kept | **true** | includes #196 `g5-mid-publish-failure-honesty`, #195 `g3-outbox-consume-crash-honesty`, #191 `g1-queryast-boundsql-honesty`, #198 `g15-wx-dt-native-bridge-honesty`, and #200 `s0-secret-rotate-without-restart-honesty` |
| #136/#152/#166/#190 redo | **false** | sibling leftovers |
| Product G4 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product JAR HTTP begin-then-complete-deny with issued context
- Playwright click of `/p3/exports/wizard` deny
- Live `:5432` JDBC deny pair
- Live Keycloak issued-context
- Switching P1 GET off staging
- Product export UI / product G4 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
