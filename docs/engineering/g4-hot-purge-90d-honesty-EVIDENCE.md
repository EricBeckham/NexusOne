# G4 leftover — 90-day hot-row purge window honesty evidence

Recorded on this branch after rebase onto `origin/main` `005a741` (Merge #182 S0+S1 claim-list tip sync; also #184 at `324d8b8`, #179 at `5ff0472`). **Not** a product G4 close. **Not** a live `:5432` / JAR prefix DELETE. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `platform.audit_record` was **not** dropped. #123 Job / Zonky ITs were **not** redone.

See [docs/g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md). Keep **both** leftover rows in [docs/README.md](../README.md): [g4-hot-purge-90d.md](../g4-hot-purge-90d.md) (#123) and this leftover. Sibling leftovers stay documented: [g4-export-wizard-deny.md](../g4-export-wizard-deny.md) (#166), [g4-p1-staging-audit-events.md](../g4-p1-staging-audit-events.md) (#156), [g4-pkce-spa-verifier.md](../g4-pkce-spa-verifier.md) (#147), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g5-mid-publish-failure.md](../g5-mid-publish-failure.md) (#145), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md) (#143), [g6-recovery-port-honesty.md](../g6-recovery-port-honesty.md) (#164), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; filled after the collect script |
| Maven | 3.8.x / agent PATH | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | not required | live `:5432` / JAR purge window SKIP when listener missing |
| Runtime | local `mvn test` + collect script | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g4-hot-purge-90d-honesty/`.

| Check | Result | Notes |
|---|---|---|
| Window policy (90d / 365d, no DROP) | **PASS** | in-memory `AuditHotPurgePolicy`; young stays; 366-day archive-only waits for cold |
| Live `:5432` / JAR purge window | **SKIP** (expected) | no live DB/JAR; Zonky #123 is not a live purge PASS |
| Live Quartz | **SKIP** (expected) | schedule-enabled defaults false |
| Live MinIO cold | **SKIP** (expected) | no live MinIO |
| Product G4 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #123/#136/#147/#152/#156/#166 | not redone | `g4_123_136_147_152_156_166_redone=false` |
| Hot table | **not dropped** | `hot_table_dropped=false` |
| G18 / AuditPort | unchanged | not reopened / not redesigned |

## Honest leftovers

- Live product `:5432` 90-day prefix DELETE + suffix `verifyChain()`
- Live Quartz `audit.hot.purge` at 03:00 UTC
- Live MinIO / object-store cold drill as a purge prerequisite
- Switching P1 GET off staging
- Product G4 close
