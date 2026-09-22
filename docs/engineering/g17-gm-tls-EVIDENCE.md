# G17 国密 TLS 双证书 slice — mvn evidence

Recorded on this branch after rebase onto `origin/main` (`7efc687`, includes #99 三权分立, #96 admin MFA, #107 F010, #100 G9 timeout, #105 Linux/ARM collect, #103 G7 expense/travel, #104 G4 object cold archive, #108 G4 PKCE login audit). **Not** a full G17 close. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g17-gm-tls-dual-cert.md](../g17-gm-tls-dual-cert.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04 | `Runtime.version()` / `java -version` |
| Maven | 3.8.7 | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Bouncy Castle provider | `bcprov-jdk18on:1.85.2` (`BC version 1.8502`) | CryptoPort GM path; **not** a GM/T qualified module |
| Bouncy Castle PKIX / TLS | `bcpkix-jdk18on:1.85` / `bctls-jdk18on:1.85` | no `1.85.2` artifacts on Central |
| Runtime | local `mvn test` | No Docker / Compose / OpenBao / KMS |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

mvn -pl nexus-crypto-core -am test \
  -Dtest=GmTlsDualCertTest,GmTlsHandshakeTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## Results (agent VM)

Finished **2026-09-22T17:17:04Z**. **BUILD SUCCESS**. Tests run: **5**, Failures: **0**, Errors: **0**, Skipped: **0**.

What those tests prove:

- Dual SM2 certificates (server-sign `digitalSignature` vs server-encrypt `keyEncipherment`/`keyAgreement`) are distinct, issued with `SM3withSM2` OID `1.2.156.10197.1.501`.
- PKCS12 dual-cert config loads; connector hints do not embed the store password.
- Localhost TLS 1.3 mTLS handshake negotiates RFC 8998 `TLS_SM4_GCM_SM3` (`cipherSuiteId=198` / `0x00C6`) and exchanges `G17-OK`.
- Server presents 2 certificates (sign leaf + CA); client presents 2 (client leaf + CA).
- mTLS without a client certificate is rejected.

Handshake line:

```
Result[succeeded=true, mtls=true, protocol=TLS 1.3, cipherSuite=TLS_SM4_GCM_SM3,
cipherSuiteId=198, serverCertificateCount=2, clientCertificateCount=2,
javaRuntime=21.0.10+7-Ubuntu-124.04, bcProvider=BC version 1.8502,
bcTls=bctls-jdk18on:1.85, dualServerCertsDistinct=true, sm3withSm2=true, payload=G17-OK]
```

## Honest leftovers

- GM/T 0024 dual-cert **protocol** (encryption cert consumed in handshake) is not implemented. RFC 8998 TLS 1.3 uses the sign cert.
- Nginx / Tongsuo GM dual-stack leftover is [g17-gm-tls-nginx-dual-stack.md](../g17-gm-tls-nginx-dual-stack.md) (scripts + documented config; SKIP without Tongsuo). Qualified GM/T Provider, 密码机 / HSM, classified delivery: **not** this PR.
- Product Tomcat connector stays HTTP. `nexus.tls.gm.enabled` defaults false.
- G18, 三权分立, admin MFA, and AuditPort same-TX were **not** reopened.
