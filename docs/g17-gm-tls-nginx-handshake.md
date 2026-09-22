# G17 leftover — real Tongsuo/Nginx GMTLS handshake honesty

S0+S1 remainder after the local GM dual-cert leftover ([g17-gm-tls-dual-cert.md](./g17-gm-tls-dual-cert.md) / #110) and the Nginx/Tongsuo dual-stack leftover ([g17-gm-tls-nginx-dual-stack.md](./g17-gm-tls-nginx-dual-stack.md) / #129). **G18 stays closed.** This slice does **not** redo #110 dual-cert material, does **not** redo #129 `DualStackTlsProbe` / documented nginx templates, does **not** redo recovery-code / email MFA ([g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md) / #154), and does **not** write Keycloak `otp` from Vue. `AuditPort.append` / `appendNewTransaction` same-TX rules are unchanged.

This is **one G17 leftover**: an honesty inventory for a **real** Tongsuo-built nginx + Tongsuo `s_client` GMTLS/NTLS handshake. Missing Tongsuo/Nginx is an honest **SKIP**. It is **not** a full G17 close, **not** 等保 / 密评, and **not** a 密码机 / HSM product.

Keep leftover rows in [docs/README.md](./README.md): `g17-gm-tls-nginx-dual-stack` (#129), this file, and the PASS/SKIP leftover [g17-gm-tls-nginx-handshake-honesty.md](./g17-gm-tls-nginx-handshake-honesty.md). Also keep `g17-admin-mfa-recovery-email` (#154), `g2-ops-pg-dump` (#155), `s0-secret-rotate-without-restart` (#143), `g7-withdraw-browser` (#159), `g10-leftover-honesty` (#202), `g4-export-wizard-deny-honesty` (#203), `g2-h5-every-page-form-write-honesty` (#204), `g9-redis-down-invalidate-remainder` (#197), and `g17-mfa-recovery-vue-otp-honesty` (#205).

## What this leftover covers

| Piece | Behaviour |
|---|---|
| Binary inventory | `nginx -V` must report Tongsuo / BabaSSL / NTLS. Stock OpenSSL nginx is never a GMTLS PASS. Tongsuo `s_client` (`tongsuo` / `openssl-tongsuo` / `NEXUS_G17_TONGSUO`) is required for PASS. |
| Real handshake | PASS only when Tongsuo nginx **and** Tongsuo `s_client` exist **and** a GMTLS/NTLS handshake was observed (`handshake_observed=true` + `gmtls_cipher`). |
| Honest SKIP | Missing nginx, stock OpenSSL nginx, missing Tongsuo `s_client`, or missing already-written #129 material is **SKIP** with a reason. This leftover does **not** regenerate PKCS12/PEM. No Docker fallback. |
| Reuse #129 templates | When binaries + existing material are present, the script renders [`scripts/g17-gm-tls-nginx-dual-stack/nginx/`](../scripts/g17-gm-tls-nginx-dual-stack/nginx/) without rewriting those templates. |
| Honesty gate | `--verify-report` rejects `nginx_gmtls_handshake=PASS` without `tongsuo_nginx=true` + `tongsuo_cli=true` + `handshake_observed=true`, and rejects 等保 / 密评 / HSM / G17-close / #110/#129 redo claims. |

Safe published vectors (not secrets):

| Item | Value |
|---|---|
| Tongsuo NTLS (when binaries exist) | `ECC-SM2-SM4-GCM-SM3` / `ECDHE-SM2-SM4-GCM-SM3` + sign/encrypt cert pair from existing #129 material |
| Local Java leftover (not this PR) | RFC 8998 `TLS_SM4_GCM_SM3` stays on #110 / #129 |

## How to run (no Docker)

```bash
./scripts/g17-gm-tls-nginx-handshake.sh
./scripts/g17-gm-tls-nginx-handshake.sh --verify-report <out>/report.env
./scripts/g17-gm-tls-nginx-handshake.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-gm-tls-nginx-handshake.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake/fixtures/fake-handshake-pass.env
./scripts/g17-gm-tls-nginx-handshake.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake/fixtures/fake-claims.env
```

On a host that actually has Tongsuo-built nginx + Tongsuo `s_client` **and** already-written #129 material:

```bash
export NEXUS_G17_NGINX=/path/to/tongsuo-nginx
export NEXUS_G17_TONGSUO=/path/to/tongsuo
export NEXUS_G17_MATERIAL=/path/to/existing-129-material
./scripts/g17-gm-tls-nginx-handshake.sh
```

`--require-handshake` turns a missing Tongsuo handshake into FAIL. Default is honest SKIP.

The #129 dual-stack leftover is unchanged as a sibling:

```bash
./scripts/g17-gm-tls-nginx-dual-stack/run.sh
```

See [engineering/g17-gm-tls-nginx-handshake-EVIDENCE.md](./engineering/g17-gm-tls-nginx-handshake-EVIDENCE.md).

## What this does not claim

| Leftover | Why |
|---|---|
| Full G17 / 等保 / 密评 | Explicitly `g17_close=NOT_CLAIMED`, `dengbao_claimed=false`, `mipin_claimed=false`. |
| 密码机 / HSM | `hsm_claimed=false`. No OpenBao / cloud KMS. |
| Product Tongsuo handshake on this agent VM | nginx not installed; stock OpenSSL 3.x is not Tongsuo. Honesty SKIP. |
| GM/T 0024 enc-cert negotiation inside Java | RFC 8998 leftover; NTLS only when Tongsuo binaries exist. |
| #110 dual-cert generation | `dual_cert_110_redone=false`. |
| #129 Java dual-stack probe | `dual_stack_129_redone=false`. Templates reused, not rewritten. |
| Recovery codes / email MFA / Vue otp write-back | #154 leftover stays [g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md). Not redone. |
| Product HTTPS connector | Boot 4.1.1 stays HTTP (`nexus.tls.gm.enabled=false`). |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |
