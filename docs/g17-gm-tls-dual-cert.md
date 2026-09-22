# G17 leftover — 国密 TLS 双证书 (local Java / Bouncy Castle)

S0+S1 PostgreSQL remainder after 三权分立 ([g17-separation-of-duties.md](./g17-separation-of-duties.md) / #99) and admin MFA ([g17-admin-mfa.md](./g17-admin-mfa.md) / #96). **G18 stays closed.** This slice does **not** redo duty roles or admin MFA, and does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules.

This is **one G17 leftover**: generate and load a **dual SM2 certificate** set (server sign + server encrypt, plus mTLS client) and prove a local Java TLS handshake. It is **not** a full G17 close, **not** classified delivery, and **not** a password-machine / HSM product.

## What this slice delivers

| Piece | Behaviour |
|---|---|
| Dual GM cert material | Ephemeral SM2 / SM3withSM2 CA issues **server-sign** (`digitalSignature`) and **server-encrypt** (`keyEncipherment` + `keyAgreement`), plus an mTLS client cert. Same curve / signature JCA names as `CryptoSuite.GM`. |
| Local stores | Script / writer emit PKCS12 under `.local/g17-gm-tls/` (gitignored). Public PEMs and SM3 fingerprints (via CryptoPort GM) are written under `public/`. Password is env `NEXUS_GM_TLS_STORE_PASSWORD` or a generated file; never committed. |
| Handshake proof | `GmTlsHandshakeProbe` binds `127.0.0.1`, runs TLS **1.3** with RFC 8998 `TLS_SM4_GCM_SM3` (`0x00C6`) using Bouncy Castle `bctls` + `bcprov`. Server presents the sign cert; encrypt cert is loaded and distinct. mTLS presents the client cert. |
| Connector hints | `connector.hints.yaml` documents Tomcat / Jetty `SSLHostConfig` pointing at the two server PKCS12 files. Product HTTP connector stays the default (`nexus.tls.gm.enabled=false`). |

Safe published vectors (not secrets):

| Item | Value |
|---|---|
| SM2 curve OID | `1.2.156.10197.1.301` |
| SM3 OID | `1.2.156.10197.1.401` |
| SM3withSM2 OID | `1.2.156.10197.1.501` |
| SM4 OID | `1.2.156.10197.1.104` |
| TLS_SM4_GCM_SM3 | `0x00C6` (RFC 8998) |

## Local Java HTTPS connector (opt-in)

Default Boot 4.1.1 Tomcat remains HTTP. This slice does **not** turn `server.ssl.enabled=true` on the product JAR.

```yaml
nexus:
  tls:
    gm:
      enabled: false
      dir: .local/g17-gm-tls
```

Set `NEXUS_GM_TLS_ENABLED=true` and `NEXUS_GM_TLS_STORE_PASSWORD` only after generating local stores:

```bash
NEXUS_GM_TLS_WRITE=1 ./scripts/g17-gm-tls-dual-cert.sh
```

Tomcat / Jetty can attach both PKCS12 files on one host:

- `server-sign.p12` — handshake / CertificateVerify (RFC 8998)
- `server-encrypt.p12` — GM dual-cert **material** (keyEncipherment / keyAgreement)
- `trust.p12` — local test CA
- `client.p12` — mTLS client

Bouncy Castle JSSE (`BCJSSE`) **lists** `TLS_SM4_GCM_SM3` as supported but does **not** select it for SM2 certificates with default cipher selection. The automated proof therefore uses the Bouncy Castle TLS API (`TlsServerProtocol` / `TlsClientProtocol`) as the local Java connector, not SunJSSE.

## Tests

```bash
./scripts/g17-gm-tls-dual-cert.sh
# or
mvn -pl nexus-crypto-core -am test \
  -Dtest=GmTlsDualCertTest,GmTlsHandshakeTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

See [engineering/g17-gm-tls-EVIDENCE.md](./engineering/g17-gm-tls-EVIDENCE.md).

## Honest leftovers (not claimed)

- Full GM/T 0024 GMTLS dual-cert **protocol** (separate encryption certificate consumed in the handshake). RFC 8998 TLS 1.3 uses the sign cert for CertificateVerify; the encrypt cert is generated, stored, and loaded, not negotiated as a GMTLS encryption credential.
- Nginx / Tongsuo / qualified GM TLS dual-stack in front of the product JAR
- Qualified GM/T Provider (Bouncy Castle `bcprov-jdk18on:1.85.2` + `bctls-jdk18on:1.85` is the existing CryptoPort path, not a 商用密码资质模块)
- 密码机 / HSM / OpenBao / cloud KMS
- Classified / 等保 product close
- Reopening G18, 三权分立, admin MFA, or AuditPort same-TX
