# G2 leftover: H5 every-page / form.write honesty evidence

Recorded on this branch after rebase onto `origin/main` `cb0f339` (Merge #206 tip-sync after #203). **Not** a product G2 close. **Not** a redo of #112 / #116 / #137 / #144 / #150 / #155 / #163 / #173. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md). Keep **all sibling leftover rows** in [docs/README.md](../README.md), including [g2-h5-every-page.md](../g2-h5-every-page.md) (#163), [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), and [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
./scripts/g2-h5-every-page-form-write-honesty.sh
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report <out>/report.env
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-g2-close.env fail
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-live-jar-pass.env fail
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-redone.env fail
./scripts/g2-h5-every-page-form-write-honesty.sh --verify-report \
  scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-h5-open-closed.env fail
```

Do **not** treat `./scripts/g2-h5-every-page.sh` or `./scripts/g2-form-write-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g2-h5-every-page-form-write-honesty/`. A sanitized copy is [`g2-h5-every-page-form-write-honesty/agent-vm.env`](./g2-h5-every-page-form-write-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g2_close=CLOSED` rejected / fake redo rejected / fake H5-open-closed rejected | `2026-09-22T20:51:49Z` |
| #163 H5 every-page harness | **PASS** | inventory still COVERED list/detail/form/todo/query; OPEN home/files/notify/login/host |
| #173 form.write harness | **PASS** | parent honest-skip fixture still verifies |
| FormSurfaceGuard form.write | **PASS** (gate) | not rewritten |
| submit-start dual-check | **PASS** (gate) | not `form.write` only |
| leftover rows kept | **true** | parents + #191 BoundSql honesty + #195 G3 crash honesty + #196 G5 mid-publish honesty + #202 G10 leftover honesty + #203 G4 export-wizard deny honesty + all sibling leftover rows |
| Live JAR / issued context | **SKIP** (expected) | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| live H5 every-page walk | **SKIP** (expected) | no live JAR / issued context |
| #112/#116/#137/#144/#150/#155/#163/#173 redo | **false** | sibling leftovers |
| Product G2 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + issued-context POST `/submissions` and submit-start dual-check walk
- Live H5 every-page SurfaceGuard browser walk
- Product G2 close
- H5 home / files / notify / login / host SurfaceGuard (#163 inventory)
- Ops `pg_dump` (#155) / physical dump HTTP (#137) / todo HTTP (#150)
