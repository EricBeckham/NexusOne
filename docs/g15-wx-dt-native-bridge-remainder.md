# G15 leftover — WeChat / DingTalk prod-gap + native-bridge remainder beyond #167/#181/#198

S0+S1 remainder after the #167 production-gap honesty leftover ([g15-wx-dt-prod-gap-honesty.md](./g15-wx-dt-prod-gap-honesty.md)), the #181 native-bridge honesty leftover ([g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md)), and the #198 combined honesty leftover ([g15-wx-dt-native-bridge-honesty.md](./g15-wx-dt-native-bridge-honesty.md)). Rebased onto `origin/main` `1c79ef3` (Merge #209 G9 Sentinel/Cluster leftover honesty after #214 tip-sync / #188 G2 ops pg_dump leftover honesty). **This does not close product G15.** It does **not** close product G15 native-bridge. It does **not** accept WeChat / DingTalk production. It is **not** IPA/APK production accept. It extends honest **PASS/SKIP** beyond #167/#181/#198.

This leftover does **not** redo #117 simulated 小程序 web-view, does **not** redo #132 WeChat/DingTalk production-host stub, does **not** redo #151 native-bridge stub product code, does **not** redo #167 prod-gap honesty product code, does **not** redo #181 native-bridge honesty product code, and does **not** redo #198 combined honesty leftover product code. It does **not** add a new harness route. F014 envelope on main is reused, not rewritten. Desktop ledger / form / todo pages are unchanged. G18 stays closed. P0 / M0 are not redone. `AuditPort.append` / `appendNewTransaction` same-TX rules are untouched.

#167 already inventories the WeChat / DingTalk production-gap stub. #181 already asserts the #151 stub contract **PASS** and IPA/APK **SKIP**. #198 already is the combined honesty gate. The leftover here is the **remainder honesty gate** beyond those three: leftover harness contracts stay always-green **PASS**. Live WeChat / DingTalk production and store-ready IPA/APK are **PASS** only when a live host was present **and** a walk was observed. Missing live host is honest **SKIP**. It never fabricates `PASS`. Product G15 close stays `g15_close=NOT_CLAIMED`.

Keep **ALL sibling leftover rows** in [docs/README.md](./README.md): [g15-wx-dt-prod-gap-honesty](./g15-wx-dt-prod-gap-honesty.md) (#167), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md) (#198), [g15-native-bridge-stub](./g15-native-bridge-stub.md) (#151), [g15-wx-dt-prod-host](./g15-wx-dt-prod-host.md) (#132), [g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md) (#188), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md) (#197), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md) (#191), [s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md) (#175), [g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md) (#174), [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md) (#169), and [linux-arm-profile-honesty](./linux-arm-profile-honesty.md) (#157). This file is the remainder leftover beside them. Do **not** drop the #188 `g2-ops-pg-dump-honesty` row or the #209 `g9-sentinel-cluster-remainder-honesty` row.

Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker.

## What this leftover covers

| Path | Behavior |
|---|---|
| #167 prod-gap harness | Already on `main`. `g15_close=NOT_CLAIMED`. WeChat / DingTalk production stay SKIP unless an observed vendor walk. Do **not** redo `/m/wx-dt-prod-gap-harness`. |
| #181 native-bridge honesty | Already on `main`. Stub contract **PASS**. IPA/APK **SKIP**. Do **not** redo `/m/native-bridge-honesty-harness`. |
| #198 combined honesty leftover | Already on `main`. Harness contracts **PASS**. Live host **SKIP**. Do **not** redo that leftover. |
| #151 native-bridge stub | Already on `main`. Do **not** redo `/m/native-bridge-harness`. |
| Remainder harness contract | Always-green `leftover_167_harness=PASS`, `leftover_181_harness=PASS`, and `leftover_198_harness=PASS` when the parent leftover honesty fixtures still verify. |
| Honest WeChat / DingTalk SKIP | `wechat_miniprogram_production` / `dingtalk_miniprogram_production` are **PASS** only when a live vendor host was present **and** `*_walk_observed=true`. Missing JSAPI / servicewechat.com / h5.dingtalk.com is honest **SKIP**. |
| Honest IPA/APK SKIP | `ios_ipa_production` / `android_apk_production` are **PASS** only when Xcode/Gradle (or equivalent) was present **and** a native walk was observed. Missing live host is honest **SKIP**. |
| Honesty gate | `--verify-report` rejects fake WeChat/DingTalk/IPA PASS, rejects a claimed G15 close, rejects #117/#132/#151/#167/#181/#198 redo flags, and requires parent leftover rows kept. |

No Docker. No new harness page. Do **not** run `./scripts/g15-wx-dt-prod-gap-honesty.sh`, `./scripts/g15-native-bridge-honesty.sh`, or `./scripts/g15-wx-dt-native-bridge-honesty.sh` as this leftover (parent `--verify-report` on honest fixtures is allowed as harness evidence).

## How to run

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

Always-green leftover units (does **not** rewrite #117/#132/#151/#167/#181/#198 product modules):

```bash
cd web
npm test -- src/mobile/host/wxDtNativeBridgeRemainder.test.js
npm run assert:h5-wx-dt-native-bridge-remainder
```

Parent leftover scripts stay as reproduction commands:

```bash
# do not run as this leftover
# ./scripts/g15-wx-dt-prod-gap-honesty.sh
# ./scripts/g15-native-bridge-honesty.sh
# ./scripts/g15-wx-dt-native-bridge-honesty.sh
```

See [engineering/g15-wx-dt-native-bridge-remainder-EVIDENCE.md](./engineering/g15-wx-dt-native-bridge-remainder-EVIDENCE.md). Live `report.env` is gitignored under `.local/g15-wx-dt-native-bridge-remainder/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G15 close | Explicitly `g15_close=NOT_CLAIMED`. Honesty gate rejects any other close claim. |
| WeChat / DingTalk production accept | Honest SKIP unless a live vendor host walk was observed. |
| Store-ready IPA / APK | Honest SKIP unless Xcode/Gradle + native walk was observed. Not IPA/APK. |
| Redo of #117 / #132 / #151 / #167 / #181 / #198 | Stub / honesty leftover product modules stay on those leftovers. `redone_117=false`, `redone_132=false`, `redone_151=false`, `redone_167=false`, `redone_181=false`, `redone_198=false`. |
| New harness route | No `/m/wx-dt-native-bridge-remainder-harness`. |
| G18 reopen / P0 / M0 redo / `AuditPort` same-TX | Closed / untouched / out of scope. |
| Desktop P3 | Untouched. |

## Honest leftovers (not claimed)

- WeChat 小程序 production accept (vendor JSAPI / servicewechat.com walk)
- DingTalk 小程序 production accept (DingTalk JSAPI / h5.dingtalk.com walk)
- Store-ready IPA / APK / TestFlight / Play
- WKWebView / Android WebView product host
- Product-wide G15 close
