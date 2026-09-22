# G17 real Tongsuo/Nginx GMTLS handshake honesty leftover — evidence

Recorded on this branch after rebase onto `origin/main` (`3c6dea2`, includes #159 G7 browser withdraw leftover, #143 SecretPort rotate-without-restart leftover, #155 ops `pg_dump` honesty leftover, #154 recovery-code / email MFA leftover, and #147 SPA verifier leftover). **Not** a full G17 close. **Not** 等保 / 密评 / 密码机 / HSM. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. #110 dual-cert and #129 dual-stack leftovers were **not** redone.

See [docs/g17-gm-tls-nginx-handshake.md](../g17-gm-tls-nginx-handshake.md). Keep both leftover rows: [g17-gm-tls-nginx-dual-stack.md](../g17-gm-tls-nginx-dual-stack.md) and this leftover. Keep [g17-admin-mfa-recovery-email.md](../g17-admin-mfa-recovery-email.md) (#154), [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md) (#143), and [g7-withdraw-browser.md](../g7-withdraw-browser.md) (#159).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Bouncy Castle | `bcprov-jdk18on:1.85.2` + `bctls-jdk18on:1.85` | #110/#129 path; **not** used by this leftover |
| OpenSSL | stock 3.x (not Tongsuo) | **not** Tongsuo |
| nginx | not installed | Tongsuo-built nginx required for GMTLS PASS |
| Runtime | collect script + honesty fixtures | No Docker / Compose / OpenBao / KMS |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g17-gm-tls-nginx-handshake/`. A sanitized copy is [`g17-gm-tls-nginx-handshake/agent-vm.env`](./g17-gm-tls-nginx-handshake/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Real Tongsuo/Nginx GMTLS handshake | **SKIP** (expected) | nginx not on PATH; stock OpenSSL 3.0.13 is not Tongsuo |
| handshake_observed | **false** | honesty gate requires `false` on SKIP |
| GM/T 0024 enc-cert negotiated | **SKIP** | RFC 8998 leftover; NTLS only when binaries exist |
| #110 / #129 redo | **false** | dual-cert and dual-stack probe untouched |
| #154 recovery / email MFA | **not redone** | [g17-admin-mfa-recovery-email.md](../g17-admin-mfa-recovery-email.md) |
| 等保 / 密评 / 密码机 / HSM / G17 close | **NOT_CLAIMED** | honesty gate enforces `false` / `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Product Tongsuo-built nginx + Tongsuo `s_client` handshake on a host that actually has those binaries
- GM/T 0024 encryption-cert protocol inside Java
- Qualified GM/T Provider, 密码机 / HSM, classified / 等保 / 密评 delivery
- Product HTTPS connector in front of `nexus-bootstrap`
- Vue otp write-back; live email MFA delivery / Keycloak recovery-authn-codes (#154 leftover)
