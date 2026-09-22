# G15 leftover: H5 PKCE / miniprogram web-view remainder evidence

Recorded on this branch after rebase onto `origin/main` `1d499ab` (Merge #254 G2 H5/form.write/report leftover honesty after #236 Linux/ARM Profile leftover honesty after #251 G4 hot-purge/staging/PKCE leftover honesty after #243 G10 file/object reconcile leftover honesty after #241 SecretPort rotate/getenv leftover honesty after #235 G5 PublishHook/DDL leftover honesty / #233 G17 MFA/OTP leftover honesty / #238 G1 FieldPolicy mask leftover honesty / #239 G7 expense/travel query leftover honesty / #237 G9 Redis-down/invalidate leftover honesty / #232 G2 H5/form.write leftover honesty / #212 G7 withdraw browser-evidence honesty / #230 G4 hot-purge / P1 staging leftover honesty / #231 S0+S1 claim-list tip-sync / #229 G9 live multi-JVM / replica remainder leftover honesty). **Not** a product G15 close. **Not** WeChat / DingTalk production验收. **Not** IPA/APK production accept. **Not** a live Keycloak H5 laptop click. **Not** a redo of #117 / #120 / #126 / #198 / #220. **Not** a desktop P3 rewrite. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort` same-TX rules were not redesigned.

See [docs/g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md). Keep **ALL sibling leftover rows** in [docs/README.md](../README.md), including [g15-h5-pkce-browser.md](../g15-h5-pkce-browser.md) (#126), [g15-miniprogram-webview.md](../g15-miniprogram-webview.md) (#117), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), and [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| Node | v22.14.0 | from `node -v` on this VM |
| Vitest | 3.2.7 | `web/package.json` |
| Spring Boot parent | **4.1.1** | product BOM; unused for this contract |
| Runtime | local `npm test` + leftover script | No Docker / vendor SDK / Xcode / Android SDK / live Keycloak |

## Commands

```bash
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-g15-close.env fail
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-live-pkce-pass.env fail
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-wechat-pass.env fail
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-ipa-pass.env fail
./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-remainder-honesty/fixtures/fake-redone.env fail
```

Do **not** treat `./scripts/g15/run-h5-pkce-browser.sh`, `./scripts/g4-pkce-https-browser.sh`, `./scripts/g15-wx-dt-native-bridge-honesty.sh`, or `./scripts/g15-wx-dt-native-bridge-remainder.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g15-h5-pkce-miniprogram-remainder-honesty/`.

| Check | Result | Notes |
|---|---|---|
| #117 simulated 小程序 web-view harness | **PASS** | leftover contract still verifies; not production |
| #126 H5 PKCE Playwright/stub harness | **PASS** | leftover honest fixture still verifies; not live Keycloak |
| #120 HTTPS PKCE sibling | **PASS** | sibling leftover kept; not a live H5 click |
| #198 wx-dt/native-bridge honesty | **PASS** | leftover honest fixture still verifies |
| #220 wx-dt/native-bridge remainder | **PASS** | leftover honest fixture still verifies |
| Live Keycloak H5 PKCE | **SKIP** (expected) | no live Keycloak laptop walk |
| Live HTTPS Keycloak H5 click | **SKIP** (expected) | no live HTTPS Keycloak H5 walk |
| WeChat production | **SKIP** (expected) | no vendor JSAPI / servicewechat.com walk |
| DingTalk production | **SKIP** (expected) | no DingTalk JSAPI / h5.dingtalk.com walk |
| iOS IPA | **SKIP** (expected) | no Xcode / WKWebView / TestFlight walk |
| Android APK | **SKIP** (expected) | no Gradle / Android WebView / Play walk |
| Product G15 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #117 / #120 / #126 / #198 / #220 | not redone | `redone_117=false` … `redone_220=false` |
| G18 / P0 / M0 / AuditPort | unchanged | not reopened / not redone / same-TX untouched |
| Sibling leftover rows | kept | parents + #254 `g2-h5-every-page-form-write-report-remainder-honesty` + #236 `linux-arm-profile-remainder-honesty` + #251 `g4-hot-purge-staging-pkce-remainder-honesty` + #243 `g10-file-object-reconcile-remainder-honesty` + #241 `s0-secret-rotate-getenv-leftover-honesty` + #235 `g5-publishhook-ddl-remainder-honesty` + #233 `g17-mfa-recovery-vue-otp-remainder-honesty` + #238 `g1-fieldpolicy-mask-second-auth-remainder-honesty` + #239 `g7-query-expense-travel-honesty-remainder` + #237 `g9-redis-down-invalidate-remainder-honesty` + #232 `g2-h5-every-page-form-write-remainder-honesty` + #212 `g7-withdraw-browser-evidence-honesty` + #230 `g4-hot-purge-staging-leftover-honesty` + #229 `g9-live-multi-jvm-replica-remainder-honesty` + recent honesty leftovers |

## Honest leftovers

- Live Keycloak Authorization Code + PKCE human laptop click on `/m/*`
- Live HTTPS Keycloak H5 click
- WeChat / DingTalk production mini-program accept
- Vendor JSAPI / vendor domain allow
- Store-ready IPA / APK / TestFlight / Play
- Product-wide G15 close
