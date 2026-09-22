# G5 leftover: PublishHook / real SchemaService DDL honesty evidence

Recorded on this branch after rebase onto `origin/main` `1c79ef3` (Merge #209 G9 Sentinel/Cluster remainder honesty after #214). **Not** a product G5 close. **Not** a redo of #94 evolve DDL abort. **Not** a redo of #145 mid-publish failure rollback. **Not** a redo of #171 browser leftover. **Not** a redo of #196 mid-publish honesty leftover. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md). Keep **all sibling leftover rows** in [docs/README.md](../README.md), including [g5-mid-publish-failure.md](../g5-mid-publish-failure.md) (#145), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), and [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x / 3.9.x** | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Live PG 15 / JAR / IdP / browser | not required | honest SKIP when unavailable |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
./scripts/g5-publishhook-ddl-honesty.sh
./scripts/g5-publishhook-ddl-honesty.sh --verify-report <out>/report.env
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-real-ddl-pass.env fail
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-publishhook-ddl-pass.env fail
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-memory-as-real-ddl.env fail
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-g5-close.env fail
./scripts/g5-publishhook-ddl-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-honesty/fixtures/fake-redone.env fail
```

Do **not** treat `./scripts/g5-form-publish-rollback.sh`, `./scripts/g5-browser-failed-publish.sh`, or `./scripts/g5-mid-publish-failure-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g5-publishhook-ddl-honesty/`. A sanitized copy is [`g5-publishhook-ddl-honesty/agent-vm.env`](./g5-publishhook-ddl-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake real-DDL PASS rejected / fake PublishHook PASS rejected / fake memory-as-real-DDL rejected / fake `g5_close=CLOSED` rejected / fake redo rejected | `2026-09-22T21:05:50Z` |
| #196 mid-publish memory harness | **PASS** (inventory) | in-memory abort-after-SchemaService; **not** real DDL |
| Memory harness is real DDL | **false** | honesty gate enforces `memory_harness_is_real_ddl=false` |
| ConfirmThenAbortEvolve is real DDL | **false** | wrapper is not SchemaService DDL |
| Zonky #94/#145 as this leftover PASS | **false** | those suites stay siblings |
| Real SchemaService DDL abort | **SKIP** (expected) | live PG 15 / observed CREATE `42P07` / evolve `23502` not present |
| PublishHook → real DDL | **SKIP** (expected) | product `SchemaServicePublishHook` → `ensurePhysicalTable` not observed |
| Product G5 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #94 / #145 / #171 / #196 | not redone | `redone_94=false`, `redone_145=false`, `redone_171=false`, `redone_196=false` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |
| Sibling leftover rows | kept | includes #196 + #197 `g9-redis-down-invalidate-remainder` + #204 `g2-h5-every-page-form-write-honesty` + #205 `g17-mfa-recovery-vue-otp-honesty` + #188 `g2-ops-pg-dump-honesty` + #209 `g9-sentinel-cluster-remainder-honesty` |

## Honest leftovers

- Live PG 15 + product `JdbcSchemaService` CREATE `42P07` / evolve `23502` abort
- Live `SchemaServicePublishHook` → real SchemaService DDL abort
- Live JAR + IdP + browser fail-click of `data-testid=form-publish`
- Product G5 close
- P4 / app-package publish / form designer
- Redo of #94 / #145 / #171 / #196
