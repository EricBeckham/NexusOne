# G2 leftover: form.write / POST submissions + submit-start dual-check honesty evidence

Recorded on this agent VM after rebase onto `origin/main` `183c837` (Merge #174; also #172 at `8b6acd0`). **Not** a product G2 close. **Not** a redo of #112 / #116 / #137 / #144 / #150 / #155 / #163. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g2-form-write-honesty.md](../g2-form-write-honesty.md). Keep both leftover rows: [g2-form-submission-write.md](../g2-form-submission-write.md) and [g2-submit-start-form-write.md](../g2-submit-start-form-write.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g2-form-write-honesty.sh
./scripts/g2-form-write-honesty.sh --verify-report <out>/report.env
./scripts/g2-form-write-honesty.sh --verify-report \
  scripts/g2-form-write-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-form-write-honesty.sh --verify-report \
  scripts/g2-form-write-honesty/fixtures/fake-live-jar-pass.env
./scripts/g2-form-write-honesty.sh --verify-report \
  scripts/g2-form-write-honesty/fixtures/fake-g2-close.env
./scripts/g2-form-write-honesty.sh --verify-report \
  scripts/g2-form-write-honesty/fixtures/fake-form-write-only.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g2-form-write-honesty/`. A sanitized copy is [`g2-form-write-honesty/agent-vm.env`](./g2-form-write-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g2_close=CLOSED` rejected / fake form.write-only rejected | `2026-09-22T19:44:54Z` |
| FormSurfaceGuard form.write | **PASS** (gate) | collect `--skip-tests` on this VM (`mvn` not on PATH) |
| submit-start dual-check | **PASS** (gate) | not `form.write` only |
| leftover rows kept | **true** | both README leftover rows stay |
| Live JAR / issued context | **SKIP** (expected) | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| live_submissions_observed | **false** | honesty gate requires `false` on SKIP |
| #112/#116/#137/#144/#150/#155/#163 redo | **false** | sibling leftovers |
| Product G2 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + issued-context POST `/submissions` and submit-start dual-check walk
- Product G2 close
- H5 home / files / notify / login / host SurfaceGuard (#163 inventory)
- Ops `pg_dump` (#155) / physical dump HTTP (#137) / todo HTTP (#150)
