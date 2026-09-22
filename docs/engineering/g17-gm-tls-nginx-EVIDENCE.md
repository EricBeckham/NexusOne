# G17 Nginx/Tongsuo (or local dual-stack) 国密 TLS leftover — evidence

Recorded on this branch after rebase onto `origin/main` (`895f748`, includes #127 Linux/ARM lifecycle leftover, #126 H5 PKCE, #123 90-day hot purge, #122 SurfaceGuard CI, #110 local GM dual-cert). **Not** a full G17 close. **Not** 等保 / 密评 / 密码机 / HSM. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g17-gm-tls-nginx-dual-stack.md](../g17-gm-tls-nginx-dual-stack.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04 | product baseline |
| Maven | 3.8.7 | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Bouncy Castle | `bcprov-jdk18on:1.85.2` + `bctls-jdk18on:1.85` | CryptoPort / leftover TLS path; **not** a GM/T qualified module |
| OpenSSL | 3.0.13 (Debian/Ubuntu stock) | **not** Tongsuo |
| nginx | not installed | Tongsuo-built nginx required for GMTLS PASS |
| Runtime | local `mvn test` + collect script | No Docker / Compose / OpenBao / KMS |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g17-gm-tls-nginx-dual-stack/run.sh --write-material
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report <out>/report.env
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report \
  scripts/g17-gm-tls-nginx-dual-stack/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report \
  scripts/g17-gm-tls-nginx-dual-stack/fixtures/fake-nginx-pass.env
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report \
  scripts/g17-gm-tls-nginx-dual-stack/fixtures/fake-claims.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g17-gm-tls-nginx/`. A sanitized copy is [`g17-gm-tls-nginx/agent-vm.txt`](./g17-gm-tls-nginx/agent-vm.txt) once recorded.

| Check | Result | Notes |
|---|---|---|
| Local Java dual-stack | pending record | intl `TLS_AES_128_GCM_SHA256` + GM `TLS_SM4_GCM_SM3`; cross-talk rejected |
| Nginx/Tongsuo GMTLS handshake | **SKIP** (expected) | nginx not on PATH; stock OpenSSL 3.0.13 is not Tongsuo |
| GM/T 0024 enc-cert negotiated | **SKIP** | RFC 8998 leftover; NTLS documented only |
| 等保 / 密评 / 密码机 / HSM / G17 close | **NOT_CLAIMED** | honesty gate enforces `false` / `NOT_CLAIMED` |
| G18 / AuditPort | unchanged | not reopened |

## Honest leftovers

- Tongsuo-built nginx + Tongsuo `s_client` handshake on a host that actually has those binaries
- GM/T 0024 encryption-cert protocol inside Java
- Qualified GM/T Provider, 密码机 / HSM, classified / 等保 / 密评 delivery
- Product HTTPS connector in front of `nexus-bootstrap`
- MFA / 三权 / TOTP (already on main; not this PR)
