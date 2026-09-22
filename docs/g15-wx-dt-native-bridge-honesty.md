# G15 leftover — WeChat / DingTalk prod-gap + native-bridge honesty beyond #167/#181

S0+S1 remainder after the #167 production-gap honesty leftover ([g15-wx-dt-prod-gap-honesty.md](./g15-wx-dt-prod-gap-honesty.md)) and the #181 native-bridge honesty leftover ([g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md)). Rebased onto `origin/main` `4c26d76` (Merge #186 RecoveryPort dry-run / not product HA honesty leftover). **This does not close product G15.** It does **not** close product G15 native-bridge. It does **not** accept WeChat / DingTalk production. It is **not** IPA/APK production accept. It extends honest **PASS/SKIP** beyond #167/#181.

This leftover does **not** redo #151 native-bridge stub product code, does **not** redo #167 prod-gap honesty product code, and does **not** redo #181 native-bridge honesty product code. It does **not** add a new harness route. F014 envelope on main is reused, not rewritten. Desktop ledger / form / todo pages are unchanged. G18 stays closed. P0 / M0 are not redone.

#167 already inventories the WeChat / DingTalk production-gap stub. #181 already asserts the #151 stub contract **PASS** and IPA/APK **SKIP**. The leftover here is the **combined honesty gate**: leftover harness contracts stay always-green **PASS**. Live WeChat / DingTalk production and store-ready IPA/APK are **PASS** only when a live host was present **and** a walk was observed. Missing live host is honest **SKIP**. It never fabricates `PASS`. Product G15 close stays `g15_close=NOT_CLAIMED`.

Keep **all sibling leftover rows** in [docs/README.md](./README.md): [g15-wx-dt-prod-gap-honesty](./g15-wx-dt-prod-gap-honesty.md) (#167), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [g15-native-bridge-stub](./g15-native-bridge-stub.md) (#151), [g15-wx-dt-prod-host](./g15-wx-dt-prod-host.md) (#132), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md), [g17-separation-honesty](./g17-separation-honesty.md), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md), [g5-browser-failed-publish](./g5-browser-failed-publish.md), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md), [g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md), and [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md). This file is the honesty leftover beside them.

Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker.

## What this leftover covers

| Path | Behavior |
|---|---|
| #167 prod-gap harness | Already on `main`. `g15_close=NOT_CLAIMED`. WeChat / DingTalk production stay SKIP unless an observed vendor walk. Do **not** redo `/m/wx-dt-prod-gap-harness`. |
| #181 native-bridge honesty | Already on `main`. Stub contract **PASS**. IPA/APK **SKIP**. Do **not** redo `/m/native-bridge-honesty-harness`. |
| #151 native-bridge stub | Already on `main`. Do **not** redo `/m/native-bridge-harness`. |
| Leftover harness contract | Always-green `wx_dt_harness_contract=PASS` and `native_bridge_harness_contract=PASS` when the #167/#181 leftover honesty fixtures still verify. |
| Honest WeChat / DingTalk SKIP | `wechat_miniprogram_production` / `dingtalk_miniprogram_production` are **PASS** only when a live vendor host was present **and** `*_walk_observed=true`. Missing JSAPI / servicewechat.com / h5.dingtalk.com is honest **SKIP**. |
| Honest IPA/APK SKIP | `ios_ipa_production` / `android_apk_production` are **PASS** only when Xcode/Gradle (or equivalent) was present **and** a native walk was observed. Missing live host is honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake WeChat/DingTalk/IPA PASS, rejects a claimed G15 close, rejects #151/#167/#181 redo flags, and requires parent leftover rows kept. |

No Docker. No new harness page. Do **not** run `./scripts/g15-wx-dt-prod-gap-honesty.sh` or `./scripts/g15-native-bridge-honesty.sh` as this leftover (parent `--verify-report` on honest fixtures is allowed as harness evidence).

## How to run

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

Always-green leftover units (does **not** rewrite #151/#167/#181 product modules):

```bash
cd web
npm test -- src/mobile/host/wxDtNativeBridgeHonesty.test.js
npm run assert:h5-wx-dt-native-bridge-honesty
```

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g15-wx-dt-prod-gap-honesty.sh
# ./scripts/g15-native-bridge-honesty.sh
```

See [engineering/g15-wx-dt-native-bridge-honesty-EVIDENCE.md](./engineering/g15-wx-dt-native-bridge-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g15-wx-dt-native-bridge-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G15 close | Explicitly `g15_close=NOT_CLAIMED`. Honesty gate rejects any other close claim. |
| WeChat / DingTalk production accept | Honest SKIP unless a live vendor host walk was observed. |
| Store-ready IPA / APK | Honest SKIP unless Xcode/Gradle + native walk was observed. Not IPA/APK. |
| Redo of #151 / #167 / #181 | Stub / honesty leftover product modules stay on those leftovers. |
| New harness route | No `/m/wx-dt-native-bridge-honesty-harness`. |
| G18 reopen / P0 / M0 redo | Closed / untouched / out of scope. |
| Desktop P3 | Untouched. |

## Honest leftovers (not claimed)

- WeChat 小程序 production accept (vendor JSAPI / servicewechat.com walk)
- DingTalk 小程序 production accept (DingTalk JSAPI / h5.dingtalk.com walk)
- Store-ready IPA / APK / TestFlight / Play
- WKWebView / Android WebView product host
- Product-wide G15 close
