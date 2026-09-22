# G7 leftover — Vue `todo-withdraw` / browser evidence honesty

S0+S1 remainder after leave withdraw HTTP, expense/travel withdraw 403 (#103), Vue `todo-withdraw` click (#113), expense/travel query (#140), and Vue HTTP reachability (#159 / [g7-withdraw-browser.md](./g7-withdraw-browser.md)). Rebased onto `origin/main` `4457ec2` (after #176 S0+S1 claim-list tip align). **G18 stays closed.** This leftover does **not** redo #103 / #113 / #140 / #159. It does **not** rewrite `TodoInboxView.vue`, Playwright stub walk, or `G7LeaveWithdrawIT` / `G7ExpenseTravelWithdrawIT` / `G7ExpenseTravelQueryIT`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim a product G7 close.

#113/#159 proved stub Vue click + adapter `POST /api/v1/p3/path/instances/{id}/withdraw` reachability + Playwright stub login. That leftover is **not** a live product-JAR / live IdP withdraw click. The leftover here is **honesty only**: live JAR/IdP browser withdraw is **PASS** only when those are present **and** a live withdraw click was observed. Missing JAR/IdP is an honest SKIP. It never fabricates `PASS`.

Keep both leftover rows in [docs/README.md](./README.md): [g7-withdraw-browser.md](./g7-withdraw-browser.md) (#113/#159) and [g7-query-expense-travel.md](./g7-query-expense-travel.md) (#140). This file is the honesty leftover beside them. Query-surface honesty leftover is [g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md) (does not redo this withdraw leftover). Browser-visible withdraw evidence honesty leftover is [g7-withdraw-browser-evidence-honesty.md](./g7-withdraw-browser-evidence-honesty.md) (beyond #175; stub walk PASS, live JAR/IdP SKIP). Keep sibling leftovers including [g3-claim-crash-window.md](./g3-claim-crash-window.md) (#170), [g6-recovery-port-honesty.md](./g6-recovery-port-honesty.md) (#164), [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) (#161), [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) (#169), and [g15-wx-dt-prod-gap-honesty.md](./g15-wx-dt-prod-gap-honesty.md) (#167).

## What this leftover covers

| Path | Behavior |
|---|---|
| #113/#159 stub Vue + HTTP reachability | Already on `main`. Do not redo. Stub walk is **not** a live walk. |
| Live JAR probe | Optional `NEXUS_BASE_URL/actuator/health`. Unset / down listener stays SKIP. |
| Issued context / IdP | `NEXUS_G7_LIVE_CONTEXT_ID` + live/Keycloak IdP. Stub login is **not** a live walk. |
| Honest live SKIP | `live_jar_idp_withdraw=SKIP` unless healthy JAR **and** issued context **and** live/Keycloak IdP **and** Chrome **and** `NEXUS_G7_LIVE_WITHDRAW_OBSERVED=true` on leave + expense + travel. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g7_close=CLOSED`, rejects #103/#113/#140/#159 redo flags, and requires both leftover rows kept. |

No Docker. No product-JAR Playwright driver. Vue `TodoInboxView.vue` is not rewritten.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G7 close | Explicitly `g7_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live product-JAR + live IdP browser withdraw | Honest SKIP on this agent VM. Stub login is not a live walk. |
| #103 expense/travel withdraw HTTP | Already on `main`. `g7_103_redone=false`. |
| #113/#159 Vue `todo-withdraw` / HTTP reachability | Already on `main`. Not rerun as this leftover. |
| #140 expense/travel list/detail/todo query | Sibling leftover. `g7_140_redone=false`. |
| Live browser query click | Still open. Not this package. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

```bash
./scripts/g7-withdraw-browser-honesty.sh
./scripts/g7-withdraw-browser-honesty.sh --verify-report <out>/report.env
./scripts/g7-withdraw-browser-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g7-withdraw-browser-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-honesty/fixtures/fake-live-pass.env
./scripts/g7-withdraw-browser-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-honesty/fixtures/fake-g7-close.env
```

Always-green Node (does **not** rerun #113/#159 Vue / Playwright):

```bash
cd web
npm run test:g7-withdraw-browser-honesty
npm run assert:g7-withdraw-browser-honesty
```

The #113/#159 leftover stays a sibling:

```bash
./scripts/g7/run-withdraw-browser.sh
```

Do **not** treat that script as this leftover. Java 21 / Boot **4.1.1**. No Docker. Live product-JAR / live IdP are **not** required.

See [engineering/g7-withdraw-browser-honesty-EVIDENCE.md](./engineering/g7-withdraw-browser-honesty-EVIDENCE.md).

## Honest leftovers (not claimed)

- Live product-JAR + live/Keycloak IdP Playwright click of `data-testid=todo-withdraw`
- Live browser query click for expense / travel
- Three-type product G7 close
- Redo of #103 / #113 / #140 / #159
