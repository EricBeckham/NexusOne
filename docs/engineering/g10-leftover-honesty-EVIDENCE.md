# G10 leftover — file-reconcile / identity-drift PASS/SKIP honesty beyond #169/#179 — evidence

Recorded on this branch after restart onto `origin/main` `5109245` (Merge #196 G5 mid-publish/browser leftover honesty; includes #195 G3 Outbox/claim-crash leftover honesty / #191 G1 BoundSql/COUNT leftover honesty / #198 G15 wx/dt + native-bridge leftover honesty / #200 S0 rotate leftover honesty / #199 claim-list tip / #192 / #193). **Not** a product G10 close. **Not** a redo of #141 / #169 / #179. **Not** identity apply. **Not** SAML / LDAP / SMS MFA. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone.

See [docs/g10-leftover-honesty.md](../g10-leftover-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g10-identity-drift-dry-run.md](../g10-identity-drift-dry-run.md) (#141), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), and [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| MinIO | not listening on `127.0.0.1:19000` / `:9000` | `S3_*` unset |
| Keycloak | not listening on `127.0.0.1:8080` | OIDC discovery not reachable |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#169` / `#179` scripts not re-run |

## Commands

```bash
./scripts/g10-leftover-honesty.sh
./scripts/g10-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/fake-live-minio-pass.env
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/fake-live-keycloak-pass.env
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/fake-live-apply-pass.env
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/fake-g10-close.env
```

Do **not** treat `./scripts/g10-file-reconcile-honesty.sh` or `./scripts/g10-identity-drift-honesty.sh` as this leftover. Those scripts are the #169 / #179 contracts.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g10-leftover-honesty/`. A sanitized copy is [`g10-leftover-honesty/agent-vm.env`](./g10-leftover-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live MinIO / Keycloak / apply PASS and fake `g10_close=CLOSED` rejected (`2026-09-22T20:43:17Z`) |
| leftover #169 cells still honest | **PASS** | counting preview/apply + tenant-prefix stay PASS; committed live MinIO SKIP |
| leftover #179 cells still honest | **PASS** | dry-run default no-write; committed live Keycloak / apply SKIP |
| Counting preview vs apply | **PASS** | from committed #169 artifact; this leftover does not re-run it |
| Tenant-prefix guard | **PASS** | from committed #169 artifact |
| Live MinIO / object store | **SKIP** (expected) | `S3_ENDPOINT` unset; nothing on `:19000` / `:9000` |
| Live Keycloak 8080 dry-run | **SKIP** (expected) | nothing on `:8080`; OIDC discovery not reachable |
| live_preview_observed / live_dry_run_observed | **false** | honesty gate requires `false` on SKIP |
| Live apply | **SKIP** | product apply not redone |
| Sibling leftover rows | kept | #141 / #169 / #179 + this leftover + #191 / #195 / #196 / #198 / #200 / #192 / #193 siblings |
| #141/#169/#179 redo | **false** | sibling leftovers |
| SAML / LDAP / SMS MFA | **false** | honesty gate |
| Product G10 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live MinIO / object-store preview + apply + tenant-prefix walk
- Live Keycloak 26.5.2 `:8080` detect + proposed-fix dry-run
- Product identity apply (`POST {"apply":true}`)
- Product G10 close
- SAML / LDAP / AD / SMS MFA / Keycloak Organizations
- Redo of #141 / #169 / #179
