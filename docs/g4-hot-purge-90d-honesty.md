# G4 leftover — 90-day hot-row purge window honesty

S0+S1 remainder after the #123 90-day hot-prefix purge ([g4-hot-purge-90d.md](./g4-hot-purge-90d.md)). Rebased onto `origin/main` `005a741` (Merge #182 S0+S1 claim-list tip sync; also #184 at `324d8b8`, #179 at `5ff0472`). **G18 stays closed.** This leftover does **not** redo #123 / #136 / #147 / #152 / #156 / #166 / #177. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** DROP `platform.audit_record`. It does **not** claim a product-wide G4 close.

#123 already landed eligibility (90-day + durable copy), prefix-only walk, Flyway V504 `GRANT DELETE`, platform Job, and suffix `verify`. That leftover is **not** a live `:5432` / product-JAR / Quartz scheduled DELETE. The leftover here is **honesty only**: the in-memory 90-day / 365-day window policy may **PASS**; the remaining live purge window is **PASS** only when live PostgreSQL + product JAR are present **and** a live prefix DELETE was observed. Missing live DB / JAR is an honest SKIP. It never fabricates `PASS`.

Keep both leftover rows in [docs/README.md](./README.md): [g4-hot-purge-90d.md](./g4-hot-purge-90d.md) (#123) and this file. Keep sibling leftovers including [g4-export-wizard.md](./g4-export-wizard.md) (#152), [g4-export-wizard-deny.md](./g4-export-wizard-deny.md) (#166), [g4-p1-staging-audit-events.md](./g4-p1-staging-audit-events.md) (#156), [g4-pkce-spa-verifier.md](./g4-pkce-spa-verifier.md) (#147), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179), [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171), [g5-mid-publish-failure.md](./g5-mid-publish-failure.md) (#145), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), [s0-secret-rotate-without-restart.md](./s0-secret-rotate-without-restart.md) (#143), [g6-recovery-port-honesty.md](./g6-recovery-port-honesty.md) (#164), [g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) (#175), and [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) (#174).

## What this leftover covers

| Path | Behavior |
|---|---|
| #123 window policy | Already on `main`. Thin in-memory check: `HOT_RETENTION=90d`, `ARCHIVE_RETENTION=365d`, young stays ineligible, 91-day archived prefix is eligible, 366-day archive-only waits for cold, **no DROP**. `window_policy=PASS`. |
| Live `:5432` / JAR purge | Optional listeners on `127.0.0.1:5432` / `:8080`. Unset / down stays SKIP. Zonky #123 is **not** a live purge PASS. |
| Live Quartz | `nexus.audit.hot-purge.schedule-enabled` defaults **false**. IsolatedQuartzJob is tenant-only. Honest SKIP unless a 03:00 UTC walk was observed. |
| Live MinIO cold | Fake / counting storage is not a live cold drill. Honest SKIP. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g4_close=CLOSED`, rejects `hot_table_dropped=true`, rejects #123/#136/#147/#152/#156/#166 redo flags, and requires both leftover rows kept. |

No Docker. No live DELETE. V504 stays `GRANT DELETE` only.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G4 close | Explicitly `g4_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live `:5432` / JAR 90-day prefix DELETE | Honest SKIP on this agent VM. Zonky is not a live walk. |
| Live Quartz scheduled purge | Schedule stays opt-in / default off. |
| Live MinIO cold drill | Fake / counting storage is not live S3. |
| Switching P1 `GET /tenants/{id}/audit-events` off staging | Sibling leftover [g4-p1-staging-audit-events.md](./g4-p1-staging-audit-events.md). |
| #123 Job / Zonky IT redo | Already on `main`. `g4_123_136_147_152_156_166_redone=false`. |
| `DROP TABLE platform.audit_record` | Forbidden. `hot_table_dropped=false`. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

```bash
./scripts/g4-hot-purge-90d-honesty.sh
./scripts/g4-hot-purge-90d-honesty.sh --verify-report <out>/report.env
./scripts/g4-hot-purge-90d-honesty.sh --verify-report \
  scripts/g4-hot-purge-90d-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-hot-purge-90d-honesty.sh --verify-report \
  scripts/g4-hot-purge-90d-honesty/fixtures/fake-live-pass.env
./scripts/g4-hot-purge-90d-honesty.sh --verify-report \
  scripts/g4-hot-purge-90d-honesty/fixtures/fake-g4-close.env
```

Always-green Java (does **not** rerun #123 Job / Zonky ITs):

```bash
mvn -pl nexus-audit-core -am test \
  -Dtest=G4HotPurge90dHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #123 leftover stays a sibling:

```bash
./scripts/g4-hot-purge-90d.sh
```

Do **not** treat that script as this leftover. Java 21 / Boot **4.1.1**. No Docker. Live `:5432` / JAR are **not** required.

See [engineering/g4-hot-purge-90d-honesty-EVIDENCE.md](./engineering/g4-hot-purge-90d-honesty-EVIDENCE.md).

## Honest leftovers (not claimed)

- Live product `:5432` 90-day prefix DELETE + suffix `verifyChain()`
- Live Quartz `audit.hot.purge` at 03:00 UTC
- Live MinIO / object-store cold drill as a purge prerequisite
- Switching P1 GET off staging
- Product G4 close
- Redo of #123 / #136 / #147 / #152 / #156 / #166
