# G17 leftover — Nginx/Tongsuo (or local dual-stack) 国密 TLS

S0+S1 remainder after the local GM dual-cert leftover ([g17-gm-tls-dual-cert.md](./g17-gm-tls-dual-cert.md) / #110). **G18 stays closed.** This slice does **not** redo admin MFA, 三权分立, or TOTP enrollment, and does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules.

This is **one G17 leftover**: a Tongsuo nginx dual-stack config that can be rendered without Docker, plus a **local Java two-port probe** (international TLS 1.3 + RFC 8998 `TLS_SM4_GCM_SM3`) that runs when Tongsuo/Nginx are absent. It is **not** a full G17 close, **not** 等保 / 密评, and **not** a 密码机 / HSM product.

## What this slice delivers

| Piece | Behaviour |
|---|---|
| Documented Tongsuo nginx config | Two loopback server blocks: international TLS 1.2/1.3 (`ssl_certificate`) and GM/T 0024 NTLS (`enable_ntls` + `ssl_ntls_sign_*` + `ssl_ntls_enc_*`). Templates under [`scripts/g17-gm-tls-nginx-dual-stack/nginx/`](../scripts/g17-gm-tls-nginx-dual-stack/nginx/). |
| Collect / verify script | [`scripts/g17-gm-tls-nginx-dual-stack/run.sh`](../scripts/g17-gm-tls-nginx-dual-stack/run.sh) inventories nginx / Tongsuo / stock OpenSSL, runs the local Java probe, renders the config, and writes `report.env`. |
| Honest SKIP | Nginx/Tongsuo GMTLS is `PASS` only when `nginx -V` reports Tongsuo/BabaSSL/NTLS **and** a handshake succeeds. Stock OpenSSL nginx, missing nginx, or missing Tongsuo `s_client` is **SKIP** with a reason. No Docker fallback. |
| Local dual-stack probe | `DualStackTlsProbe` listens on two `127.0.0.1` ports at once: P-256 / `TLS_AES_128_GCM_SHA256` and SM2 / `TLS_SM4_GCM_SM3`. Cross-talk (intl client → GM port, GM client → intl port) is rejected. |
| Honesty gate | `--verify-report` rejects `nginx_gmtls_handshake=PASS` without `tongsuo_nginx=true`, and rejects 等保 / 密评 / HSM / G17-close claims. |

Safe published vectors (not secrets):

| Item | Value |
|---|---|
| International | TLS 1.3 `TLS_AES_128_GCM_SHA256` (`0x1301`), P-256, SHA256withECDSA |
| GM (local Java) | TLS 1.3 `TLS_SM4_GCM_SM3` (`0x00C6`), SM2 / SM3 / SM4 — same leftover as #110 |
| Tongsuo NTLS (documented) | `ECC-SM2-SM4-GCM-SM3` / `ECDHE-SM2-SM4-GCM-SM3` + sign/encrypt cert pair |

## How to run (no Docker)

```bash
./scripts/g17-gm-tls-nginx-dual-stack/run.sh
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report <out>/report.env

# optional: emit PKCS12/PEM under <out>/material (gitignored) and re-render nginx paths
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --write-material
```

Java-only (same probe the script runs):

```bash
mvn -pl nexus-crypto-core -am test \
  -Dtest=DualStackTlsProbeTest,GmTlsHandshakeTest,GmTlsDualCertTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

On a host that actually has Tongsuo-built nginx + Tongsuo `s_client`:

```bash
export NEXUS_G17_NGINX=/path/to/tongsuo-nginx
export NEXUS_G17_TONGSUO=/path/to/tongsuo
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --write-material
```

`--require-nginx` turns a missing Tongsuo handshake into FAIL. Default is honest SKIP.

See [engineering/g17-gm-tls-nginx-EVIDENCE.md](./engineering/g17-gm-tls-nginx-EVIDENCE.md).

## Honest leftovers (not claimed)

- Live Nginx/Tongsuo GMTLS handshake on this agent VM (nginx not installed; stock OpenSSL 3.x is not Tongsuo)
- Full GM/T 0024 encryption-certificate **negotiation** inside the Java handshake. RFC 8998 TLS 1.3 still uses the sign cert; the encrypt cert is material + documented NTLS config only
- Qualified GM/T Provider / 商用密码资质模块
- 密码机 / HSM / OpenBao / cloud KMS
- Classified / 等保 / 密评 product close
- Product Boot 4.1.1 connector stays HTTP (`nexus.tls.gm.enabled=false`)
- Reopening G18, 三权分立, admin MFA, TOTP enrollment, or AuditPort same-TX
