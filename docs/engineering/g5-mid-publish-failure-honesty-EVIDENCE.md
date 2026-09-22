# G5 leftover: mid-publish failure / browser failed-publish honesty evidence

Recorded on this branch after rebase onto `origin/main` `c8cd853` (Merge #195 g3-outbox-consume-crash-honesty after #191 / #198 / #200 / #199 / #192 / #193 / #190). **Not** a product G5 close. **Not** a redo of #94 evolve DDL abort. **Not** a redo of #145 mid-publish failure rollback. **Not** a redo of #171 browser leftover. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md). Keep **both** leftover rows in [docs/README.md](../README.md): [g5-mid-publish-failure.md](../g5-mid-publish-failure.md) (#145) and [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171). Keep ALL sibling leftover rows, including [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [g6-recovery-port-honesty.md](../g6-recovery-port-honesty.md) (#164), [g6-recovery-port-dry-run.md](../g6-recovery-port-dry-run.md) (#135), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x / 3.9.x** | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Live JAR / IdP / browser | not required | honest SKIP when unavailable |
| Runtime | local `mvn test` + collect script | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

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

Do **not** treat `./scripts/g5-form-publish-rollback.sh` or `./scripts/g5-browser-failed-publish.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g5-mid-publish-failure-honesty/`.

| Check | Result | Notes |
|---|---|---|
| Mid-publish abort-after-SchemaService harness | **PASS** | in-memory `SchemaServicePublishHook`; Running v1 + snapshot; `ROLLED_BACK`; no v2 |
| Browser failed-publish click | **SKIP** (expected) | live browser/IdP/JAR unavailable; #145 harness is not a live fail-click |
| Product G5 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #94 / #145 / #171 | not redone | `redone_94=false`, `redone_145=false`, `redone_171=false` |
| Publish-log as fail-click | **false** | UI 不再模拟失败 |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |
| Both leftover rows | kept | `g5-mid-publish-failure` + `g5-browser-failed-publish` |
| Sibling leftover rows | kept | includes #186 `g6-recovery-port-dry-run-honesty` + #190 `g4-p1-staging-audit-events-honesty` + #193 `linux-arm-profile-leftover-honesty` + #192 `g7-query-expense-travel-honesty` |

## Honest leftovers

- Live JAR + IdP + browser click of `data-testid=form-publish` that hits PublishHook → real DDL abort
- Product G5 close
- P4 / app-package publish / form designer
