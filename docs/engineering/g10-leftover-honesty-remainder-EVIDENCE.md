# G10 leftover — file-reconcile / identity-drift PASS/SKIP honesty remainder beyond #202/#169/#179/#141 — evidence

Recorded on this branch after rebase onto `origin/main` `426a78e` (Merge #221 G2 report/physical-dump leftover honesty after #225 S0 rotate/getenv remainder leftover honesty after #222 G4 PKCE SPA-verifier remainder leftover honesty / #216 G17 SoD leftover honesty / #218 claim-list tip-sync / #220 G15 wx-dt/native-bridge remainder leftover honesty / #215 G1 BoundSql/COUNT/export leftover honesty). **Not** a product G10 close. **Not** a redo of #141 / #169 / #179 / #202. **Not** identity apply. **Not** SAML / LDAP / SMS MFA. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone.

See [docs/g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g10-identity-drift-dry-run.md](../g10-identity-drift-dry-run.md) (#141), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), and [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| MinIO | not listening on `127.0.0.1:19000` / `:9000` | `S3_*` unset |
| Keycloak | not listening on `127.0.0.1:8080` | OIDC discovery not reachable |
| Runtime | leftover honesty remainder inventory only | No Docker / Compose. `#141` / `#169` / `#179` / `#202` scripts not re-run |

## Commands

```bash
./scripts/g10-leftover-honesty-remainder.sh
./scripts/g10-leftover-honesty-remainder.sh --verify-report <out>/report.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-live-minio-pass.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-live-keycloak-pass.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-live-apply-pass.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-g10-close.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-redone.env
```

Do **not** treat `./scripts/g10-leftover-honesty.sh`, `./scripts/g10-file-reconcile-honesty.sh`, or `./scripts/g10-identity-drift-honesty.sh` as this leftover. Those scripts are the #202 / #169 / #179 contracts. Parent `--verify-report` on honest fixtures is harness evidence.

## Results (agent VM)

Filled after the leftover honesty remainder script on this VM at `2026-09-22T21:34:03Z`. Live output is gitignored under `.local/g10-leftover-honesty-remainder/`. A sanitized copy is [`g10-leftover-honesty-remainder/agent-vm.env`](./g10-leftover-honesty-remainder/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live MinIO / Keycloak / apply PASS, fake `g10_close=CLOSED`, and fake #202 redo rejected (`2026-09-22T21:34:03Z`) |
| leftover #169 cells still honest | **PASS** | counting preview/apply + tenant-prefix stay PASS; committed live MinIO SKIP |
| leftover #179 cells still honest | **PASS** | dry-run default no-write; committed live Keycloak / apply SKIP |
| leftover #202 cells still honest | **PASS** | parent leftover honesty fixtures stay PASS |
| Counting preview vs apply | **PASS** | from committed #169 artifact; this leftover does not re-run it |
| Tenant-prefix guard | **PASS** | from committed #169 artifact |
| Live MinIO / object store | **SKIP** (expected) | `S3_ENDPOINT` unset; nothing on `:19000` / `:9000` |
| Live Keycloak 8080 dry-run | **SKIP** (expected) | nothing on `:8080`; OIDC discovery not reachable |
| live_preview_observed / live_dry_run_observed | **false** | honesty gate requires `false` on SKIP |
| Live apply | **SKIP** | product apply not redone |
| Sibling leftover rows | kept | #141 / #169 / #179 / #202 + this leftover + #221 / #225 / #223 / #222 / #216 / #220 / #215 / #210 / #217 siblings |
| #141/#169/#179/#202 redo | **false** | sibling leftovers |
| SAML / LDAP / SMS MFA | **false** | honesty gate |
| Product G10 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live MinIO / object-store preview + apply + tenant-prefix walk
- Live Keycloak 26.5.2 `:8080` detect + proposed-fix dry-run
- Product identity apply (`POST {"apply":true}`)
- Product G10 close
- SAML / LDAP / AD / SMS MFA / Keycloak Organizations
- Redo of #141 / #169 / #179 / #202
