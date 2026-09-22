# G15 leftover — WeChat / DingTalk production mini-program acceptance gap honesty

S0+S1 remainder after #117 simulated 小程序 web-view, #132 WeChat/DingTalk production-host **stub**, and #151 APP WebView native-bridge **stub**. Rebased onto `origin/main` `58e83f1` (Merge #166 G4 export-wizard complete-deny leftover). **This does not close product G15.** It is a **contract stub** that inventories the production 小程序 gap. It is **not** production验收.

Keep leftover rows in [docs/README.md](./README.md): [g4-export-wizard-deny](./g4-export-wizard-deny.md) (#166) and [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md) (#169). Do not drop sibling G15 rows [g15-wx-dt-prod-host.md](./g15-wx-dt-prod-host.md) / [g15-native-bridge-stub.md](./g15-native-bridge-stub.md). Follow-on honest PASS/SKIP leftover (does **not** redo this leftover): [g15-wx-dt-native-bridge-honesty.md](./g15-wx-dt-native-bridge-honesty.md).

Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL 15. No Docker. Desktop ledger / form / todo pages are unchanged. G18 stays closed. P0 / M0 are not redone. F014 envelope on main is reused, not rewritten.

## What this leftover is (and is not)

| Surface | Route / artifact | Behavior |
|---|---|---|
| Honesty contract stub | `web/src/mobile/host/wechatDingTalkProdGapHonesty.js` | Same HostBridgePort channels as #132. `productionAccepted` / `vendorJsapiLoaded` stay `false`. `g15Close=NOT_CLAIMED`. |
| Honesty inventory page | `/m/wx-dt-prod-gap-harness` | Gap list + origin/channel reject probes. Banner states **不是生产验收**. Not a second vendor phone chrome. |
| Honesty gate | `scripts/g15-wx-dt-prod-gap-honesty.sh --verify-report` | Rejects `g15_close=CLOSED` and `wechat/dingtalk_miniprogram_production=PASS` without an observed vendor walk. |
| Same envelope | #132 `wechatDingTalkHost` | Vendor origins (`servicewechat.com`, `h5.dingtalk.com`, …) stay `origin_rejected`. `eval` / tokens stay rejected. |

`describeProdGapContract().wechatMiniProgramProduction` / `dingtalkMiniProgramProduction` are always `SKIP`.

## Not claimed vs leftovers already on main

| Existing leftover | What it is | What this stub is not |
|---|---|---|
| #117 [g15-miniprogram-webview.md](./g15-miniprogram-webview.md) | Simulated 小程序 web-view parent | Not a mini-program. Not production. |
| #132 [g15-wx-dt-prod-host.md](./g15-wx-dt-prod-host.md) | WeChat / DingTalk production-host **stub** | Not vendor JSAPI. Production still **not** accepted. This leftover does **not** redo `/m/wx-dt-harness`. |
| #151 [g15-native-bridge-stub.md](./g15-native-bridge-stub.md) | iOS WK / Android JS native-bridge stub | Not store-ready. Not WeChat / DingTalk production. |
| #162 [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md) | FieldPolicy mask / 二次授权 honesty | Orthogonal. Row kept. |
| #158 [g17-vue-otp-writeback.md](./g17-vue-otp-writeback.md) | Vue Keycloak otp write-back honesty | Orthogonal. Row kept. |
| #160 [g17-gm-tls-nginx-handshake.md](./g17-gm-tls-nginx-handshake.md) | Tongsuo/Nginx GMTLS handshake honesty | Orthogonal. Row kept. |
| #161 [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) | live multi-JVM / replica promotion honesty | Orthogonal. Row kept. |
| #163 [g2-h5-every-page.md](./g2-h5-every-page.md) | H5 every-page SurfaceGuard honesty | Orthogonal. Row kept. |
| #164 [g6-recovery-port-honesty.md](./g6-recovery-port-honesty.md) | RecoveryPort still dry-run honesty | Orthogonal. Row kept. |
| #157 [linux-arm-profile-honesty.md](./linux-arm-profile-honesty.md) | TARGET-ARM-01 / supply-chain csv honesty | Orthogonal. Row kept. |
| #169 [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) | file metadata ↔ object reconcile honesty | Orthogonal. Row kept. |
| #166 [g4-export-wizard-deny.md](./g4-export-wizard-deny.md) | HTTP export-wizard complete-deny leftover | Orthogonal. Row kept. |

Envelope (unchanged from [g15-h5-mobile.md](./g15-h5-mobile.md)):

```text
{
  source: "nexus-h5" | "nexus-host",
  channel: "login" | "route" | "file" | "close",
  requestId: "h5-…",
  payload: { action, path?, returnTo?, fileId?, fileName? }
}
```

Business pages still do not branch on WeChat / DingTalk (H5 规范 §80.2). Host differences stay in `web/src/mobile/host/`.

## Honest leftovers (not claimed)

| Item | Status |
|---|---|
| WeChat 小程序 production accept | **Not accepted.** No vendor JSAPI, no servicewechat.com allow, no WeChat domain walk. |
| DingTalk 小程序 production accept | **Not accepted.** No DingTalk JSAPI, no h5.dingtalk.com allow. |
| APP WebView store-ready native package | **Open.** Native-bridge stub is [g15-native-bridge-stub.md](./g15-native-bridge-stub.md). |
| Product G15 | **Open.** Honesty gate rejects `g15_close=CLOSED`. |
| G18 / P0 / M0 | Closed or out of scope. Not redone. |
| Desktop P3 | Untouched. |

## How to run

```bash
./scripts/g15-wx-dt-prod-gap-honesty.sh
./scripts/g15-wx-dt-prod-gap-honesty.sh --verify-report \
  scripts/g15-wx-dt-prod-gap-honesty/fixtures/honest-leftover.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g15-wx-dt-prod-gap-honesty.sh --verify-report \
  scripts/g15-wx-dt-prod-gap-honesty/fixtures/fake-g15-close.env fail
./scripts/g15-wx-dt-prod-gap-honesty.sh --verify-report \
  scripts/g15-wx-dt-prod-gap-honesty/fixtures/fake-production-pass.env fail
```

```bash
cd web
npm test -- src/mobile/host/wechatDingTalkProdGapHonesty.test.js
npm run assert:h5-wx-dt-prod-gap
```

Open `http://127.0.0.1:5173/m/wx-dt-prod-gap-harness`. The page is a **stub**. It is not WeChat / DingTalk production.

See [engineering/g15-wx-dt-prod-gap-honesty-EVIDENCE.md](./engineering/g15-wx-dt-prod-gap-honesty-EVIDENCE.md).
