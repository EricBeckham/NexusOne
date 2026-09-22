# G15 leftover: WeChat / DingTalk prod-gap + native-bridge honesty evidence

Recorded on this branch after rebase onto `origin/main` `eb8a151` (Merge #193 Linux/ARM leftover honesty). **Not** a product G15 close. **Not** a product G15 native-bridge close. **Not** WeChat / DingTalk production验收. **Not** IPA/APK production accept. **Not** a redo of #151 / #167 / #181. **Not** a desktop P3 rewrite. **Not** a G18 reopen. **Not** a P0/M0 redo.

See [docs/g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md). Keep **all sibling leftover rows** in [docs/README.md](../README.md), including [g15-wx-dt-prod-gap-honesty.md](../g15-wx-dt-prod-gap-honesty.md) (#167), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), and [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| Node | v22.14.0 | from `node -v` |
| Vitest | 3.2.7 | `web/package.json` |
| Spring Boot parent | **4.1.1** | product BOM; unused for this contract |
| Runtime | local `npm test` + leftover script | No Docker / Playwright / vendor SDK / Xcode / Android SDK |

## Commands

```bash
./scripts/g15-wx-dt-native-bridge-honesty.sh
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report <out>/report.env
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-g15-close.env fail
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-wechat-pass.env fail
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-ipa-pass.env fail
./scripts/g15-wx-dt-native-bridge-honesty.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-honesty/fixtures/fake-redone.env fail
```

Do **not** treat `./scripts/g15-wx-dt-prod-gap-honesty.sh` or `./scripts/g15-native-bridge-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g15-wx-dt-native-bridge-honesty/`.

| Check | Result | Notes |
|---|---|---|
| #167 wx-dt prod-gap harness | **PASS** | leftover honest fixture still verifies; not a product redo |
| #181 native-bridge harness | **PASS** | leftover honest fixture still verifies; stub contract PASS |
| WeChat production | **SKIP** (expected) | no vendor JSAPI / servicewechat.com walk |
| DingTalk production | **SKIP** (expected) | no DingTalk JSAPI / h5.dingtalk.com walk |
| iOS IPA | **SKIP** (expected) | no Xcode / WKWebView / TestFlight walk |
| Android APK | **SKIP** (expected) | no Gradle / Android WebView / Play walk |
| Product G15 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #151 / #167 / #181 | not redone | `redone_151=false`, `redone_167=false`, `redone_181=false` |
| G18 / P0 / M0 | unchanged | not reopened / not redone |
| Sibling leftover rows | kept | parents + G15 stubs + recent honesty leftovers |

## Honest leftovers

- WeChat / DingTalk production mini-program accept
- Vendor JSAPI / vendor domain allow
- Store-ready IPA / APK / TestFlight / Play
- WKWebView / Android WebView product host
- Product-wide G15 close
