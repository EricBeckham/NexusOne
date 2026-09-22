# G17 三权分立 honesty leftover — evidence

Recorded on this branch after rebase onto `origin/main` `cf8fc8b` (Merge #183 G4 90-day hot-purge honesty leftover; includes #182 S0+S1 claim-list tip, #184 G1 FieldPolicy mask / second-auth honesty leftover, #179 G10 identity drift-fix honesty leftover, #181 G15 native-bridge honesty leftover, #177 G4 SPA verifier honesty leftover, #180 S0 getenv snapshot leftover, #171 G5 browser failed-publish leftover, and #178 claim-list tip). **Not** a full G17 close. **Not** a product SoD close. **Not** 等保 / 密评 / 密码机 / HSM. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. #99 catalog / assign / audit-admin ITs were **not** redone.

See [docs/g17-separation-honesty.md](../g17-separation-honesty.md). Keep **both** leftover rows: [g17-separation-of-duties.md](../g17-separation-of-duties.md) (#99) and this leftover. Keep g17-* siblings: [g17-admin-mfa.md](../g17-admin-mfa.md) (#96), [g17-admin-totp-enrollment.md](../g17-admin-totp-enrollment.md) (#118), [g17-totp-enrollment-ux.md](../g17-totp-enrollment-ux.md) (#133), [g17-admin-mfa-recovery-email.md](../g17-admin-mfa-recovery-email.md) (#154), [g17-mfa-recovery-email-honesty.md](../g17-mfa-recovery-email-honesty.md) (#172), [g17-vue-otp-writeback.md](../g17-vue-otp-writeback.md) (#158), [g17-gm-tls-dual-cert.md](../g17-gm-tls-dual-cert.md) (#110), [g17-gm-tls-nginx-dual-stack.md](../g17-gm-tls-nginx-dual-stack.md) (#129), [g17-gm-tls-nginx-handshake.md](../g17-gm-tls-nginx-handshake.md) (#160). Keep the #171 sibling [g5-browser-failed-publish.md](../g5-browser-failed-publish.md), the #177 sibling [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md), the #179 sibling [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md), the #180 sibling [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md), the #181 sibling [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md), the #183 sibling [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md), and the #184 sibling [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Keycloak | not listening | loopback `:8080` / `:8180` `/realms/nexus` |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g17-separation-honesty.sh
./scripts/g17-separation-honesty.sh --verify-report <out>/report.env
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/fake-live-pass.env
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/fake-g17-close.env
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/fake-p1-rewired.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g17-separation-honesty/`. A sanitized copy is [`g17-separation-honesty/agent-vm.env`](./g17-separation-honesty/agent-vm.env) once recorded. Walk JSON: [`g17-separation-honesty/walk.json`](../g17-separation-honesty/walk.json).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g17_close=CLOSED` rejected / fake P1 rewired rejected | `2026-09-22T20:10:50Z` |
| #99 catalog + exclusive pairs | **PASS** (inventory) | not a #99 IT redo |
| operator (`tenant_member`) not a platform duty | **true** | catalog completeness note |
| P1 `ALL_DEMO` rewired | **OPEN** | still bypasses every duty |
| Product duty-assign UI | **OPEN** | absent |
| Live admin / auditor / operator HTTP | **SKIP** (expected) | live JAR unset; IdP unreachable |
| live_*_observed | **false** | honesty gate requires `false` on SKIP |
| #96/#99/#110/#118/#129/#133/#154/#158/#160/#172 redo | **false** | sibling leftovers |
| 等保 / 密评 / 密码机 / HSM / G17 close / SoD close | **NOT_CLAIMED** | honesty gate enforces `false` / `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + live IdP admin / auditor / operator HTTP SoD walk
- Rewiring P1 tenant APIs off `ALL_DEMO` onto P2 duty roles
- Product UI to assign the three duties
- Qualified GM/T Provider, 密码机 / HSM, classified / 等保 / 密评 delivery
- Product G17 close
