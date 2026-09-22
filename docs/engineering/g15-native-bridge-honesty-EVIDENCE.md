# G15 leftover — APP WebView native-bridge honesty evidence

Recorded on this branch after rebase onto `origin/main` `476ceaa` (Merge #177 G4 Keycloak SPA PKCE verifier honesty leftover after #180). **Not** a product G15 close. **Not** a product G15 native-bridge close. **Not** IPA/APK production accept. **Not** a redo of #114 / #117 / #132 / #151 / #167. **Not** a desktop P3 rewrite. **Not** a G18 reopen.

See [docs/g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md). Keep **all sibling leftover rows** [g15-native-bridge-stub.md](../g15-native-bridge-stub.md), [g15-wx-dt-prod-gap-honesty.md](../g15-wx-dt-prod-gap-honesty.md), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md), [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md), [g4-pkce-spa-verifier.md](../g4-pkce-spa-verifier.md), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md), [g7-withdraw-browser.md](../g7-withdraw-browser.md), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md), and [g7-query-expense-travel.md](../g7-query-expense-travel.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| Node | v22.14.0 | from `node -v` |
| Vitest | 3.2.7 | `web/package.json` |
| Spring Boot parent | **4.1.1** | product BOM; unused for this contract |
| Runtime | local `npm test` + leftover script | No Docker / Playwright / Xcode / Android SDK |

## Commands

```bash
./scripts/g15-native-bridge-honesty.sh
```

## Results (agent VM)

Filled after the honesty script on this VM.

| Check | Result | Notes |
|---|---|---|
| Honest leftover fixture | PASS | `g15_close=NOT_CLAIMED` · `stub_contract=PASS` |
| Fake `g15_close=CLOSED` | FAIL (expected) | honesty gate |
| Fake IPA/APK / store-ready PASS | FAIL (expected) | no `ios_walk_observed` / `android_walk_observed` |
| `nativeBridgeHonesty.test.js` | PASS 4 | same F014 envelope; IPA/APK SKIP |
| host units | PASS 50 | includes #114/#117/#132/#151/#167 leftover tests |
| `assert:h5-native-bridge-honesty` | PASS | #114/#117/#132/#151/#167 leftover; both README rows kept |
| `assert:h5-native-bridge` | PASS | #151 stub leftover not redone |
| `assert:h5-wx-dt-prod-gap` | PASS | #167 leftover row kept |
| vite build | PASS | NativeBridgeHonestyPage emitted (131 modules) |
| Product G15 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |

## Honest leftovers

- Store-ready IPA / APK / TestFlight / Play
- WKWebView / Android WebView product host
- Xcode / Gradle project
- WeChat / DingTalk production mini-program accept
- Product-wide G15 close
