# G2 leftover — report query/export + physical dump SurfaceGuard honesty beyond #124/#137

S0+S1 remainder after the #124 report query/export SurfaceGuard leftover ([g2-report-surface-guard.md](./g2-report-surface-guard.md)) and the #137 physical dump / raw export leftover ([g2-physical-dump-surface.md](./g2-physical-dump-surface.md)). Rebased onto `origin/main` `bd00ca3` (Merge #224 claim-list tip after #223 / #222 / #216 / #218). **This does not close product G2.** **G18 stays closed.** This leftover does **not** redo #122 SurfaceGuard bypass CI, #124 report HTTP, #137 dump HTTP, or H5 form.write leftovers (#163/#173/#204). It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0 / M0. `g2_close=NOT_CLAIMED`. `redone_122=false`. `redone_124=false`. `redone_137=false`.

#124 already wired report query (`ledger`/`read`) and report export (`ledger.export.csv`/`submit`) through `ReportSurfaceGuard` then `AuthorizedExport.submit`. #137 already wired physical dump / raw export through `DumpSurfaceGuard` then `AuthorizedExport.writeProjected`. The leftover here is the **honesty gate**: leftover harness contracts stay always-green **PASS**. Live product-JAR + issued-context HTTP is **PASS** only when a healthy JAR, issued context, and observed report query/export plus dump walks exist. Missing JAR/context is honest SKIP. It never fabricates `PASS`. Live JAR walks that were not observed stay SKIP. Report export is **not** a physical dump. Dump is **not** report Task submit.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g2-report-surface-guard](./g2-report-surface-guard.md) (#124), [g2-physical-dump-surface](./g2-physical-dump-surface.md) (#137), [g2-surfaceguard-bypass-ci](./g2-surfaceguard-bypass-ci.md) (#122), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g2-form-write-honesty](./g2-form-write-honesty.md) (#173), [g2-h5-every-page](./g2-h5-every-page.md) (#163), [g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md) (#188), [g2-ops-pg-dump](./g2-ops-pg-dump.md) (#155), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), and every other leftover row already on `main`. This file is the honesty leftover beside them.

Baseline: Java 21, Spring Boot **4.1.1**. No Docker.

## What this leftover covers

| Path | Behavior |
|---|---|
| #124 report SurfaceGuard | Already on `main`. `GET/POST /api/v1/p3/reports/*` via `ReportSurfaceGuard` (`ledger/read` + `ledger.export.csv/submit`) then `AuthorizedExport.submit`. Do **not** rewrite `ReportSurfaceGuard` / `ReportController`. |
| #137 dump SurfaceGuard | Already on `main`. `GET/POST /api/v1/p3/dumps/*` via `DumpSurfaceGuard` then `AuthorizedExport.writeProjected`. Do **not** rewrite `DumpSurfaceGuard` / `DumpController`. |
| Leftover harness contract | Always-green `report_surface_harness=PASS` and `dump_surface_harness=PASS` when the #124/#137 leftover docs and guards still classify the same cells. |
| Honest live JAR SKIP | `live_jar_walk` / `live_report_walk` / `live_dump_walk` are **PASS** only when a live JAR was present **and** the walks were observed. Missing `NEXUS_BASE_URL` / issued context is honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g2_close=CLOSED`, rejects #122/#124/#137/#163/#173/#204 redo flags, rejects treating report as a physical dump, and requires sibling leftover rows kept. |

No Docker. No product-JAR Playwright driver. Parent leftovers stay siblings.

## How to run

```bash
./scripts/g2-report-physical-dump-honesty.sh
./scripts/g2-report-physical-dump-honesty.sh --verify-report <out>/report.env
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/fake-g2-close.env fail
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/fake-live-jar-pass.env fail
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/fake-redone.env fail
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/fake-report-is-dump.env fail
```

Always-green Java (does **not** rerun #122 / #124 / #137 suites):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G2ReportPhysicalDumpHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g2-report-surface-guard.sh
# ./scripts/g2-physical-dump-surface.sh
```

`PASS` for live JAR requires all of: reachable product JAR, issued context, observed `POST /api/v1/p3/reports/query` + `/export`, and observed `POST /api/v1/p3/dumps/export`. This leftover does not start the JAR.

See [engineering/g2-report-physical-dump-honesty-EVIDENCE.md](./engineering/g2-report-physical-dump-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g2-report-physical-dump-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product-wide G2 | Explicitly `g2_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Redo of #122 / #124 / #137 | Parent leftover product modules stay on those leftovers. |
| Report export is a physical dump | Report stays `AuthorizedExport.submit`. Honesty gate requires `report_is_physical_dump=false`. |
| Dump is report Task submit | Dump stays `AuthorizedExport.writeProjected`. Honesty gate requires `dump_is_report_task_submit=false`. |
| Live JAR + issued-context HTTP on this agent VM | Base URL unset; nothing on `:8080` / `:8088`. Honesty SKIP. |
| H5 form.write leftovers (#163/#173/#204) | Sibling leftovers. Not rewritten. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Docker / Compose | Forbidden. Native JAR only when the operator already started it. |

## Honest leftovers (not claimed)

- Live product-JAR + issued-context report query/export walk
- Live product-JAR + issued-context physical dump / raw export walk
- Product-wide G2 close
- Redo of #122 / #124 / #137 / H5 form.write leftovers
- Ops-level `pg_dump` through SurfaceGuard
