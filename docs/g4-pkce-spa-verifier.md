# G4 leftover — human notebook + live Keycloak CA / SPA-generated PKCE verifier

S0+S1 remainder after #108 / #120 / #125. **Not** a P0/M0 redo. Product **G18 stays closed**. This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules and does **not** claim a product-wide G4 close. Honesty leftover beyond this page (live Keycloak CA / SPA PKCE PASS only when observed; SKIP when unreachable) is [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md). Remainder honesty leftover beyond #177/#147 is [g4-pkce-spa-verifier-remainder-honesty.md](./g4-pkce-spa-verifier-remainder-honesty.md). This #147 leftover is not rewritten.

Already on `main` (do not redo):

- #108 HTTP-driver Authorization Code + PKCE (BFF mints `code_verifier`): [g4-pkce-login-audit.md](./g4-pkce-login-audit.md)
- #120 HTTPS loopback + cookie-honoring HTTP/2: [g4-pkce-https-browser.md](./g4-pkce-https-browser.md)
- #125 desktop Playwright click (stub IdP, BFF verifier): [g4-pkce-browser-click.md](./g4-pkce-browser-click.md)
- #126 H5 `/m/*` Playwright/stub (browser already mints S256): [g15-h5-pkce-browser.md](./g15-h5-pkce-browser.md)

The leftover here is the **evidence path** for a laptop browser that trusts the loopback Keycloak CA and a **SPA-generated** `code_verifier` (never minted by `authorize-url?pkce=s256` without `code_challenge`). Live Keycloak is **PASS only when OIDC discovery answers**. If Keycloak is unreachable, the harness writes an honest **SKIP** with a reason. That is not a product close.

## What this leftover covers

| Piece | Behaviour |
|---|---|
| SPA S256 mint | Same helper as H5 (`web/src/mobile/auth/pkce.js` `createS256`). Verifier stays in the page. RFC 7636 appendix B vector is asserted. |
| Client-held authorize-url | `GET /api/v1/auth/authorize-url?pkce=s256&code_challenge=…` echoes the challenge and **does not** return `code_verifier`. BFF mint without `code_challenge` stays on `main` (#108/#125). |
| Stub LOGIN | `SessionSpaHeldPkceLoginAuditTest` posts the SPA-held verifier and lands `p2.auth.login.accepted` on the in-memory chain (`c21a`). |
| Live Keycloak | Opt-in `LiveKeycloakSpaHeldPkceTest` (`NEXUS_KEYCLOAK_SMOKE=1` + reachable OIDC). This leftover **does not start** `kc.sh`. |
| Loopback CA notebook | How a human laptop trusts `#120` `.local/g4-https/server.crt` and clicks SPA PKCE. Not claimed as a walked click on this agent VM unless discovery is up. |
| Honesty gate | `--verify-report` rejects `live_keycloak_pkce=PASS` without discovery, rejects `g4_close=CLOSED`, and rejects secrets in the report. |

Default product `GET /api/v1/auth/authorize-url` still has no PKCE (M0-04 confidential smoke).

## Human laptop notebook (CA + SPA verifier)

These steps are the leftover notebook. They are **not** a claimed product walk on this agent VM when Keycloak is down.

1. Generate (or reuse) the #120 loopback cert. Do **not** rewrite that script:
   `./scripts/g4-pkce-https-certs.sh` then `source /tmp/g4-https.env`.
2. Trust `.local/g4-https/server.crt` in the laptop browser (Chrome → Authorities, or the OS trust store). Insecure-origin fallback is `--unsafely-treat-insecure-origin-as-secure=http://127.0.0.1:8180` and is **not** the primary path.
3. If a native Keycloak HTTPS leftover is already running (`nexus-p2-iam/scripts/start-native-keycloak-https.sh` from #120), open `https://127.0.0.1:5173/m/login`.
4. Click **PKCE 授权登录** (`data-testid=mobile-pkce-login`). The page calls `createS256()`, stores the verifier in `sessionStorage`, and requests `authorize-url?pkce=s256&code_challenge=…`. The response must **not** include `code_verifier`.
5. Complete the Keycloak login form. `/m/callback` POSTs `{ code, code_verifier }` and strips the query. Tokens stay out of the address bar.
6. Desktop `#125` `data-testid=pkce-login` remains BFF-shaped. Do not treat that click as this leftover.

This leftover’s required evidence is `./scripts/g4-pkce-spa-verifier.sh` (SPA mint + honesty + live PASS or SKIP). A missing live Keycloak is SKIP, not a fake PASS.

## How to run

```bash
./scripts/g4-pkce-spa-verifier.sh
./scripts/g4-pkce-spa-verifier.sh --verify-report \
  scripts/g4/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-pkce-spa-verifier.sh --verify-report \
  scripts/g4/fixtures/fake-live-pass.env
./scripts/g4-pkce-spa-verifier.sh --verify-report \
  scripts/g4/fixtures/fake-g4-close.env
```

Always-green Java (no Keycloak):

```bash
mvn -pl nexus-p2-iam -am test \
  -Dtest=PkceTest,AuthPkceApiTest,SessionPkceLoginAuditTest,SessionSpaHeldPkceLoginAuditTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Live (only when Keycloak is already up — this leftover does not start it):

```bash
export NEXUS_KEYCLOAK_SMOKE=1
# source /tmp/keycloak-smoke.env or /tmp/keycloak-https.env (0600, not git)
./scripts/g4-pkce-spa-verifier.sh
```

`--require-live` turns a missing live walk into FAIL. Default is honest SKIP.

See [engineering/g4-pkce-spa-verifier-EVIDENCE.md](./engineering/g4-pkce-spa-verifier-EVIDENCE.md).

## Linked traces

| Trace | Role |
|---|---|
| `0199a1c0-0000-7000-8000-00000000c214`–`c216` | #108 HTTP PKCE (unchanged) |
| `0199a1c0-0000-7000-8000-00000000c217`–`c218` | #120 HTTPS leftover (unchanged) |
| `0199a1c0-0000-7000-8000-00000000c219` | #125 desktop stub click (unchanged) |
| `0199a1c0-0000-7000-8000-00000000c21a` | This leftover: SPA-held verifier + stub LOGIN |
| `0199a1c0-0000-7000-8000-00000000c21b` | This leftover: live SPA-held PKCE (opt-in; SKIP if Keycloak is down) |

## Honest leftovers (not claimed)

| Still open | Why |
|---|---|
| Product G4 close | HTTP export wizard / P1 staging `audit-events`. This is one evidence path. |
| Human laptop click recorded here | Notebook is documented. This agent VM SKIP’d live Keycloak unless discovery answered. |
| Desktop BFF mint | `#125` still uses `authorize-url?pkce=s256` without `code_challenge`. Not changed. |
| `GET /api/v1/auth/callback` | Still does not carry the verifier. SPA uses POST `/login`. |
| Starting Keycloak | #108 / #120 own `kc.sh`. This leftover probes and SKIPs. |
| G17 / G6 / G18 reopen | Not this package. `AuditPort` same-TX rules untouched. |
