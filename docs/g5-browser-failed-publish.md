# G5 leftover — browser failed-publish click / PublishHook honesty

S0+S1 remainder after the G5 forms package already on `main` (#70 validate/hook, #85 SchemaService gate, #94 product CREATE/evolve + evolve DDL abort, #145 mid-publish failure rollback). **Not** a P0/M0 redo. Product **G18 stays closed**. `AuditPort` same-TX joining is unchanged.

This leftover is **honesty only** for a **browser failed-publish click** through the product `PublishHook` toward real SchemaService DDL. Mid-fail / abort (Running pointer + snapshot unchanged, `form_publish_log.outcome=ROLLED_BACK`) stays the #94 / #145 contract. This leftover does **not** redo those suites.

This leftover does **not** claim product G5 close (`g5_close=NOT_CLAIMED`). A live browser/IdP/JAR walk is an honest **SKIP** when those surfaces are unavailable. The UI publish-log viewer is **not** a failed-publish click.

Keep both leftover rows in [docs/README.md](./README.md): [g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) (#175) and this file, plus [g2-form-write-honesty.md](./g2-form-write-honesty.md) (#173), [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) (#174), [g17-mfa-recovery-email-honesty.md](./g17-mfa-recovery-email-honesty.md) (#172), [g1-datascope-or-in-ast.md](./g1-datascope-or-in-ast.md) (#168), [g3-claim-crash-window.md](./g3-claim-crash-window.md) (#170), and [g5-mid-publish-failure.md](./g5-mid-publish-failure.md) (#145). PASS/SKIP honesty leftover beyond #145/#171 is [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md). PublishHook / real SchemaService DDL honesty leftover beyond #196/#171/#145 is [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md). Publish remainder leftover beyond #217 is [g5-publishhook-ddl-remainder-honesty.md](./g5-publishhook-ddl-remainder-honesty.md). Also keep sibling leftover rows [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md) (#186), [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) (#161), [g6-recovery-port-honesty.md](./g6-recovery-port-honesty.md) (#164), [g2-h5-every-page.md](./g2-h5-every-page.md) (#163), [linux-arm-profile-honesty.md](./linux-arm-profile-honesty.md) (#157), [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) (#169), [g4-export-wizard-deny.md](./g4-export-wizard-deny.md) (#166), and [g15-wx-dt-prod-gap-honesty.md](./g15-wx-dt-prod-gap-honesty.md) (#167).

## What this leftover covers

| Surface | Behavior |
|---|---|
| Product click | Vue `data-testid=form-publish` → `POST /api/v1/p3/forms/{id}/publish` → `SchemaServicePublishHook.beforeActivate` → `SchemaService.ensurePhysicalTable` (CREATE / evolve / confirm). DDL still only by SchemaService. |
| Mid-fail / abort honesty | If that click fails mid-publish, Running pointer + snapshot stay unchanged and `ROLLED_BACK` is written. Evidence of that contract stays on #94 / #145. This leftover does **not** rerun those ITs. |
| Publish-log viewer | `data-testid=form-publish-log` / `simulatePublishFailure` only reads `GET …/publish-log`. `rolled_back=false`. UI 不再模拟失败. That is **not** a failed-publish click. |
| Live probe | JAR `/actuator/health`, IdP OIDC discovery, system Chrome. Missing any → honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake `PASS` without live JAR + IdP + browser + observed fail-click + PublishHook DDL + mid-fail abort honesty. Rejects `g5_close=CLOSED`. Rejects treating publish-log as a fail-click. |

`PASS` requires all of: live JAR, live IdP, live browser, observed failed Publish click, observed PublishHook → real DDL path, and mid-fail/abort still honest. This leftover does not start those surfaces.

## How to run

Baseline: Java 21, Spring Boot **4.1.1**, no Docker. Live browser / IdP / JAR are **not** required.

```bash
./scripts/g5-browser-failed-publish.sh
./scripts/g5-browser-failed-publish.sh --verify-report <out>/report.env
./scripts/g5-browser-failed-publish.sh --verify-report \
  scripts/g5-browser-failed-publish/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g5-browser-failed-publish.sh --verify-report \
  scripts/g5-browser-failed-publish/fixtures/fake-browser-pass.env
./scripts/g5-browser-failed-publish.sh --verify-report \
  scripts/g5-browser-failed-publish/fixtures/fake-g5-close.env
./scripts/g5-browser-failed-publish.sh --verify-report \
  scripts/g5-browser-failed-publish/fixtures/fake-publish-log-click.env
```

Always-green Java (does **not** redo #94 / #145 suites):

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=G5BrowserFailedPublishHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Do **not** run `./scripts/g5-form-publish-rollback.sh` as this leftover — that is the #70 / #94 / #145 contract.

See [engineering/g5-browser-failed-publish-EVIDENCE.md](./engineering/g5-browser-failed-publish-EVIDENCE.md).

## Evidence

Fill from `./scripts/g5-browser-failed-publish.sh`. Live `report.env` is gitignored under `.local/g5-browser-failed-publish/`. Artifact: [g5-browser-failed-publish/walk.json](./g5-browser-failed-publish/walk.json).

| Item | Value |
|---|---|
| JDK / Boot | OpenJDK **21** / Spring Boot **4.1.1** |
| Live JAR / IdP | probe only; this leftover does not boot them |
| Browser click | honest **SKIP** unless observed |
| `#94` / `#145` | **not redone** |
| `AuditPort` | **untouched** |
| G18 | **stays closed** |
| Docker | no |

## What this does not claim

| Leftover | Why |
|---|---|
| Product G5 close | Explicitly `g5_close=NOT_CLAIMED`. |
| Live browser failed-publish click | Missing live browser / IdP / JAR → honest SKIP. |
| Redo of #94 evolve DDL abort | `g5_94_145_redone=false`. |
| Redo of #145 mid-publish failure rollback | Same. Mid-fail / abort honesty stays on that leftover. |
| Publish-log as fail-click | `simulate_publish_log_is_fail_click=false`. |
| P4 / app-package publish / form designer | Out of this leftover. |
| G18 reopen / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- A live JAR + IdP + browser click of `data-testid=form-publish` that actually hits PublishHook → real DDL abort
- Product G5 close
- P4 / app-package publish
- Form designer product
