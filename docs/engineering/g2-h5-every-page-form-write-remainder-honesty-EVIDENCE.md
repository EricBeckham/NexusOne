# G2 leftover: H5 every-page / form.write remainder honesty evidence

Recorded on this branch after rebase onto `origin/main` `c3ffd50` (Merge #212 G7 withdraw browser-evidence leftover honesty after #230). **Not** a product G2 close. **Not** a redo of #112 / #116 / #137 / #144 / #150 / #155 / #163 / #173 / #204. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md). Keep **all sibling leftover rows** in [docs/README.md](../README.md), including [g2-h5-every-page.md](../g2-h5-every-page.md) (#163), [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), and [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217).

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
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-g2-close.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-live-jar-pass.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-live-h5-pass.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-harness-as-live.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-redone.env fail
./scripts/g2-h5-every-page-form-write-remainder-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-remainder-honesty/fixtures/fake-h5-open-closed.env fail
```

Do **not** treat `./scripts/g2-h5-every-page.sh`, `./scripts/g2-form-write-honesty.sh`, or `./scripts/g2-h5-every-page-form-write-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM at `2026-09-22T21:49:26Z`. Live output is gitignored under `.local/g2-h5-every-page-form-write-remainder-honesty/`. A sanitized copy is [`g2-h5-every-page-form-write-remainder-honesty/agent-vm.env`](./g2-h5-every-page-form-write-remainder-honesty/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live JAR / live H5 PASS, fake harness-as-live, fake `g2_close=CLOSED`, fake #204 redo, and fake H5-open-covered rejected (`2026-09-22T21:45:55Z`) |
| #204 harness contract | **PASS** (inventory) | **not** live JAR / live H5 |
| #204 harness is live JAR | **false** | honesty gate enforces `harness_204_is_not_live_jar=true` |
| #204 harness is live H5 | **false** | honesty gate enforces `harness_204_is_not_live_h5=true` |
| #163 H5 every-page harness | **PASS** | COVERED list/detail/form/todo/query; OPEN home/files/notify/login/host |
| #173 form.write harness | **PASS** | parent honest-skip fixture still verifies |
| leftover rows kept | **true** | parents + #221 + #228 G10 remainder + #226 G3 leftover + #229 G9 remainder + #230 G4 leftover + #212 G7 leftover + #225 G6 remainder + #223/#222/#216/#220/#215/#210/#217 + all sibling leftover rows |
| Live JAR / issued context | **SKIP** (expected) | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| live H5 every-page walk | **SKIP** (expected) | no live JAR / issued context |
| Product G2 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #163 / #173 / #204 | not redone | `redone_163=false`, `redone_173=false`, `redone_204=false` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live product-JAR + issued-context POST `/submissions` and submit-start dual-check walk
- Live H5 every-page SurfaceGuard browser walk
- Product G2 close
- H5 home / files / notify / login / host SurfaceGuard (#163 inventory)
- Treating #204 harness PASS as a live JAR / live H5 walk
- Redo of #163 / #173 / #204
