# G2 leftover: H5 every-page / form.write / report physical-dump remainder honesty evidence

Recorded on this branch after rebase onto `origin/main` `971b648` (Merge #236 Linux/ARM profile leftover honesty after #251). **Not** a product G2 close. **Not** a redo of #112 / #116 / #122 / #124 / #137 / #144 / #150 / #155 / #163 / #173 / #204 / #221 / #232. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md). Keep **all sibling leftover rows** in [docs/README.md](../README.md), including [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g2-h5-every-page.md](../g2-h5-every-page.md) (#163), [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), and [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x / 3.9.x** | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-g2-close.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-live-jar-pass.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-live-h5-pass.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-live-report-pass.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-live-dump-pass.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-232-as-live.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-221-as-live.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-redone.env fail
./scripts/g2-h5-every-page-form-write-report-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-report-remainder-honesty/fixtures/fake-report-is-dump.env fail
```

Do **not** treat `./scripts/g2-h5-every-page.sh`, `./scripts/g2-form-write-honesty.sh`, `./scripts/g2-h5-every-page-form-write-honesty.sh`, `./scripts/g2-h5-every-page-form-write-remainder-honesty.sh`, or `./scripts/g2-report-physical-dump-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM (`971b648` rebase). Live output is gitignored under `.local/g2-h5-every-page-form-write-report-remainder-honesty/`. A sanitized copy is [`g2-h5-every-page-form-write-report-remainder-honesty/agent-vm.env`](./g2-h5-every-page-form-write-report-remainder-honesty/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live JAR / H5 / report / dump PASS, fake #232-as-live, fake #221-as-live, fake `g2_close=CLOSED`, fake #232/#221 redo, and fake report-is-dump rejected |
| #232 remainder contract | **PASS** (inventory) | **not** live JAR / live H5 / live report / live dump |
| #221 report/dump harness | **PASS** (inventory) | **not** live report / dump / H5 / form.write |
| #204 harness contract | **PASS** (inventory) | **not** live JAR / live H5 |
| leftover rows kept | **true** | parents + #236 Linux/ARM remainder + #251 G4 hot-purge/staging/PKCE remainder + #243 G10 file/object remainder + #241 SecretPort leftover + #235 G5 PublishHook remainder + all sibling leftover rows |
| Live JAR / issued context | **SKIP** (expected) | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| live H5 every-page walk | **SKIP** (expected) | no live JAR / issued context |
| live report / dump walk | **SKIP** (expected) | no live JAR / issued context |
| Product G2 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #163 / #173 / #204 / #221 / #232 | not redone | `redone_163=false`, `redone_173=false`, `redone_204=false`, `redone_221=false`, `redone_232=false` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live product-JAR + issued-context POST `/submissions` and submit-start dual-check walk
- Live H5 every-page SurfaceGuard browser walk
- Live product-JAR + issued-context report query/export walk
- Live product-JAR + issued-context physical dump / raw export walk
- Product G2 close
- H5 home / files / notify / login / host SurfaceGuard (#163 inventory)
- Treating #232 / #221 / #204 harness PASS as a live walk
- Redo of #163 / #173 / #204 / #221 / #232
