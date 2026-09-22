# G17 leftover — Vue page Keycloak otp write-back / authenticator enrollment bridge honesty

S0+S1 remainder after admin MFA (#96), native Keycloak TOTP enrollment (#118), and in-app enrollment UX (#133). Rebased onto `main` `6487b87` (after #153 claim-list tip alignment). **G18 stays closed.** Keep both leftover rows (`g7-withdraw-browser` and `g17-vue-otp-writeback`) in [docs/README.md](./README.md). This slice does **not** redo `AdminMfaPolicy`, the #118 HTTP driver, the #133 copy / error paths, or #154 recovery/email MFA. It does **not** claim 等保 / 密评 / 完整 G17. G17 close is **NOT_CLAIMED**. Recovery codes and email MFA stay the sibling leftover [g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md).

#133 proved the in-app authenticator page: Vue copy, 6-digit confirm, and error paths. That leftover still does **not** write a Keycloak `otp` credential. #118 remains the native Keycloak `CONFIGURE_TOTP` HTTP-driver walk. The leftover here is the **honesty bridge**: Vue confirm success ≠ Keycloak otp write-back.

If live Keycloak write-back is unavailable (no issuer, no OIDC discovery, or no product Vue write-back endpoint), the harness records an honest **SKIP** with a reason. It never fabricates `PASS`.

## What this leftover covers

| Surface | Behavior |
|---|---|
| Bridge contract | `web/src/auth/totpWriteback.js` — `describeOtpWritebackBridge` / `attemptVueOtpWriteback`. Vue confirm stays local. |
| Vue honesty panel | `/p2/mfa/enroll` `data-testid=totp-writeback-panel` / `totp-writeback-status` / `totp-writeback-reason`. Does not replace #133 start / confirm / error testids. |
| Product write-back API | **Absent.** There is no `POST /api/v1/auth/mfa/enroll` that stores a Keycloak otp credential. |
| Live probe | OIDC discovery against `NEXUS_KEYCLOAK_ISSUER` or loopback `:8080` / `:8180` `/realms/nexus`. |
| Always-green | Vitest + honesty assert. No JAR, no Docker, no Keycloak required. |
| Honesty gate | `--verify-report` rejects fake `PASS` without a reachable issuer + product endpoint + stored credential, and rejects 等保 / 密评 / HSM / G17-close / G18-reopen / recovery-code / email-MFA claims. |

`PASS` requires all of: reachable Keycloak 26.5.2 issuer, OIDC discovery, a product Vue write-back endpoint, and a visible stored otp credential. This leftover does not add that product endpoint.

## How to run

```bash
# Always-green + live probe (honest SKIP when Keycloak is down)
./scripts/g17-vue-otp-writeback.sh

# Honesty gate only
./scripts/g17-vue-otp-writeback.sh --verify-report \
  scripts/g17-vue-otp-writeback/fixtures/honest-skip.env
```

Equivalent always-green:

```bash
cd web
npm install
npm run test:g17-totp-enroll
npm run assert:g17-totp-enroll
npm run test:g17-vue-otp-writeback
npm run assert:g17-vue-otp-writeback
```

If live Keycloak 26.5.2 is later listening on loopback:

```bash
export NEXUS_KEYCLOAK_ISSUER=http://127.0.0.1:8080/realms/nexus
./scripts/g17-vue-otp-writeback.sh
```

Discovery may become `PASS` while write-back stays **SKIP** (`product_vue_writeback_endpoint_absent`). That is still honest. It is **not** a #118 redo.

## Evidence

Fill from `./scripts/g17-vue-otp-writeback.sh`. Artifact: [g17-vue-otp-writeback/walk.json](./g17-vue-otp-writeback/walk.json). Live `report.env` is gitignored under `.local/g17-vue-otp-writeback/`. See [engineering/g17-vue-otp-writeback-EVIDENCE.md](./engineering/g17-vue-otp-writeback-EVIDENCE.md).

| Item | Value |
|---|---|
| JDK / Boot | OpenJDK **21** / Spring Boot **4.1.1** (unchanged; this leftover does not boot the JAR) |
| Node | **22** |
| Keycloak | probe only (26.5.2 when present). This VM: **not listening** |
| Default stub login | unchanged (`acr=mfa`) — G18 stays closed |
| `#96` / `#118` / `#133` | **not redone** |
| `AuditPort` | **untouched** |
| Docker | no |

| Check | Result |
|---|---|
| Vue confirm still local (`confirmEnrollment`) | always-green |
| Write-back without enrolled session | `SKIP` `vue_confirm_is_local_only` |
| Live issuer unreachable | honest `SKIP` `keycloak_issuer_unreachable` |
| Live issuer up, no product endpoint | honest `SKIP` `product_vue_writeback_endpoint_absent` |
| Fake `PASS` / 等保 / 密评 / G17-close fixtures | rejected |
| Recovery codes / email MFA | **NOT_CLAIMED** (sibling leftover [g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md)) |

## Honest leftovers (not claimed)

| Still open | Why |
|---|---|
| Product Vue page writing otp into Keycloak | No `POST /api/v1/auth/mfa/enroll`. #118 HTTP driver remains the credential walk. |
| Recovery codes | Sibling leftover [g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md). **NOT_CLAIMED** here. |
| Email MFA product acceptance | Sibling leftover [g17-admin-mfa-recovery-email.md](./g17-admin-mfa-recovery-email.md). **NOT_CLAIMED** here. |
| SMS MFA / WebAuthn / passkeys | Not implemented. |
| Forced OTP at the Keycloak flow layer | Same as #118: product gate is P2. |
| Full G17 / 等保 / 密评 / 密码机 / 真实 Tongsuo 握手 | **NOT_CLAIMED.** 三权 is [g17-separation-of-duties.md](./g17-separation-of-duties.md). Tongsuo leftover is [g17-gm-tls-nginx-dual-stack.md](./g17-gm-tls-nginx-dual-stack.md). |
| G18 reopen | **No.** Default stub token still fabricates MFA. |
| P0/M0 redo, Docker | Out of scope |
