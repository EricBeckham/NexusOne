# G17 leftover — Tongsuo/Nginx GMTLS handshake PASS/SKIP honesty beyond #160/#129

S0+S1 remainder **after** the #160 real Tongsuo/Nginx GMTLS handshake leftover ([g17-gm-tls-nginx-handshake.md](./g17-gm-tls-nginx-handshake.md)) and the #129 Nginx/Tongsuo dual-stack leftover ([g17-gm-tls-nginx-dual-stack.md](./g17-gm-tls-nginx-dual-stack.md)). Rebased onto `origin/main` `86c33a8` (Merge #211 tip-sync after #205 G17 MFA recovery/OTP leftover honesty / #197 G9 Redis-down invalidate leftover remainder / #204 G2 H5/form.write leftover honesty / #206 tip-sync / #203 G4 export-wizard deny leftover honesty / #202 G10 leftover honesty). **G18 stays closed.** This leftover does **not** redo #110 dual-cert, #129 `DualStackTlsProbe` / documented nginx templates, or #160 handshake collect product code. It does **not** claim 等保 / 密评 / 完整 G17. `g17_close=NOT_CLAIMED`. No P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules are unchanged.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g17-gm-tls-nginx-dual-stack](./g17-gm-tls-nginx-dual-stack.md) (#129), [g17-gm-tls-nginx-handshake](./g17-gm-tls-nginx-handshake.md) (#160), [g17-gm-tls-dual-cert](./g17-gm-tls-dual-cert.md) (#110), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md) (#203), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md) (#197), [g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md) (#180), and [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171). Keep this file.

#129 already locked the local Java `DualStackTlsProbe` (intl TLS 1.3 + RFC 8998 `TLS_SM4_GCM_SM3`) and documented Tongsuo nginx templates. #160 already locked a real Tongsuo handshake as honest **SKIP** when binaries are missing. The leftover here is the **honesty gate beyond those two**: DualStackTlsProbe + parent honesty fixtures stay **PASS**. Live Tongsuo/Nginx GMTLS is **PASS** only when Tongsuo-built nginx **and** Tongsuo `s_client` exist **and** a handshake was observed. Missing Tongsuo/Nginx is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #129 cells still honest | Templates + `DualStackTlsProbe` source stay present. Parent `--verify-report` honest SKIP accepted; fake nginx PASS rejected. `leftover_129_cells_honest=PASS`. Do not redo. |
| #160 cells still honest | Committed `nginx_gmtls_handshake=SKIP`, `handshake_observed=false`, `g17_close=NOT_CLAIMED`. Parent `--verify-report` honest SKIP accepted; fake handshake PASS / 等保 claims rejected. `leftover_160_cells_honest=PASS`. Do not redo. |
| Dual-stack probe harness | Always-green `dual_stack_probe=PASS`. Local Java leftover, **not** a Tongsuo handshake. |
| Honest Tongsuo SKIP | `nginx_gmtls_handshake=PASS` only with `tongsuo_nginx=true` + `tongsuo_cli=true` + `handshake_observed=true`. Missing binaries is honest **SKIP**. |
| Sibling leftover rows | `docs/README.md` keeps #129 / #160 and this leftover, plus #202 `g10-leftover-honesty`, #203 `g4-export-wizard-deny-honesty`, #204 `g2-h5-every-page-form-write-honesty`, #197 `g9-redis-down-invalidate-remainder`, #205 `g17-mfa-recovery-vue-otp-honesty`, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects `nginx_gmtls_handshake=PASS` without binaries + observed walk, rejects `g17_close=CLOSED`, and rejects #110 / #129 / #160 redo flags. |

No Docker. No `#129` / `#160` collect re-run. Stock OpenSSL nginx is never a GMTLS PASS.

## What this does not claim

| Leftover | Why |
|---|---|
| 等保 / 密评 / 完整 G17 | Explicitly `g17_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. `dengbao_claimed=false`, `mipin_claimed=false`. |
| 密码机 / HSM | `hsm_claimed=false`. No OpenBao / cloud KMS. |
| Product Tongsuo handshake on this agent VM | nginx not installed; stock OpenSSL 3.x is not Tongsuo. Honesty SKIP. |
| GM/T 0024 enc-cert negotiation inside Java | RFC 8998 leftover; NTLS only when Tongsuo binaries exist. |
| #110 / #129 / #160 redo | Sibling leftovers. `dual_cert_110_redone=false`. `dual_stack_129_redone=false`. `handshake_160_redone=false`. |
| Recovery codes / email MFA / Vue otp write-back | Orthogonal leftovers. Not redone. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |
| Docker / Compose | Forbidden. |

## How to run

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

#129 / #160 stay the sibling reproduction commands. This leftover does **not** invoke their collect paths:

```bash
# do not run for this leftover
# ./scripts/g17-gm-tls-nginx-dual-stack/run.sh
# ./scripts/g17-gm-tls-nginx-handshake.sh
```

Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

See [engineering/g17-gm-tls-nginx-handshake-honesty-EVIDENCE.md](./engineering/g17-gm-tls-nginx-handshake-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g17-gm-tls-nginx-handshake-honesty/`.

## Honest leftovers (not claimed)

- Product Tongsuo-built nginx + Tongsuo `s_client` handshake on a host that actually has those binaries
- GM/T 0024 encryption-cert protocol inside Java
- Qualified GM/T Provider, 密码机 / HSM, classified / 等保 / 密评 delivery
- Product HTTPS connector in front of `nexus-bootstrap`
- Redo of #110 / #129 / #160
