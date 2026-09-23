# G7 leftover — expense/travel query + withdraw browser-evidence leftover honesty beyond #239/#212/#192

S0+S1 remainder **after** the #239 expense/travel query honesty remainder leftover ([g7-query-expense-travel-honesty-remainder.md](./g7-query-expense-travel-honesty-remainder.md)), the #212 browser-visible withdraw evidence honesty leftover ([g7-withdraw-browser-evidence-honesty.md](./g7-withdraw-browser-evidence-honesty.md)), and the #192 query honesty leftover ([g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md)). Rebased onto `origin/main` `2ac6326` (Merge #263 Linux/ARM leftover honesty remainder after #262 G10 file/object leftover honesty after #259 G9 leftover honesty remainder after #249 G17 SoD/admin-force leftover honesty remainder after #240 G15 H5/PKCE leftover honesty after #254 G2 H5/form.write/report leftover honesty after #236 Linux/ARM profile remainder leftover honesty after #251 G4 leftover honesty after #243 G10 leftover honesty after #241 SecretPort leftover honesty). **G18 stays closed.** This leftover does **not** redo #103 / #113 / #140 / #159 / #175 / #192 / #212 / #239 product code. It does **not** rewrite `G7ExpenseTravelQueryIT`, withdraw ITs, Playwright, or `TodoInboxView.vue`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0 / M0. It does **not** claim a product G7 close. `g7_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g7-query-expense-travel](./g7-query-expense-travel.md) (#140), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), this file, [g7-withdraw-browser](./g7-withdraw-browser.md) (#113/#159), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md) (#175), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g7-withdraw-expense-travel](./g7-withdraw-expense-travel.md) (#103), [g15-h5-pkce-miniprogram-remainder-honesty](./g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g17-separation-leftover-honesty-remainder](./g17-separation-leftover-honesty-remainder.md) (#249), [g9-leftover-honesty-remainder](./g9-leftover-honesty-remainder.md) (#259), [g10-file-object-reconcile-leftover-honesty](./g10-file-object-reconcile-leftover-honesty.md) (#262), [linux-arm-profile-leftover-remainder-honesty](./linux-arm-profile-leftover-remainder-honesty.md) (#263), [g2-h5-every-page-form-write-report-remainder-honesty](./g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty](./linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty](./g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty](./g10-file-object-reconcile-remainder-honesty.md) (#243), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g5-publishhook-ddl-remainder-honesty](./g5-publishhook-ddl-remainder-honesty.md) (#235), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g17-mfa-recovery-vue-otp-remainder-honesty](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), and every leftover-style sibling already on `origin/main`. Keep this file.

#239 already recorded query surfaces as always-green **PASS** and live JAR/IdP query / browser query click as honest **SKIP**. #212 already inventories stub walk + screenshots as **PASS** and live JAR/IdP browser-visible withdraw as honest **SKIP**. #192 already inventories query surfaces as **PASS** and live JAR/IdP HTTP query as honest **SKIP**. The leftover here is the **honesty leftover beyond those three**:

| Remaining path | Honesty |
|---|---|
| #239 leftover cells | Query surfaces **PASS**, live JAR/IdP **SKIP**, `g7_close=NOT_CLAIMED`. Parent `--verify-report` honest SKIP accepted; fake live PASS / fake `g7_close=CLOSED` rejected. `leftover_239_cells_honest=PASS`. `leftover_239_honesty_gate=PASS`. Do not redo. |
| #212 leftover cells | Browser-visible stub walk **PASS**, live JAR/IdP withdraw **SKIP**, `g7_close=NOT_CLAIMED`. `leftover_212_cells_honest=PASS`. Do not redo. |
| #192 leftover cells | Query surfaces **PASS**, live JAR/IdP **SKIP**, `g7_close=NOT_CLAIMED`. `leftover_192_cells_honest=PASS`. Do not redo. |
| Query surfaces (expense + travel) | Always-green **PASS** when the #140 contract is still present. Not a Zonky IT redo. |
| Live JAR / issued context / live IdP query | **PASS** only when healthy JAR + issued context + live/Keycloak IdP + observed expense+travel query walk. Missing JAR/IdP is honest **SKIP**. |
| Live browser query click | Still **OPEN**. **PASS** only when live JAR + issued context + observed browser click. Unavailable is honest **SKIP**. |
| Live browser-visible withdraw | **PASS** only when healthy JAR + issued context + live/Keycloak IdP + observed `todo-withdraw` click. Stub walk is **not** a live walk. Missing path is honest **SKIP**. |
| Product G7 close | **NOT_CLAIMED**. |

Missing live JAR / IdP is an honest **SKIP**. It never fabricates `PASS`. Zonky IT / stub walk.json is **not** a live JAR.

## What this leftover covers

| Surface | Behavior |
|---|---|
| #239 leftover cells | `G7ExpenseTravelQueryIT` contract still present; parent remainder honesty fixtures PASS. Do not redo. |
| #212 leftover cells | Committed stub walk PASS / live withdraw SKIP. Do not redo. |
| #192 leftover cells | Committed query PASS / live SKIP. Do not redo. |
| Remainder honesty gate | `--verify-report` rejects fake live JAR/IdP PASS, rejects fake browser query PASS, rejects fake live withdraw PASS, rejects `g7_close=CLOSED`, and rejects #103/#113/#140/#159/#175/#192/#212/#239 redo flags. Requires leftover rows kept (including #239 / #212 / #192 / #263 / #262 / #259 / #249 / #240 / #254 / #236 / #251 / #243 / #241). |
| Live walks | `live_jar_idp_query` / `live_browser_query_click` / `live_jar_idp_browser_withdraw` are **PASS** only with reachable live path + observed walk. |

This leftover does **not** start the product JAR, IdP, or Playwright. Parent leftover scripts stay siblings.

## How to run

```bash
./scripts/g7-query-withdraw-leftover-honesty.sh
./scripts/g7-query-withdraw-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g7-query-withdraw-leftover-honesty.sh --verify-report \
  scripts/g7-query-withdraw-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g7-query-withdraw-leftover-honesty.sh --verify-report \
  scripts/g7-query-withdraw-leftover-honesty/fixtures/fake-live-pass.env fail
./scripts/g7-query-withdraw-leftover-honesty.sh --verify-report \
  scripts/g7-query-withdraw-leftover-honesty/fixtures/fake-browser-pass.env fail
./scripts/g7-query-withdraw-leftover-honesty.sh --verify-report \
  scripts/g7-query-withdraw-leftover-honesty/fixtures/fake-withdraw-pass.env fail
./scripts/g7-query-withdraw-leftover-honesty.sh --verify-report \
  scripts/g7-query-withdraw-leftover-honesty/fixtures/fake-g7-close.env fail
./scripts/g7-query-withdraw-leftover-honesty.sh --verify-report \
  scripts/g7-query-withdraw-leftover-honesty/fixtures/fake-redone.env fail
```

#140 / #192 / #212 / #239 stay the sibling reproduction commands:

```bash
# do not run for this leftover
# ./scripts/g7/run-query-expense-travel.sh
# ./scripts/g7-query-expense-travel-honesty.sh
# ./scripts/g7-withdraw-browser-evidence-honesty.sh
# ./scripts/g7-query-expense-travel-honesty-remainder.sh
```

Parent `--verify-report` on honest fixtures is allowed as leftover-cell evidence.

`PASS` for live JAR/IdP query or withdraw requires all of: reachable product JAR, issued context, live/Keycloak IdP, and an observed walk. This leftover does not start the JAR.

See [engineering/g7-query-withdraw-leftover-honesty-EVIDENCE.md](./engineering/g7-query-withdraw-leftover-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g7-query-withdraw-leftover-honesty/`. Walk JSON: [g7-query-withdraw-leftover-honesty/walk.json](./g7-query-withdraw-leftover-honesty/walk.json).

## What this does not claim

| Leftover | Why |
|---|---|
| Product G7 close | Explicitly `g7_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live product-JAR + live IdP HTTP query | Honest SKIP on this agent VM. Zonky IT is not a live walk. |
| Live browser query click | Still OPEN. Honest SKIP when JAR/IdP / observed click is missing. |
| Live browser-visible withdraw | Honest SKIP. Stub walk.json is not a live walk. |
| #103 / #113 / #140 / #159 / #175 / #192 / #212 / #239 redo | Sibling leftovers. `leftover_*_redone=false`. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- Live product-JAR + live/Keycloak IdP HTTP query of expense / travel list / field-equals / detail / todo
- Live browser query click for expense / travel
- Live product-JAR + live/Keycloak IdP Playwright click of `data-testid=todo-withdraw`
- Three-type product G7 close
- Redo of #103 / #113 / #140 / #159 / #175 / #192 / #212 / #239
