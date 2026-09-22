# G7 leftover: expense/travel query + withdraw browser-evidence leftover honesty beyond #239/#212/#192

See [docs/g7-query-withdraw-leftover-honesty.md](../g7-query-withdraw-leftover-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g7-query-expense-travel.md](../g7-query-expense-travel.md) (#140), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), this leftover, [g7-withdraw-browser.md](../g7-withdraw-browser.md) (#113/#159), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g7-withdraw-expense-travel.md](../g7-withdraw-expense-travel.md) (#103), [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md) (#249), [g9-leftover-honesty-remainder.md](../g9-leftover-honesty-remainder.md) (#259), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), and the other leftover rows already on `main`.

Recorded on this branch after rebase onto `origin/main` `e10be1f` (Merge #259 G9 leftover honesty remainder after #249). **Not** a product G7 close. **Not** a redo of #103 / #113 / #140 / #159 / #175 / #192 / #212 / #239. **Not** a live product-JAR / live IdP query or withdraw walk. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | leftover-off | this leftover does not rerun #140 Zonky IT or #192/#212/#239 collect |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | `./scripts/g7-query-withdraw-leftover-honesty.sh` | No Docker / Compose |

## Commands

```bash
./scripts/g7-query-withdraw-leftover-honesty.sh
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

Do **not** treat parent leftover collect scripts as this leftover. Parent `--verify-report` on honest fixtures is harness evidence.

## Results (agent VM)

Filled after the leftover honesty remainder script on this VM at `2026-09-22T22:50:57Z`. Live output is gitignored under `.local/g7-query-withdraw-leftover-honesty/`. A sanitized copy is [`g7-query-withdraw-leftover-honesty/agent-vm.env`](./g7-query-withdraw-leftover-honesty/agent-vm.env). Walk JSON: [`../g7-query-withdraw-leftover-honesty/walk.json`](../g7-query-withdraw-leftover-honesty/walk.json).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live PASS / fake browser PASS / fake withdraw PASS / fake G7 close / redo rejected |
| #239 leftover cells | **PASS** (gate) | query surfaces PASS; live JAR/IdP SKIP; `g7_close=NOT_CLAIMED` |
| #212 leftover cells | **PASS** (gate) | stub walk PASS; live withdraw SKIP; `g7_close=NOT_CLAIMED` |
| #192 leftover cells | **PASS** (gate) | query surfaces PASS; live JAR/IdP SKIP; `g7_close=NOT_CLAIMED` |
| Expense query surface | **PASS** | #140 `G7ExpenseTravelQueryIT` contract still present |
| Travel query surface | **PASS** | #140 contract still present |
| Live product-JAR + live IdP query | **SKIP** (expected) | `live_jar_not_healthy` (`NEXUS_BASE_URL` unset) |
| Live browser query click | **SKIP** (expected) | still OPEN; leftover does not start Playwright |
| Live browser-visible withdraw | **SKIP** (expected) | stub walk is not a live walk |
| leftover rows kept | **true** | ALL sibling leftover rows including #239 / #212 / #192 / #259 / #249 / #240 / #254 / #236 / #251 / #243 / #241 |
| Product G7 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #103/#113/#140/#159/#175/#192/#212/#239 redo | **false** | withdraw + query leftovers not rewritten |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + live/Keycloak IdP HTTP query of expense / travel list / field-equals / detail / todo
- Live browser query click for expense / travel
- Live product-JAR + live/Keycloak IdP Playwright click of `data-testid=todo-withdraw`
- Three-type product G7 close
- Redo of #103 / #113 / #140 / #159 / #175 / #192 / #212 / #239
