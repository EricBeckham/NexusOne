# G17 Vue Keycloak otp write-back honesty leftover — evidence

Recorded on this branch from `main` `7f97ec1` (after #151 native-bridge stub + #156 P1 staging audit-events). **Not** a full G17 close. **Not** 等保 / 密评 / 密码机 / HSM. **Not** a G18 reopen. `#96` / `#118` / `#133` were not redone. Recovery codes / email MFA are not this leftover. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g17-vue-otp-writeback.md](../g17-vue-otp-writeback.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04 | product baseline |
| Node | 22.14.0 | always-green Vitest + probe |
| Spring Boot parent | **4.1.1** | unchanged |
| Keycloak | not listening on `:8080` / `:8180` | live write-back **SKIP** |
| Runtime | `./scripts/g17-vue-otp-writeback.sh` | No Docker / Compose |

## Commands

```bash
./scripts/g17-vue-otp-writeback.sh
./scripts/g17-vue-otp-writeback.sh --verify-report \
  scripts/g17-vue-otp-writeback/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-vue-otp-writeback.sh --verify-report \
  scripts/g17-vue-otp-writeback/fixtures/fake-pass.env
./scripts/g17-vue-otp-writeback.sh --verify-report \
  scripts/g17-vue-otp-writeback/fixtures/fake-claims.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g17-vue-otp-writeback/`. A sanitized copy is [`g17-vue-otp-writeback/walk.json`](../g17-vue-otp-writeback/walk.json).

| Check | Result | Notes |
|---|---|---|
| Vue confirmEnrollment (local) | pending record | #133 leftover unchanged |
| Live Keycloak OIDC discovery | **SKIP** (expected) | `127.0.0.1:8080` / `:8180` not listening |
| Vue → Keycloak otp write-back | **SKIP** | `keycloak_issuer_unreachable` (and product endpoint absent) |
| Fake PASS / 等保 / 密评 / G17-close | rejected | honesty gate |
| Recovery codes / email MFA | **NOT_CLAIMED** | orthogonal leftover |
| G18 / AuditPort | unchanged | not reopened |

## Honest leftovers

- Product Vue write-back endpoint that stores a Keycloak otp credential
- Human laptop + live Keycloak CA click of `/p2/mfa/enroll` that actually writes otp
- Recovery codes / email MFA product acceptance
- SMS MFA / WebAuthn / passkeys
- Qualified 等保 / 密评 / 密码机 / HSM / 完整 G17
