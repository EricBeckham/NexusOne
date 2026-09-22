# G7 leftover — expense/travel list/detail/todo query honesty remainder beyond #192/#140

S0+S1 remainder **after** the #140 expense/travel list/detail/todo query leftover ([g7-query-expense-travel.md](./g7-query-expense-travel.md)), the #192 query honesty leftover ([g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md)), and the related withdraw siblings (#103 expense/travel withdraw 403, #113/#159 Vue `todo-withdraw` / HTTP reachability, #175 Vue `todo-withdraw` honesty, #212 browser-visible withdraw evidence honesty). Rebased onto `origin/main` `c8777b6` (Merge #237 G9 Redis-down/invalidate leftover honesty after #232 G2 H5/form.write leftover honesty after #212). **G18 stays closed.** This leftover does **not** redo #103 / #113 / #140 / #159 / #175 / #192 / #212 product code. It does **not** rewrite `G7ExpenseTravelQueryIT`, withdraw ITs, Playwright, or `TodoInboxView.vue`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0 / M0. It does **not** claim a product G7 close. `g7_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g7-query-expense-travel](./g7-query-expense-travel.md) (#140), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), this file, [g7-withdraw-browser](./g7-withdraw-browser.md) (#113/#159), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md) (#175), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g7-withdraw-expense-travel](./g7-withdraw-expense-travel.md) (#103), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), and the other leftover rows already on `main`. Keep this file.

#140 already recorded expense + travel ledger list / field-equals / detail / todo query via `G7ExpenseTravelQueryIT` (Zonky PG 15, no live listener). #192 already inventories those query surfaces as always-green **PASS** and live JAR/IdP HTTP query as honest **SKIP**. The leftover here is the **remainder honesty gate beyond those two**:

| Remaining path | Honesty |
|---|---|
| #140 leftover cells | Expense + travel list / field-equals / detail / todo surfaces still present. `leftover_140_cells_honest=PASS`. Do not redo the Zonky IT. |
| #192 leftover cells | Query surfaces **PASS**, live JAR/IdP **SKIP**, `g7_close=NOT_CLAIMED`. Parent `--verify-report` honest SKIP accepted; fake live PASS / fake `g7_close=CLOSED` rejected. `leftover_192_cells_honest=PASS`. `leftover_192_honesty_gate=PASS`. Do not redo. |
| #175 withdraw honesty sibling | Vue `todo-withdraw` honesty leftover stays a sibling. `leftover_175_cells_honest=PASS`. Do not redo. |
| #212 leftover cells | Browser-visible stub walk **PASS**, live JAR/IdP withdraw **SKIP**, `g7_close=NOT_CLAIMED`. `leftover_212_cells_honest=PASS`. Do not redo. |
| Query surfaces (expense + travel) | Always-green **PASS** when the #140 contract is still present. Not a Zonky IT redo. |
| Live JAR / issued context / live IdP query | **PASS** only when healthy JAR + issued context + live/Keycloak IdP + observed expense+travel query walk. Missing JAR/IdP is honest **SKIP**. |
| Live browser query click | Still **OPEN**. **PASS** only when live JAR + issued context + observed browser click. Unavailable is honest **SKIP**. |
| Product G7 close | **NOT_CLAIMED**. |

Missing live JAR / IdP is an honest **SKIP**. It never fabricates `PASS`. Zonky IT is **not** a live JAR.

## What this leftover covers

| Surface | Behavior |
|---|---|
| #140 leftover cells | `G7ExpenseTravelQueryIT` contract still present. Do not redo. |
| #192 leftover cells | Committed #192 agent-vm artifact stays query PASS / live SKIP. Do not redo. |
| Remainder honesty gate | `--verify-report` rejects fake live JAR/IdP PASS, rejects fake browser query PASS, rejects `g7_close=CLOSED`, and rejects #103/#113/#140/#159/#175/#192/#212 redo flags. Requires leftover rows kept (including #140 / #192 / #175 / #212 / #230 / #232 / #237). |
| Live walks | `live_jar_idp_query` / `live_browser_query_click` are **PASS** only with reachable live path + observed walk. |

This leftover does **not** start the product JAR, IdP, or Playwright. Parent leftover scripts stay siblings.

## How to run

```bash
./scripts/g7-query-expense-travel-honesty-remainder.sh
./scripts/g7-query-expense-travel-honesty-remainder.sh --verify-report <out>/report.env
./scripts/g7-query-expense-travel-honesty-remainder.sh --verify-report \
  scripts/g7-query-expense-travel-honesty-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g7-query-expense-travel-honesty-remainder.sh --verify-report \
  scripts/g7-query-expense-travel-honesty-remainder/fixtures/fake-live-pass.env fail
./scripts/g7-query-expense-travel-honesty-remainder.sh --verify-report \
  scripts/g7-query-expense-travel-honesty-remainder/fixtures/fake-browser-pass.env fail
./scripts/g7-query-expense-travel-honesty-remainder.sh --verify-report \
  scripts/g7-query-expense-travel-honesty-remainder/fixtures/fake-g7-close.env fail
./scripts/g7-query-expense-travel-honesty-remainder.sh --verify-report \
  scripts/g7-query-expense-travel-honesty-remainder/fixtures/fake-redone.env fail
```

#140 / #192 stay the sibling reproduction commands:

```bash
# do not run for this leftover
# ./scripts/g7/run-query-expense-travel.sh
# ./scripts/g7-query-expense-travel-honesty.sh
```

Parent `--verify-report` on honest fixtures is allowed as leftover-cell evidence.

If a live product JAR is later listening (native process, not Docker):

```bash
export NEXUS_BASE_URL=http://127.0.0.1:8080
export NEXUS_G7_LIVE_CONTEXT_ID=...   # issued X-Nexus-Context-Id; env / Secret only
export NEXUS_G7_LIVE_IDP=keycloak
export NEXUS_G7_LIVE_QUERY_OBSERVED=true
./scripts/g7-query-expense-travel-honesty-remainder.sh
```

`PASS` for live JAR/IdP query requires all of: reachable product JAR, issued context, live/Keycloak IdP, and an observed expense+travel list/field-equals/detail/todo walk. This leftover does not start the JAR.

See [engineering/g7-query-expense-travel-honesty-remainder-EVIDENCE.md](./engineering/g7-query-expense-travel-honesty-remainder-EVIDENCE.md). Live `report.env` is gitignored under `.local/g7-query-expense-travel-honesty-remainder/`. Walk JSON: [g7-query-expense-travel-honesty-remainder/walk.json](./g7-query-expense-travel-honesty-remainder/walk.json).

## What this does not claim

| Leftover | Why |
|---|---|
| Product G7 close | Explicitly `g7_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live product-JAR + live IdP HTTP query | Honest SKIP on this agent VM. Zonky IT is not a live walk. |
| Live browser query click | Still OPEN. Honest SKIP when JAR/IdP / observed click is missing. |
| #103 expense/travel withdraw HTTP | Already on `main`. `g7_103_redone=false`. |
| #113/#159 Vue `todo-withdraw` / HTTP reachability | Already on `main`. Not rerun as this leftover. |
| #140 expense/travel list/detail/todo query IT | Sibling leftover. `g7_140_redone=false`. Surfaces re-asserted, not rewritten. |
| #175 Vue `todo-withdraw` honesty | Sibling leftover. `g7_175_redone=false`. |
| #192 query honesty leftover | Sibling leftover. `leftover_192_redone=false`. |
| #212 browser-visible withdraw evidence honesty | Sibling leftover. `leftover_212_redone=false`. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- Live product-JAR + live/Keycloak IdP HTTP query of expense / travel list / field-equals / detail / todo
- Live browser query click for expense / travel
- Three-type product G7 close
- Redo of #103 / #113 / #140 / #159 / #175 / #192 / #212
