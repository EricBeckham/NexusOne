# G5 leftover: browser failed-publish / PublishHook honesty evidence

Recorded on this branch after rebase onto `origin/main` `135ba83` (Merge #178 claim-list tip sync after #175 G7 withdraw-browser honesty leftover; includes #176 claim-list tip align and #173 G2 form-write honesty). **Not** a product G5 close. **Not** a redo of #94 evolve DDL abort. **Not** a redo of #145 mid-publish failure rollback. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g5-browser-failed-publish.md](../g5-browser-failed-publish.md). Keep both leftover rows in [docs/README.md](../README.md): [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175) and this leftover, plus [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), [g17-mfa-recovery-email-honesty.md](../g17-mfa-recovery-email-honesty.md) (#172), [g1-datascope-or-in-ast.md](../g1-datascope-or-in-ast.md) (#168), [g3-claim-crash-window.md](../g3-claim-crash-window.md) (#170), and [g5-mid-publish-failure.md](../g5-mid-publish-failure.md) (#145). Sibling leftovers stay documented: [g9-live-multi-jvm-replica.md](../g9-live-multi-jvm-replica.md) (#161), [g6-recovery-port-honesty.md](../g6-recovery-port-honesty.md) (#164), [g2-h5-every-page.md](../g2-h5-every-page.md) (#163), [linux-arm-profile-honesty.md](../linux-arm-profile-honesty.md) (#157), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), [g4-export-wizard-deny.md](../g4-export-wizard-deny.md) (#166), [g15-wx-dt-prod-gap-honesty.md](../g15-wx-dt-prod-gap-honesty.md) (#167).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 (or agent PATH) | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Live JAR / IdP / browser | not required | honest SKIP when unavailable |
| Runtime | local `mvn test` + collect script | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

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

Do **not** treat `./scripts/g5-form-publish-rollback.sh` as this leftover. That script is the #70 / #94 / #145 contract.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g5-browser-failed-publish/`. A sanitized copy is [`g5-browser-failed-publish/agent-vm.env`](./g5-browser-failed-publish/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Browser failed-publish click | **SKIP** (expected) | live browser/IdP/JAR unavailable |
| Product G5 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #94 / #145 | not redone | `g5_94_145_redone=false` |
| Publish-log as fail-click | **false** | UI 不再模拟失败 |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned |

## Honest leftovers

- Live JAR + IdP + browser click of `data-testid=form-publish` that hits PublishHook → real DDL abort
- Product G5 close
- P4 / app-package publish / form designer
