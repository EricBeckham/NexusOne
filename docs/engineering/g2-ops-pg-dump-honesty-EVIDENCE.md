# G2 leftover — ops `pg_dump` outside SurfaceGuard honesty beyond #155 — evidence

Recorded on this branch after rebase onto `origin/main` (`005a741`, Merge #182 claim-list tip align; also #184 at `324d8b8`, #179 at `5ff0472`). **Not** a product G2 close. **Not** a redo of #137 / #144 / #150 / #155. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone.

See [docs/g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md). Keep **both** leftover rows in [docs/README.md](../README.md): [g2-ops-pg-dump.md](../g2-ops-pg-dump.md) (#155) and this leftover. Keep **all sibling leftover rows**, including [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180 / #182 wording), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md), [g2-physical-dump-surface.md](../g2-physical-dump-surface.md), [g2-todo-http-surface.md](../g2-todo-http-surface.md), [g2-tenant-only-catalog.md](../g2-tenant-only-catalog.md), and [g2-form-write-honesty.md](../g2-form-write-honesty.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | not required | leftover does not dump a product database |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g2-ops-pg-dump-honesty.sh
./scripts/g2-ops-pg-dump-honesty.sh --verify-report <out>/report.env
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

Do **not** treat `./scripts/g2-ops-pg-dump.sh` as this leftover. That script is the #155 contract.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g2-ops-pg-dump-honesty/`. A sanitized copy is [`g2-ops-pg-dump-honesty/agent-vm.env`](./g2-ops-pg-dump-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live dump PASS rejected / fake `g2_close=CLOSED` rejected / fake ops-on-SurfaceGuard rejected | `2026-09-22T20:05:27Z` after rebase onto `5ff0472` |
| Ops `pg_dump` outside SurfaceGuard | **PASS** | documented + catalog |
| Product dump uses `AuthorizedExport` | **PASS** | #137 path, not rewritten |
| Ops scripts stay G6 runbook | **PASS** | must not claim SurfaceGuard |
| leftover rows kept | **true** | #155 + this leftover + siblings including #179 |
| Live product-database `pg_dump` | **SKIP** (expected) | not observed; #155 static gate is not a live dump |
| collect tests | skipped (`mvn` not on PATH) | same inventory + honesty gate |
| #137/#144/#150/#155 redo | **false** | sibling leftovers |
| Product G2 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live `pg_dump` of a product database
- Putting ops-level `pg_dump` through product `SurfaceGuard`
- Product G2 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
