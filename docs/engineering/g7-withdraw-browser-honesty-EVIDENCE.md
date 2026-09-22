# G7 leftover: Vue todo-withdraw / browser evidence honesty

Recorded on this branch after rebase onto `origin/main` `183c837` (Merge #174 G9 Redis-down invalidate honesty leftover). **Not** a product G7 close. **Not** a redo of #103 / #113 / #140 / #159. **Not** a live product-JAR / live IdP withdraw click. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md). Keep both leftover rows in [docs/README.md](../README.md): [g7-withdraw-browser.md](../g7-withdraw-browser.md) (#113/#159) and [g7-query-expense-travel.md](../g7-query-expense-travel.md) (#140). Sibling leftovers (#170 claim-then-write crash window, #164 RecoveryPort honesty, #161 live multi-JVM, #169 file-reconcile honesty, #167 WeChat/DingTalk prod-gap) stay documented.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 (filled after collect) | product baseline; leftover does not boot the JAR |
| Node | 22 (filled after collect) | always-green Vitest + probe |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | `./scripts/g7-withdraw-browser-honesty.sh` | No Docker / Compose. No live JAR / IdP |

## Commands

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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g7-withdraw-browser-honesty/`. A sanitized copy is [`g7-withdraw-browser-honesty/walk.json`](../g7-withdraw-browser-honesty/walk.json).

| Check | Result | Notes |
|---|---|---|
| #113/#159 stub Vue + HTTP reachability | unchanged | not redone |
| Live product-JAR + live IdP browser withdraw | **SKIP** (expected) | `NEXUS_BASE_URL` unset / no issued context / no live IdP |
| Fake live PASS / product G7 close | rejected | honesty gate |
| Both leftover rows | kept | `g7-withdraw-browser` + `g7-query-expense-travel` |
| Product G7 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + live/Keycloak IdP Playwright click of `todo-withdraw`
- Live browser query click for expense / travel
- Three-type product G7 close
