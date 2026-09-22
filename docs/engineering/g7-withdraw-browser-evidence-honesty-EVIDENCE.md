# G7 leftover: browser-visible withdraw evidence honesty

Recorded on this branch after rebase onto `origin/main` `86c33a8` (Merge #211 tip-sync after #205 G17 MFA recovery/OTP honesty; includes #197 / #204). **Not** a product G7 close. **Not** a redo of #82 / #103 / #113 / #140 / #159 / #175 / #192. **Not** a live product-JAR / live IdP withdraw click. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md), including [g7-withdraw-evidence.md](../g7-withdraw-evidence.md) (#82), [g7-withdraw-expense-travel.md](../g7-withdraw-expense-travel.md) (#103), [g7-withdraw-browser.md](../g7-withdraw-browser.md) (#113/#159), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), and [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
./scripts/g7-withdraw-browser-evidence-honesty.sh
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report <out>/report.env
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-live-pass.env fail
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-g7-close.env fail
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-redone.env fail
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-stub-as-live.env fail
```

Do **not** treat `./scripts/g7/run-withdraw-browser.sh` or `./scripts/g7-withdraw-browser-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g7-withdraw-browser-evidence-honesty/`. A sanitized copy is [`g7-withdraw-browser-evidence-honesty/agent-vm.env`](./g7-withdraw-browser-evidence-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g7_close=CLOSED` rejected / fake redo rejected / fake stub-as-live rejected | `2026-09-22T21:01:14Z` after rebase onto `86c33a8` |
| Leave / expense / travel withdraw HTTP | **PASS** (gate) | #82/#103 leftover docs still present; not rerun |
| Vue `todo-withdraw` + HTTP path | **PASS** | #113/#159 control still present |
| #175 leftover honesty | **PASS** | parent honest-skip fixture still verifies |
| #192 query leftover honesty | **PASS** | sibling leftover; withdraw paths not redone |
| Browser-visible stub walk | **PASS** | leave + expense + travel; stub ≠ live |
| Screenshot artifacts | **PASS** | stub-login / 我发起 / todo-withdraw / WITHDRAWN |
| leftover rows kept | **true** | parents + #205 G17 MFA recovery/OTP honesty + #197 G9 Redis-down remainder + #204 G2 H5/form.write honesty + #211 tip-sync + all sibling leftover rows |
| AuditPort same-TX | **PASS** | untouched |
| Live JAR/IdP browser withdraw | **SKIP** (expected) | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| Live browser-visible withdraw | **SKIP** (expected) | stub walk is not a live walk |
| Live browser query click | **SKIP** (expected) | still open; not this leftover |
| #82/#103/#113/#140/#159/#175/#192 redo | **false** | sibling leftovers |
| Product G7 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + live/Keycloak IdP Playwright click of `todo-withdraw`
- Live browser-visible withdraw on leave + expense + travel
- Live browser query click for expense / travel
- Three-type product G7 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
