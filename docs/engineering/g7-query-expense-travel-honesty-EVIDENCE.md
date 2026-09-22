# G7 leftover: expense/travel list/detail/todo query honesty

Recorded on this branch after rebase onto `origin/main` `eb8a151` (Merge #193 Linux/ARM Profile leftover honesty after #190). **Not** a product G7 close. **Not** a redo of #103 / #113 / #140 / #159 / #175. **Not** a live product-JAR / live IdP query walk. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g7-query-expense-travel.md](../g7-query-expense-travel.md) (#140), [g7-withdraw-browser.md](../g7-withdraw-browser.md) (#113/#159), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g7-withdraw-expense-travel.md](../g7-withdraw-expense-travel.md) (#103), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), and [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | not on PATH | collect `--skip-tests` on this VM |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g7-query-expense-travel-honesty.sh
./scripts/g7-query-expense-travel-honesty.sh --verify-report <out>/report.env
./scripts/g7-query-expense-travel-honesty.sh --verify-report \
  scripts/g7-query-expense-travel-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g7-query-expense-travel-honesty.sh --verify-report \
  scripts/g7-query-expense-travel-honesty/fixtures/fake-live-pass.env
./scripts/g7-query-expense-travel-honesty.sh --verify-report \
  scripts/g7-query-expense-travel-honesty/fixtures/fake-g7-close.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g7-query-expense-travel-honesty/`. A sanitized copy is [`g7-query-expense-travel-honesty/agent-vm.env`](./g7-query-expense-travel-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g7_close=CLOSED` rejected | `2026-09-22T20:26:41Z` |
| Expense list/field-equals/detail/todo surface | **PASS** | #140 `G7ExpenseTravelQueryIT` contract still present |
| Travel list/field-equals/detail/todo surface | **PASS** | #140 contract still present |
| leftover rows kept | **true** | all sibling leftover rows stay |
| Live product-JAR + live IdP query | **SKIP** (expected) | `live_jar_not_healthy` (`NEXUS_BASE_URL` unset) |
| query_walk_observed | **false** | honesty gate requires `false` on SKIP |
| #103/#113/#140/#159/#175 redo | **false** | withdraw paths + #140 IT not rewritten |
| Product G7 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + live/Keycloak IdP HTTP query of expense / travel list / field-equals / detail / todo
- Live browser query click for expense / travel
- Three-type product G7 close
