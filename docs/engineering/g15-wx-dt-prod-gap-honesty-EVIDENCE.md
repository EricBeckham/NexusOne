# G15 leftover — WeChat / DingTalk production mini-program gap honesty evidence

Recorded on this branch after rebase onto `origin/main` `845df2b` (Merge #160 Tongsuo/Nginx GMTLS handshake honesty; includes #158 Vue otp write-back leftover). **Not** a product G15 close. **Not** WeChat / DingTalk production验收. **Not** a redo of #117 / #132 / #151. **Not** a desktop P3 rewrite. **Not** a G18 reopen.

See [docs/g15-wx-dt-prod-gap-honesty.md](../g15-wx-dt-prod-gap-honesty.md). Keep leftover rows [g17-gm-tls-nginx-handshake.md](../g17-gm-tls-nginx-handshake.md) and [g17-vue-otp-writeback.md](../g17-vue-otp-writeback.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| Node | pending this run | from `web/package.json` / `node -v` |
| Vitest | pending this run | `web/package.json` |
| Spring Boot parent | **4.1.1** | product BOM; unused for this contract |
| Runtime | local `npm test` + leftover script | No Docker / Playwright / vendor SDK |

## Commands

```bash
./scripts/g15-wx-dt-prod-gap-honesty.sh
```

## Results (agent VM)

Filled after the honesty script on this VM.

| Check | Result | Notes |
|---|---|---|
| Honest leftover fixture | pending | `g15_close=NOT_CLAIMED` |
| Fake `g15_close=CLOSED` | pending FAIL | honesty gate |
| Fake WeChat production PASS | pending FAIL | no `wechat_walk_observed` |
| `wechatDingTalkProdGapHonesty.test.js` | pending | same F014 envelope; vendor origins denied |
| `assert:h5-wx-dt-prod-gap` | pending | #117/#132/#151 leftover; #158/#162 rows kept |
| Product G15 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |

## Honest leftovers

- WeChat / DingTalk production mini-program accept
- Vendor JSAPI / vendor domain allow
- Store-ready APP WebView native package
- Product-wide G15 close
