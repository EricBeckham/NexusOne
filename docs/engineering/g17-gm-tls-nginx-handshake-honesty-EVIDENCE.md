# G17 leftover — Tongsuo/Nginx GMTLS handshake PASS/SKIP honesty beyond #160/#129 — evidence

Recorded on this branch after rebase onto `origin/main` `86c33a8` (Merge #211 tip-sync after #205 G17 MFA recovery/OTP leftover honesty / #197 G9 Redis-down invalidate leftover remainder / #204 G2 H5/form.write leftover honesty / #206 tip-sync / #203 G4 export-wizard deny leftover honesty; includes #202 G10 leftover honesty / #196 G5 mid-publish leftover honesty / #195 G3 Outbox leftover honesty). **Not** 等保 / 密评 / 完整 G17. **Not** a redo of #110 / #129 / #160. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone.

See [docs/g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g17-gm-tls-nginx-dual-stack.md](../g17-gm-tls-nginx-dual-stack.md) (#129), [g17-gm-tls-nginx-handshake.md](../g17-gm-tls-nginx-handshake.md) (#160), [g17-gm-tls-dual-cert.md](../g17-gm-tls-dual-cert.md) (#110), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), and [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| OpenSSL | stock 3.x (not Tongsuo) | **not** Tongsuo |
| nginx | not installed | Tongsuo-built nginx required for GMTLS PASS |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#129` / `#160` collect scripts not re-run |

## Commands

```bash
./scripts/g17-gm-tls-nginx-handshake-honesty.sh
./scripts/g17-gm-tls-nginx-handshake-honesty.sh --verify-report <out>/report.env
./scripts/g17-gm-tls-nginx-handshake-honesty.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-gm-tls-nginx-handshake-honesty.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake-honesty/fixtures/fake-handshake-pass.env
./scripts/g17-gm-tls-nginx-handshake-honesty.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake-honesty/fixtures/fake-g17-close.env
./scripts/g17-gm-tls-nginx-handshake-honesty.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake-honesty/fixtures/fake-redone.env
```

Do **not** treat `./scripts/g17-gm-tls-nginx-dual-stack/run.sh` or `./scripts/g17-gm-tls-nginx-handshake.sh` collect as this leftover. Those scripts are the #129 / #160 contracts. Parent `--verify-report` on honest fixtures is harness evidence.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g17-gm-tls-nginx-handshake-honesty/`. A sanitized copy is [`g17-gm-tls-nginx-handshake-honesty/agent-vm.env`](./g17-gm-tls-nginx-handshake-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake handshake PASS / fake G17 close / fake #129/#160 redo rejected (`2026-09-22T20:57:00Z`) |
| leftover #129 cells still honest | **PASS** | DualStackTlsProbe source + nginx templates present; parent honest SKIP accepted |
| leftover #160 cells still honest | **PASS** | committed handshake SKIP; `g17_close=NOT_CLAIMED` |
| DualStackTlsProbe harness | **PASS** | local Java leftover; **not** a Tongsuo handshake |
| Real Tongsuo/Nginx GMTLS handshake | **SKIP** (expected) | nginx not on PATH; stock OpenSSL 3.x is not Tongsuo |
| handshake_observed | **false** | honesty gate requires `false` on SKIP |
| Sibling leftover rows | kept | includes #202 `g10-leftover-honesty`, #203 `g4-export-wizard-deny-honesty`, #204 `g2-h5-every-page-form-write-honesty`, #197 `g9-redis-down-invalidate-remainder`, and #205 `g17-mfa-recovery-vue-otp-honesty` |
| #110/#129/#160 redo | **false** | sibling leftovers |
| 等保 / 密评 / 密码机 / HSM / G17 close | **NOT_CLAIMED** | honesty gate enforces `false` / `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Product Tongsuo-built nginx + Tongsuo `s_client` handshake
- GM/T 0024 encryption-cert protocol inside Java
- Qualified GM/T Provider / 密码机 / HSM / 等保 / 密评
- Product HTTPS connector
- Redo of #110 / #129 / #160
