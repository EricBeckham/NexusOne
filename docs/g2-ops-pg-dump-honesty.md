# G2 leftover — ops `pg_dump` outside SurfaceGuard honesty beyond #155

S0+S1 remainder after the #155 leftover ([g2-ops-pg-dump.md](./g2-ops-pg-dump.md)). Rebased onto `origin/main` `86c33a8` (Merge #211 S0+S1 claim-list tip-sync after #205; also #205 at `44f853d`, #197 at `d3a197a`). **G18 stays closed.** This leftover does **not** redo #137 dump HTTP, #144 `tenant_only` / catalog, #150 todo HTTP, or #155 product gate / `OpsPgDumpHonestyGate`. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** run a live product-database `pg_dump`. It does **not** claim product G2 close. `g2_close=NOT_CLAIMED`.

Keep **both** leftover rows in [docs/README.md](./README.md): `g2-ops-pg-dump` (#155) and this file. Keep **all sibling leftover rows**, including `g17-mfa-recovery-vue-otp-honesty` (#205), `g9-redis-down-invalidate-remainder` (#197), `g2-h5-every-page-form-write-honesty` (#204), `g4-export-wizard-deny-honesty` (#203), `g10-leftover-honesty` (#202), `g5-mid-publish-failure-honesty` (#196), `g3-outbox-consume-crash-honesty` (#195), `g1-queryast-boundsql-honesty` (#191), `g15-wx-dt-native-bridge-honesty` (#198), `s0-secret-rotate-without-restart-honesty` (#200), `g7-query-expense-travel-honesty` (#192), `linux-arm-profile-leftover-honesty` (#193), `g4-p1-staging-audit-events-honesty` (#190), `g6-recovery-port-dry-run-honesty` (#186), `s0s1-live-redis-minio-isolation-honesty` (#185), `g17-separation-honesty` (#187), `g4-hot-purge-90d-honesty` (#183), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `s0-secret-getenv-snapshot-honesty` (#180 / #182 wording), and the other G2 leftovers.

#155 already documents that ops-level `pg_dump` is **out** of the product `SurfaceGuard` path and statically guards that boundary. Product physical dump HTTP stays on #137 `AuthorizedExport.writeProjected`. The leftover here is the **honesty gate** beyond that: documented path is **PASS**. Live product-database `pg_dump` is **PASS** only when a dump of a product DB was actually observed. Missing product DB / no observed dump is honest **SKIP**. It never fabricates `PASS`. `#155` static gate is **not** a live dump. Ops `pg_dump` must use #137 `AuthorizedExport` **or** stay ops/G6 runbook.

## What this leftover covers

| Path | Behavior |
|---|---|
| #155 leftover docs + `OpsPhysicalDumpCatalog` | Ops `pg_dump` is documented **outside** product SurfaceGuard. Always-green. Not rewritten. |
| Product dump HTTP (#137) | `DumpController` uses `AuthorizedExport.writeProjected`. Always-green. Not rewritten. |
| Ops scripts / G6 runbooks | Stay a `pg_dump` runbook. Must not claim `SurfaceGuard` / `AuthorizedExport`. Always-green. |
| Live product-database `pg_dump` | `live_product_db_pg_dump=PASS` only when `live_product_db_pg_dump_observed=true`. This leftover does not dump. Missing DB is honest SKIP. |
| Honesty gate | `--verify-report` rejects fake live dump PASS, `ops_pg_dump_on_surfaceguard=true`, `g2_close=CLOSED`, and #137/#144/#150/#155 redo flags. |
| Leftover rows | `docs/README.md` keeps #155 and this honesty leftover, plus every sibling leftover row. |

Reuse only: existing #155 catalog / leftover docs and the #137 dump path. Java 21 / Boot **4.1.1**. No Docker. Does **not** invoke `./scripts/g2-ops-pg-dump.sh` (that is the #155 contract).

## How to run

```bash
./scripts/g2-ops-pg-dump-honesty.sh
./scripts/g2-ops-pg-dump-honesty.sh --verify-report \
  scripts/g2-ops-pg-dump-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-ops-pg-dump-honesty.sh --verify-report \
  scripts/g2-ops-pg-dump-honesty/fixtures/fake-live-dump-pass.env
./scripts/g2-ops-pg-dump-honesty.sh --verify-report \
  scripts/g2-ops-pg-dump-honesty/fixtures/fake-g2-close.env
./scripts/g2-ops-pg-dump-honesty.sh --verify-report \
  scripts/g2-ops-pg-dump-honesty/fixtures/fake-ops-on-surface.env
```

Always-green Java (does not redo #137 / #144 / #150 / #155 suites):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G2OpsPgDumpPathHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #155 leftover stays the reproduction command for the original static gate:

```bash
# do not run for this leftover
# ./scripts/g2-ops-pg-dump.sh
```

See [engineering/g2-ops-pg-dump-honesty-EVIDENCE.md](./engineering/g2-ops-pg-dump-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g2-ops-pg-dump-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product-wide G2 | Explicitly `g2_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Ops `pg_dump` on SurfaceGuard | Documented **out of path**. Must use #137 `AuthorizedExport` or stay ops/G6 runbook. |
| #155 static gate / `OpsPgDumpHonestyGate` | Sibling leftover on `main`. `redone_155=false`. |
| Physical dump HTTP (#137) / tenant_only catalog (#144) / todo HTTP (#150) | Sibling leftovers. Not rewritten. |
| Live product-database `pg_dump` on this VM | Not observed. Honest SKIP. `#155` static gate is not a live dump. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Docker / Compose | Forbidden. |

## Honest leftovers (not claimed)

- Live `pg_dump` of a product database
- Putting ops-level `pg_dump` through product `SurfaceGuard`
- Product-wide G2 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
