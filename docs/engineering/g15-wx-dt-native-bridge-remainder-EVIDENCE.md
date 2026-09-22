# G15 leftover: WeChat / DingTalk prod-gap + native-bridge remainder evidence

Recorded on this branch after rebase onto `origin/main` `c1d3150` (Merge #210 G17 Tongsuo/Nginx GMTLS handshake leftover honesty after #217 G5 PublishHook real-DDL leftover honesty / #209 G9 Sentinel/Cluster leftover honesty). **Not** a product G15 close. **Not** a product G15 native-bridge close. **Not** WeChat / DingTalk production验收. **Not** IPA/APK production accept. **Not** a redo of #117 / #132 / #151 / #167 / #181 / #198. **Not** a desktop P3 rewrite. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort` same-TX rules were not redesigned.

See [docs/g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md). Keep **ALL sibling leftover rows** in [docs/README.md](../README.md), including [g15-wx-dt-prod-gap-honesty.md](../g15-wx-dt-prod-gap-honesty.md) (#167), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), and [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| Node | v22.14.0 | from `node -v` on this VM |
| Vitest | 3.2.7 | `web/package.json` |
| Spring Boot parent | **4.1.1** | product BOM; unused for this contract |
| Runtime | local `npm test` + leftover script | No Docker / Playwright / vendor SDK / Xcode / Android SDK |

## Commands

```bash
./scripts/g15-wx-dt-native-bridge-remainder.sh
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report <out>/report.env
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/fake-g15-close.env fail
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/fake-wechat-pass.env fail
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/fake-ipa-pass.env fail
./scripts/g15-wx-dt-native-bridge-remainder.sh --verify-report \
  scripts/g15-wx-dt-native-bridge-remainder/fixtures/fake-redone.env fail
```

Do **not** treat `./scripts/g15-wx-dt-prod-gap-honesty.sh`, `./scripts/g15-native-bridge-honesty.sh`, or `./scripts/g15-wx-dt-native-bridge-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g15-wx-dt-native-bridge-remainder/`.

| Check | Result | Notes |
|---|---|---|
| #167 wx-dt prod-gap harness | **PASS** | leftover honest fixture still verifies; not a product redo |
| #181 native-bridge harness | **PASS** | leftover honest fixture still verifies; stub contract PASS |
| #198 wx-dt/native-bridge honesty | **PASS** | leftover honest fixture still verifies |
| WeChat production | **SKIP** (expected) | no vendor JSAPI / servicewechat.com walk |
| DingTalk production | **SKIP** (expected) | no DingTalk JSAPI / h5.dingtalk.com walk |
| iOS IPA | **SKIP** (expected) | no Xcode / WKWebView / TestFlight walk |
| Android APK | **SKIP** (expected) | no Gradle / Android WebView / Play walk |
| Product G15 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #117 / #132 / #151 / #167 / #181 / #198 | not redone | `redone_117=false` … `redone_198=false` |
| G18 / P0 / M0 / AuditPort | unchanged | not reopened / not redone / same-TX untouched |
| Sibling leftover rows | kept | parents + #188 `g2-ops-pg-dump-honesty` + #209 `g9-sentinel-cluster-remainder-honesty` + #217 `g5-publishhook-ddl-honesty` + #210 `g17-gm-tls-nginx-handshake-honesty` + recent honesty leftovers |

## Honest leftovers

- WeChat / DingTalk production mini-program accept
- Vendor JSAPI / vendor domain allow
- Store-ready IPA / APK / TestFlight / Play
- WKWebView / Android WebView product host
- Product-wide G15 close
