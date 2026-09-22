# G2 leftover — form.write / POST submissions + submit-start dual-check honesty

S0+S1 remainder after [g2-form-submission-write.md](./g2-form-submission-write.md) (#106) and [g2-submit-start-form-write.md](./g2-submit-start-form-write.md) (#112). Rebased onto `origin/main` `e63d918` (after #168). **G18 stays closed.** This leftover does **not** redo #112 / #116 / #137 / #144 / #150 / #155 / #163. It does **not** claim product G2 close. `g2_close=NOT_CLAIMED`.

Keep both leftover rows in [docs/README.md](./README.md): `g2-form-submission-write` and `g2-submit-start-form-write`. This file is the honesty harness around those two leftovers.

#106 already put `POST /api/v1/p3/forms/{id}/submissions` through `FormSurfaceGuard.requireFill` (`form` / `write`). #112 already dual-checks submit-start when `form_id` is present (`form.write` then `process.instance` / `start`). The leftover here is the **honesty harness**: in-process FormSurfaceGuard + dual-check stay **PASS**. Live product-JAR + issued-context HTTP is **PASS** only when the endpoint is up **and** both walks were observed. Missing `NEXUS_BASE_URL` / issued context or a down listener is honest **SKIP**. It never fabricates `PASS`. Submit-start is **not** `form.write` only.

## What this leftover covers

| Path | Behavior |
|---|---|
| Form fill / POST submissions | `FormSurfaceGuard.requireFill` is `form` / `write`. `g2_self_reader` denies fill; `g2_form_designer` allows. |
| submit-start dual-check | `form.write` **and** `process.instance` / `start` when `form_id` is present. Approver start-only cannot fill. Designer fill-only cannot start. |
| Leftover rows | `docs/README.md` keeps both #106 and #112 leftover rows. |
| Live JAR inventory | Probe `NEXUS_BASE_URL` / `NEXUS_G2_LIVE_BASE` + loopback `:8080` / `:8088` + `NEXUS_G2_LIVE_CONTEXT_ID`. |
| Honest SKIP | Unset live base, missing issued context, down JAR, or no observed walk stays `SKIP` with a reason. |
| Honesty gate | `--verify-report` rejects `live_jar_walk=PASS` without present + observed submissions/submit-start, rejects `g2_close=CLOSED`, rejects `submit_start_is_form_write_only=true`, and rejects #112/#116/#137/#144/#150/#155/#163 redo flags. |

Reuse only: existing `FormSurfaceGuard` and `G2SurfaceAgreementTest` dual-check. Java 21 / Boot **4.1.1**. No Docker. Does not re-run `G2SubmitStartFormWriteIT` (Zonky / #112).

## How to run

```bash
# Always-green FormSurfaceGuard + dual-check + live probe (honest SKIP when JAR is down)
./scripts/g2-form-write-honesty.sh

# Honesty gate only
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

Equivalent tests:

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=G2FormWriteHonestyTest,FormSurfaceGuardTest,G2SurfaceAgreementTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

#112 / #106 stay the reproduction commands for the original leftover ITs. This leftover does not invoke them:

```bash
# do not run for this leftover
# ./scripts/g2-submit-start-form-write.sh
```

If a live product JAR is later listening (native process, not Docker):

```bash
export NEXUS_BASE_URL=http://127.0.0.1:8080
export NEXUS_G2_LIVE_CONTEXT_ID=...   # issued X-Nexus-Context-Id; env / Secret only
./scripts/g2-form-write-honesty.sh
```

`PASS` requires all of: reachable product JAR, issued context, observed `POST /api/v1/p3/forms/{id}/submissions`, and observed submit-start dual-check. This leftover does not start the JAR.

See [engineering/g2-form-write-honesty-EVIDENCE.md](./engineering/g2-form-write-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g2-form-write-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product-wide G2 | Explicitly `g2_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| submit-start is `form.write` only | Dual-check. Process start stays required. |
| #112 submit-start Zonky IT | Sibling leftover on `main`. Not rewritten. |
| H5 `/m/*` (#116) / every-page (#163) | Sibling leftovers. Not rewritten. |
| Physical dump (#137) / tenant_only catalog (#144) / todo HTTP (#150) / ops `pg_dump` (#155) | Sibling leftovers. Not rewritten. |
| Live JAR + issued-context HTTP on this agent VM | Base URL unset; nothing on `:8080` / `:8088`. Honesty SKIP. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Docker / Compose | Forbidden. Native JAR only when the operator already started it. |

## Honest leftovers (not claimed)

- Live product-JAR + issued-context POST `/submissions` and submit-start dual-check walk
- Product-wide G2 close
- Putting SurfaceGuard on H5 home / files / notify / login / host
- Ops-level `pg_dump` through SurfaceGuard
