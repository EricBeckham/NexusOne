# G7 leftover — expense/travel list/detail/todo query honesty beyond #140

S0+S1 remainder after leave withdraw HTTP, expense/travel withdraw 403 (#103), Vue `todo-withdraw` click (#113), expense/travel query (#140 / [g7-query-expense-travel.md](./g7-query-expense-travel.md)), Vue HTTP reachability (#159), and Vue `todo-withdraw` honesty (#175). Rebased onto `origin/main` `eb8a151` (Merge #193 Linux/ARM Profile leftover honesty after #190). **G18 stays closed.** This leftover does **not** redo #103 / #113 / #140 / #159 / #175 withdraw paths. It does **not** rewrite `G7ExpenseTravelQueryIT`, withdraw ITs, Playwright, or `TodoInboxView.vue`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim a product G7 close. `g7_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g7-query-expense-travel](./g7-query-expense-travel.md) (#140), [g7-withdraw-browser](./g7-withdraw-browser.md) (#113/#159), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md) (#175), [g7-withdraw-expense-travel](./g7-withdraw-expense-travel.md) (#103), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md) (#183), [g17-separation-honesty](./g17-separation-honesty.md) (#187), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md) (#190), and [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193). Keep this file.

#140 already recorded expense + travel ledger list / field-equals / detail / todo query via `G7ExpenseTravelQueryIT` (Zonky PG 15, no live listener). That leftover is **not** a live product-JAR / live IdP query walk. The leftover here is **honesty only**: query surfaces stay **PASS** when the #140 contract is still present. Live JAR/IdP HTTP query is **PASS** only when a healthy JAR, issued context, live/Keycloak IdP, and an observed expense+travel query walk exist. Missing JAR/IdP is an honest SKIP. It never fabricates `PASS`. Zonky IT is **not** a live JAR.

## What this leftover covers

| Path | Behavior |
|---|---|
| #140 query contract | Expense + travel `GET /api/v1/p3/ledgers` / ledger detail / records list / field-equals / miss empty / record detail / pending / `/p3/todos` / todo detail / initiated / done. Always-green **PASS** when `G7ExpenseTravelQueryIT` still holds those surfaces. Do not redo the Zonky IT. |
| Live JAR probe | Optional `NEXUS_BASE_URL` / `NEXUS_G7_LIVE_BASE` + loopback `:8080` / `:8088`. Unset / down listener stays SKIP. |
| Issued context / IdP | `NEXUS_G7_LIVE_CONTEXT_ID` + live/Keycloak IdP. Stub login is **not** a live walk. |
| Honest live SKIP | `live_jar_idp_query=SKIP` unless healthy JAR **and** issued context **and** live/Keycloak IdP **and** `NEXUS_G7_LIVE_QUERY_OBSERVED=true` on expense + travel. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g7_close=CLOSED`, rejects #103/#113/#140/#159/#175 redo flags, and requires sibling leftover rows kept. |

No Docker. No product-JAR Playwright driver. Withdraw leftovers stay siblings.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G7 close | Explicitly `g7_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live product-JAR + live IdP HTTP query | Honest SKIP on this agent VM. Zonky IT is not a live walk. |
| #103 expense/travel withdraw HTTP | Already on `main`. `g7_103_redone=false`. |
| #113/#159 Vue `todo-withdraw` / HTTP reachability | Already on `main`. Not rerun as this leftover. |
| #140 expense/travel list/detail/todo query IT | Sibling leftover. `g7_140_redone=false`. Surfaces re-asserted, not rewritten. |
| #175 Vue `todo-withdraw` honesty | Sibling leftover. `g7_175_redone=false`. |
| Live browser query click | Still open. Not this package. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

```bash
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

Always-green Java (does **not** rerun `G7ExpenseTravelQueryIT` / withdraw ITs):

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=G7QueryExpenseTravelHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #140 leftover stays the reproduction command for the original Zonky IT:

```bash
# do not run for this leftover
# ./scripts/g7/run-query-expense-travel.sh
```

If a live product JAR is later listening (native process, not Docker):

```bash
export NEXUS_BASE_URL=http://127.0.0.1:8080
export NEXUS_G7_LIVE_CONTEXT_ID=...   # issued X-Nexus-Context-Id; env / Secret only
export NEXUS_G7_LIVE_IDP=keycloak
export NEXUS_G7_LIVE_QUERY_OBSERVED=true
./scripts/g7-query-expense-travel-honesty.sh
```

`PASS` for live JAR/IdP query requires all of: reachable product JAR, issued context, live/Keycloak IdP, and an observed expense+travel list/field-equals/detail/todo walk. This leftover does not start the JAR.

See [engineering/g7-query-expense-travel-honesty-EVIDENCE.md](./engineering/g7-query-expense-travel-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g7-query-expense-travel-honesty/`.

## Honest leftovers (not claimed)

- Live product-JAR + live/Keycloak IdP HTTP query of expense / travel list / field-equals / detail / todo
- Live browser query click for expense / travel
- Three-type product G7 close
- Redo of #103 / #113 / #140 / #159 / #175 withdraw paths
