# G15 leftover — WeChat / DingTalk production mini-program gap honesty evidence

Recorded on this branch after rebase onto `origin/main` `af36096` (Merge #169 G10 file-reconcile honesty). **Not** a product G15 close. **Not** WeChat / DingTalk production验收. **Not** a redo of #117 / #132 / #151. **Not** a desktop P3 rewrite. **Not** a G18 reopen.

See [docs/g15-wx-dt-prod-gap-honesty.md](../g15-wx-dt-prod-gap-honesty.md). Keep leftover rows [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) and [linux-arm-profile-honesty.md](../linux-arm-profile-honesty.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| Node | v22.14.0 | from `node -v` |
| Vitest | 3.2.7 | `web/package.json` |
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
| Honest leftover fixture | PASS | `g15_close=NOT_CLAIMED` |
| Fake `g15_close=CLOSED` | FAIL (expected) | honesty gate |
| Fake WeChat production PASS | FAIL (expected) | no `wechat_walk_observed` |
| `wechatDingTalkProdGapHonesty.test.js` | PASS 4 | same F014 envelope; vendor origins denied |
| host units | PASS | includes #117/#132/#151 leftover tests |
| `assert:h5-wx-dt-prod-gap` | PASS | #117/#132/#151 leftover; #169/#157 rows kept |
| Product G15 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |

## Honest leftovers

- WeChat / DingTalk production mini-program accept
- Vendor JSAPI / vendor domain allow
- Store-ready APP WebView native package
- Product-wide G15 close
