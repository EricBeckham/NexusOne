# G4 leftover — desktop human/browser PKCE click

S0+S1 remainder. **Not** a P0/M0 redo. Product **G18 stays closed**. This package does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules and does **not** claim full G4 or G17.

HTTP-driver Authorization Code + PKCE on the hash chain is already on `main`:

- #108 form driver (not Chromium): [g4-pkce-login-audit.md](./g4-pkce-login-audit.md)
- #120 HTTPS loopback + cookie-honoring HTTP/2 (optional Chrome CDP): [g4-pkce-https-browser.md](./g4-pkce-https-browser.md)

The leftover here is a **desktop Playwright click walk**: Vue `data-testid=pkce-login` → stub IdP login form (`data-testid=idp-login`) → `/callback` posts `code` + `code_verifier` → permission grant click → `GET /api/v1/audit/traces/{traceId}` shows `p2.auth.login.accepted` and `p2.permission.granted` on the same AuditPort-shaped hash chain.

The walk uses the **G7 browser-withdraw pattern**: stub login/IdP API + Vite preview + Playwright + **system Chrome**, no Docker, no product JAR.

**Not claimed:** product G4 close, human laptop + live Keycloak CA trust, SPA-generated verifier, H5 `/m/*` PKCE (G15 leftover), AuditPort same-TX redesign, G18 reopen.

## What is proven

1. Vitest + happy-dom: desktop login card `data-testid=pkce-login` calls `GET /api/v1/auth/authorize-url?pkce=s256` and assigns the authorize URL; callback helper POSTs `code` + `code_verifier`.
2. Playwright + system Chrome against Vite preview:
   - Click **开始 PKCE 登录**
   - Land on stub IdP HTML form (Keycloak-shaped username/password)
   - Click **Sign In** (`data-testid=idp-login`)
   - Desktop `/callback` (not `/m/callback`) exchanges code + verifier
   - Click **写入授权** (`data-testid=permission-grant`)
   - `/p3/audit` shows LOGIN + PERMISSION on trace `0199a1c0-0000-7000-8000-00000000c219`
   - Stub `verifyChain()` intact (CanonicalAudit v1 + SHA-256, same rules as `InMemoryHashChainedAuditPort`)

Default product `GET /api/v1/auth/authorize-url` still has no PKCE (M0-04 confidential smoke). Stub IdP ignores nothing on this leftover: S256 challenge/verifier must match.

## Linked traces

| Trace | Role |
|---|---|
| `0199a1c0-0000-7000-8000-00000000c214`–`c216` | #108 HTTP PKCE (unchanged) |
| `0199a1c0-0000-7000-8000-00000000c217`–`c218` | #120 HTTPS leftover (unchanged) |
| `0199a1c0-0000-7000-8000-00000000c219` | This desktop click walk (LOGIN + PERMISSION) |

## How to run

Baseline: Java 21, Spring Boot **4.1.1**, Node 22, system Chrome, no Docker.

```bash
./scripts/g4/run-pkce-browser-click.sh
```

Equivalents:

```bash
# Always-on Vue click (no Chrome, no JAR)
cd web && npm test -- src/views/login-pkce.browser.test.js

# Playwright walk (stub IdP + Vite preview + system Chrome)
cd scripts/g4 && npm install
cd web && npm install && npm run build
NEXUS_G4_WEB_PORT=4174 node scripts/g4/stub-pkce-idp.mjs &
NEXUS_API_ORIGIN=http://127.0.0.1:18089 npx vite preview --host 127.0.0.1 --port 4174
node scripts/g4/pkce-browser-click.mjs \
  --base http://127.0.0.1:4174 \
  --stub http://127.0.0.1:18089 \
  --json-out docs/g4-pkce-browser-click/walk.json
```

If Playwright or Chrome is missing, the walk writes `result=SKIP` and exits 0. HTTP PKCE on main stays proven.

## Browser steps

1. Open `/login`. Click **开始 PKCE 登录** (`data-testid=pkce-login`).
2. Stub IdP form: username `g4-pkce@example.com`, password `password`. Click **Sign In**.
3. Desktop `/callback` posts `{ code, redirect_uri, code_verifier }` with `X-Trace-Id`.
4. Land on `/p2/permissions/datascope`. Click **写入授权** (`data-testid=permission-grant`).
5. 审计哈希链 → trace `0199a1c0-0000-7000-8000-00000000c219` shows LOGIN + PERMISSION.

## Evidence

Recorded **pending-this-run** on this branch with `./scripts/g4/run-pkce-browser-click.sh`. Artifact: [walk.json](./g4-pkce-browser-click/walk.json).

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** |
| Spring Boot | **4.1.1** (product baseline; this leftover does not boot the JAR) |
| Node | **22.14.0** |
| Browser | system Chrome + `playwright-core` (no Docker, no bundled Chromium download) |
| Login | `data-testid=pkce-login` → stub IdP `data-testid=idp-login` |
| Trace | `0199a1c0-0000-7000-8000-00000000c219` |
| `AuditPort` | same event types + CanonicalAudit SHA-256; **same-TX rules untouched** |
| Docker | no |

| Suite | Result |
|---|---|
| Vitest `login-pkce.browser.test.js` | pending |
| Playwright walk | pending |
| `web npm test` | pending (existing H5 / G7 tests unchanged) |
| live Keycloak + product JAR click | **not claimed** — HTTP on main already recorded |

## Honest leftover G4 rows

This is **not** a product-wide G4 close and **not** a human laptop + live Keycloak acceptance.

| Still open | Why |
|---|---|
| Human laptop + live Keycloak | Operator must trust `.local/g4-https/server.crt` (or insecure-origin). This package is stub IdP + Playwright, same pattern as [g7-withdraw-browser.md](./g7-withdraw-browser.md). |
| SPA-generated verifier | BFF still mints `code_verifier` on `authorize-url?pkce=s256`. A production public-client page should generate it in the browser. |
| `GET /api/v1/auth/callback` | Still does not carry the verifier. Desktop leftover uses Vue `/callback` POST. |
| H5 `/m/*` PKCE | Orthogonal G15 leftover. This walk is desktop only. |
| Full G4 product close | 90-day hot purge, HTTP export wizard, switching P1 tenant audit-events off staging. |
| G17 / G6 | Not this package |
| G18 reopen | **No.** Stub IdP + fabricated admin MFA token is unchanged |

Same-TX `append` joining is unchanged. Default authorize URL still has no `code_challenge` (M0-04 confidential smoke).
