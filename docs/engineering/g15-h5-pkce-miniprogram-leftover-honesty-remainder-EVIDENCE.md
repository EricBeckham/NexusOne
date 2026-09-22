# G15 leftover: H5 PKCE / miniprogram leftover honesty remainder evidence

Recorded on this branch after rebase onto `origin/main` `2ac6326` (Merge #263 Linux/ARM leftover remainder after #262 G10 leftover honesty / #259 G9 leftover remainder / #249 G17 SoD leftover honesty after #240 G15 H5/PKCE leftover honesty). **Not** a product G15 close. **Not** WeChat / DingTalk production验收. **Not** IPA/APK production accept. **Not** a live Keycloak H5 laptop click. **Not** a redo of #117 / #120 / #126 / #198 / #220 / #240. **Not** a desktop P3 rewrite. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort` same-TX rules were not redesigned. `#240 harness PASS` is **not** a live walk.

See [docs/g15-h5-pkce-miniprogram-leftover-honesty-remainder.md](../g15-h5-pkce-miniprogram-leftover-honesty-remainder.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g15-h5-pkce-browser.md](../g15-h5-pkce-browser.md) (#126), [g15-miniprogram-webview.md](../g15-miniprogram-webview.md) (#117), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), this leftover, [g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md) (#249), [linux-arm-profile-leftover-remainder-honesty.md](../linux-arm-profile-leftover-remainder-honesty.md) (#263), [g10-file-object-reconcile-leftover-honesty.md](../g10-file-object-reconcile-leftover-honesty.md) (#262), [g9-leftover-honesty-remainder.md](../g9-leftover-honesty-remainder.md) (#259), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), and the other leftover rows already on `main`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Node | local leftover script | unused for this leftover collect |
| Runtime | leftover honesty inventory only | No Docker / vendor SDK / Xcode / Android SDK / live Keycloak |

## Commands

```bash
./scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh
./scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh --verify-report <out>/report.env
./scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-g15-close.env fail
./scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-live-pkce-pass.env fail
./scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-wechat-pass.env fail
./scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-ipa-pass.env fail
./scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-redone.env fail
./scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder.sh --verify-report \
  scripts/g15-h5-pkce-miniprogram-leftover-honesty-remainder/fixtures/fake-harness-as-live.env fail
```

Do **not** treat `./scripts/g15-h5-pkce-miniprogram-remainder-honesty.sh`, `./scripts/g15-wx-dt-native-bridge-remainder.sh`, `./scripts/g15-wx-dt-native-bridge-honesty.sh`, or `./scripts/g15/run-h5-pkce-browser.sh` as this leftover.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g15-h5-pkce-miniprogram-leftover-honesty-remainder/`. A sanitized copy is [`g15-h5-pkce-miniprogram-leftover-honesty-remainder/agent-vm.env`](./g15-h5-pkce-miniprogram-leftover-honesty-remainder/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | PASS / expected FAIL | leftover #240/#220/#198 cells PASS; fake close / fake live PASS / fake redo / fake harness-as-live rejected |
| leftover #240 honesty cells | PASS | parent leftover honesty fixtures stay honest; harness PASS is not a live walk |
| leftover #220 honesty cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #198 honesty cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| Live Keycloak H5 PKCE | SKIP | no live Keycloak laptop walk; #240 harness PASS is not that walk |
| Live HTTPS Keycloak H5 click | SKIP | no live HTTPS Keycloak H5 walk |
| WeChat production | SKIP | no vendor JSAPI / servicewechat.com walk |
| DingTalk production | SKIP | no DingTalk JSAPI / h5.dingtalk.com walk |
| iOS IPA | SKIP | no Xcode / WKWebView / TestFlight walk |
| Android APK | SKIP | no Gradle / Android WebView / Play walk |
| Product G15 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #117 / #120 / #126 / #198 / #220 / #240 | not redone | `redone_117=false` … `leftover_240_redone=false` |
| G18 / P0 / M0 / AuditPort | unchanged | not reopened / not redone / same-TX untouched |
| Sibling leftover rows | kept | parents + #249 `g17-separation-leftover-honesty-remainder` + #254 `g2-h5-every-page-form-write-report-remainder-honesty` + #236 `linux-arm-profile-remainder-honesty` + #251 `g4-hot-purge-staging-pkce-remainder-honesty` + #243 `g10-file-object-reconcile-remainder-honesty` + #241 `s0-secret-rotate-getenv-leftover-honesty` + recent honesty leftovers |

## Honest leftovers

- Live Keycloak Authorization Code + PKCE human laptop click on `/m/*`
- Live HTTPS Keycloak H5 click
- WeChat / DingTalk production mini-program accept
- Vendor JSAPI / vendor domain allow
- Store-ready IPA / APK / TestFlight / Play
- Product-wide G15 close
- Redo of #117 / #120 / #126 / #198 / #220 / #240
