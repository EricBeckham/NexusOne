# G7 leftover: expense/travel list/detail/todo query honesty remainder beyond #192/#140/#212

See [docs/g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g7-query-expense-travel.md](../g7-query-expense-travel.md) (#140), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), this leftover, [g7-withdraw-browser.md](../g7-withdraw-browser.md) (#113/#159), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g7-withdraw-expense-travel.md](../g7-withdraw-expense-travel.md) (#103), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), and the other leftover rows already on `main`.

Recorded on this branch after rebase onto `origin/main` `c8777b6` (Merge #237 G9 Redis-down/invalidate leftover honesty after #232 G2 H5/form.write leftover honesty after #212). **Not** a product G7 close. **Not** a redo of #103 / #113 / #140 / #159 / #175 / #192 / #212. **Not** a live product-JAR / live IdP query walk. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | leftover-off | this leftover does not rerun #140 Zonky IT or #192 collect |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | `./scripts/g7-query-expense-travel-honesty-remainder.sh` | No Docker / Compose |

## Commands

```bash
./scripts/g7-query-expense-travel-honesty-remainder.sh
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

Do **not** treat `./scripts/g7/run-query-expense-travel.sh` or `./scripts/g7-query-expense-travel-honesty.sh` as this leftover. Those scripts are the #140 / #192 contracts. Parent `--verify-report` on honest fixtures is harness evidence.

## Results (agent VM)

Filled after the leftover honesty remainder script on this VM at `2026-09-22T21:52:25Z`. Live output is gitignored under `.local/g7-query-expense-travel-honesty-remainder/`. A sanitized copy is [`g7-query-expense-travel-honesty-remainder/agent-vm.env`](./g7-query-expense-travel-honesty-remainder/agent-vm.env) once recorded. Walk JSON: [`../g7-query-expense-travel-honesty-remainder/walk.json`](../g7-query-expense-travel-honesty-remainder/walk.json).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live PASS / fake browser PASS / fake G7 close / redo rejected (`2026-09-22T21:52:25Z`) |
| #140 leftover cells | **PASS** (gate) | expense + travel list/detail/todo contract still present |
| #192 leftover cells | **PASS** (gate) | query surfaces PASS; live JAR/IdP SKIP; `g7_close=NOT_CLAIMED` |
| #212 leftover cells | **PASS** (gate) | stub walk PASS; live withdraw SKIP; `g7_close=NOT_CLAIMED` |
| Expense query surface | **PASS** | #140 `G7ExpenseTravelQueryIT` contract still present |
| Travel query surface | **PASS** | #140 contract still present |
| Live product-JAR + live IdP query | **SKIP** (expected) | `live_jar_not_healthy` (`NEXUS_BASE_URL` unset) |
| Live browser query click | **SKIP** (expected) | still OPEN; leftover does not start Playwright |
| leftover rows kept | **true** | ALL sibling leftover rows including #192 / #212 / #230 / #232 / #237 |
| Product G7 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #103/#113/#140/#159/#175/#192/#212 redo | **false** | withdraw + query leftovers not rewritten |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + live/Keycloak IdP HTTP query of expense / travel list / field-equals / detail / todo
- Live browser query click for expense / travel
- Three-type product G7 close
- Redo of #103 / #113 / #140 / #159 / #175 / #192 / #212
