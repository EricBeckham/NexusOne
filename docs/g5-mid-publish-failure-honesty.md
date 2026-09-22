# G5 leftover — mid-publish failure / browser failed-publish honesty beyond #145/#171

S0+S1 remainder after the #145 mid-publish failure leftover ([g5-mid-publish-failure.md](./g5-mid-publish-failure.md)) and the #171 browser failed-publish honesty leftover ([g5-browser-failed-publish.md](./g5-browser-failed-publish.md)). Rebased onto `origin/main` `c8cd853` (Merge #195 g3-outbox-consume-crash-honesty after #191 / #198 / #200 / #199 / #192 / #193 / #190). **G18 stays closed.** This leftover does **not** redo #94 evolve DDL abort. It does **not** redo #145 Zonky / HTTP / memory ITs. It does **not** redo #171 leftover. It does **not** change `FormService` / `SchemaService` / `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim product G5 close. P0/M0 are **not** redone.

#145 already proved CREATE abort / SchemaService-then-abort keeps `form.running_version`, the RUNNING row, and `snapshot_json` unchanged and writes `form_publish_log.outcome=ROLLED_BACK`. #171 already inventoried a live browser fail-click as honest SKIP. The leftover here is the **honesty gate**: the in-memory abort-after-SchemaService harness stays always-green **PASS**. A live browser / IdP / JAR failed-publish click is **PASS** only when those surfaces are present **and** a fail-click through `SchemaServicePublishHook` toward real DDL was observed. Missing live browser/IdP/JAR is honest **SKIP**. It never fabricates `PASS`.

Keep both leftover rows in [docs/README.md](./README.md): `g5-mid-publish-failure` (#145) and `g5-browser-failed-publish` (#171). Keep ALL sibling leftover rows (including `g6-recovery-port-dry-run-honesty` / `g4-p1-staging-audit-events-honesty` / `linux-arm-profile-leftover-honesty` / `g7-query-expense-travel-honesty` / `g6-recovery-port-honesty` / `g6-recovery-port-dry-run` / `s0s1-live-redis-minio-isolation-honesty` / `g17-separation-honesty` / `g4-hot-purge-90d-honesty` / `g1-fieldpolicy-mask-second-auth-honesty` / `g10-identity-drift-honesty` / `g15-native-bridge-honesty` / `g15-wx-dt-native-bridge-honesty` / `g1-queryast-boundsql-honesty` / `g3-outbox-consume-crash-honesty` / `g4-pkce-spa-verifier-honesty` / `s0-secret-getenv-snapshot-honesty` / `s0-secret-rotate-without-restart-honesty` / `g9-redis-down-invalidate-honesty` / `g10-file-reconcile-honesty` / `g7-withdraw-browser-honesty` / `g2-form-write-honesty`). PublishHook / real SchemaService DDL honesty leftover beyond this file is [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md). Publish remainder leftover beyond #217 is [g5-publishhook-ddl-remainder-honesty.md](./g5-publishhook-ddl-remainder-honesty.md). This file is the honesty leftover beside them.

## What this leftover covers

| Path | Behavior |
|---|---|
| #145 mid-publish contract | Already on `main`. CREATE abort / SchemaService-then-abort / HTTP leftover stay on that leftover. Do **not** redo Zonky ITs. |
| #171 browser leftover | Already on `main`. Do **not** redo. |
| Mid-publish harness | Always-green `mid_publish_harness=PASS`. In-memory `SchemaServicePublishHook` finishes `ensurePhysicalTable`, then aborts. Running pointer + snapshot stay v1; `ROLLED_BACK`; no v2 `form_version`. |
| Live browser fail-click | `browser_failed_publish=SKIP` unless live JAR + IdP + browser + observed fail-click + PublishHook DDL + mid-fail abort honesty. |
| Publish-log viewer | Still **not** a fail-click. `simulate_publish_log_is_fail_click=false`. |
| Honesty gate | `--verify-report` rejects fake browser PASS, rejects `g5_close=CLOSED`, rejects #94/#145/#171 redo flags, and requires both leftover rows kept. |

No Docker. Live browser / IdP / JAR are **not** required. Do **not** run `./scripts/g5-form-publish-rollback.sh` or `./scripts/g5-browser-failed-publish.sh` as this leftover.

## How to run

```bash
./scripts/g5-mid-publish-failure-honesty.sh
./scripts/g5-mid-publish-failure-honesty.sh --verify-report <out>/report.env
./scripts/g5-mid-publish-failure-honesty.sh --verify-report \
  scripts/g5-mid-publish-failure-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g5-mid-publish-failure-honesty.sh --verify-report \
  scripts/g5-mid-publish-failure-honesty/fixtures/fake-browser-pass.env
./scripts/g5-mid-publish-failure-honesty.sh --verify-report \
  scripts/g5-mid-publish-failure-honesty/fixtures/fake-g5-close.env
```

Always-green Java (does **not** rerun #94 / #145 Zonky ITs or #171 leftover):

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=G5MidPublishFailureHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The existing leftover scripts stay as reproduction commands:

```bash
# do not run for this leftover
# ./scripts/g5-form-publish-rollback.sh
# ./scripts/g5-browser-failed-publish.sh
```

Java **21** / Boot **4.1.1**. No Docker. Live browser / IdP / JAR are **not** required.

See [engineering/g5-mid-publish-failure-honesty-EVIDENCE.md](./engineering/g5-mid-publish-failure-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g5-mid-publish-failure-honesty/`. Walk JSON: [g5-mid-publish-failure-honesty/walk.json](./g5-mid-publish-failure-honesty/walk.json).

## What this does not claim

| Leftover | Why |
|---|---|
| Product G5 close | Explicitly `g5_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live browser failed-publish click | Missing live browser / IdP / JAR → honest SKIP. |
| Redo of #94 evolve DDL abort | `redone_94=false`. |
| Redo of #145 mid-publish failure rollback | `redone_145=false`. Zonky ITs stay on that leftover. |
| Redo of #171 browser leftover | `redone_171=false`. |
| Publish-log as fail-click | `simulate_publish_log_is_fail_click=false`. |
| P4 / app-package publish / form designer | Out of this leftover. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- A live JAR + IdP + browser click of `data-testid=form-publish` that actually hits PublishHook → real DDL abort
- Product G5 close
- P4 / app-package publish
- Form designer product
- Redo of #94 / #145 / #171
