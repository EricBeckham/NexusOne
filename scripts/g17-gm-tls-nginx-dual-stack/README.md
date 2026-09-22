# G17 leftover — Nginx/Tongsuo or local dual-stack 国密 TLS

Evidence collector for the leftover marked **Nginx/Tongsuo 国密双栈** on the team list. This is **not** a G17 / 等保 / 密评 / 密码机 close.

The documented Tongsuo nginx config can be rendered without Docker. On VMs without Tongsuo-built nginx the GMTLS handshake is an honest **SKIP**. The local Java two-port probe (international TLS 1.3 + RFC 8998 `TLS_SM4_GCM_SM3`) is the runnable-without-Tongsuo proof.

## Run

No Docker. JDK 21 and Maven on PATH.

```bash
./scripts/g17-gm-tls-nginx-dual-stack/run.sh
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --write-material --out .local/g17-gm-tls-nginx/manual
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report .local/g17-gm-tls-nginx/manual/report.env
```

| Path | Role |
|---|---|
| `nginx/tongsuo-dual-stack.conf.template` | Two-port Tongsuo NTLS + international TLS config |
| `nginx/nginx.conf.template` | Wrapper (`daemon off`, loopback prefix) |
| `fixtures/honest-skip.env` | Accepted SKIP report |
| `fixtures/fake-nginx-pass.env` | Must fail: GMTLS PASS without Tongsuo nginx |
| `fixtures/fake-claims.env` | Must fail: 等保 / 密评 / HSM / G17 close |

`--require-nginx` turns a missing Tongsuo handshake into FAIL. Default is SKIP.

## Honesty rules

- `g17_close` is always `NOT_CLAIMED`.
- `dengbao_claimed` / `mipin_claimed` / `hsm_claimed` must stay `false`.
- `nginx_gmtls_handshake=PASS` requires `nginx_present=true` **and** `tongsuo_nginx=true`.
- `gmtls_enc_cert_negotiated` cannot be `PASS` (GM/T 0024 enc-cert leftover).
- `docker_used=false`. G18 stays `closed_unchanged`. `audit_port_changed=false`.
- Passwords are never written to the report.

Evidence: [docs/engineering/g17-gm-tls-nginx-EVIDENCE.md](../../docs/engineering/g17-gm-tls-nginx-EVIDENCE.md).
