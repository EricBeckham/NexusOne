# G4 leftover — live Keycloak CA / SPA PKCE honesty beyond #147

S0+S1 remainder after the #147 evidence path ([g4-pkce-spa-verifier.md](./g4-pkce-spa-verifier.md)). Rebased onto `origin/main` `607394e` (after #175 G7 withdraw-browser honesty / #176 claim-list tip align). **G18 stays closed.** This leftover does **not** redo #108 HTTP-driver PKCE, #120 HTTPS loopback, #125 desktop stub click, #147 SPA verifier leftover, #152 export wizard, or #166 complete-deny. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** start `kc.sh`. It does **not** claim product G4 close. `g4_close=NOT_CLAIMED`.

Keep **both** leftover rows in [docs/README.md](./README.md): `g4-pkce-spa-verifier` (#147) and this file.

#147 already mints SPA S256, echoes a client-held `code_challenge`, and SKIPs live Keycloak when OIDC discovery is down. The leftover here is the **honesty gate** beyond that: live Keycloak CA / SPA-held token exchange is **PASS** only when discovery answers **and** the walk was actually observed. `#147` harness discovery is **not** a live token exchange. A CA file on disk is **not** a trusted laptop click. Missing Keycloak is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #147 SPA mint | RFC 7636 appendix B + `Pkce.clientHeld` (no BFF verifier) stay always-green. Not rewritten. |
| Live Keycloak SPA PKCE | `live_keycloak_pkce=PASS` only with OIDC discovery **and** `token_exchange_observed=true`. Discovery-only is SKIP. |
| Live CA trust | `live_ca_trust=PASS` only with `#120` CA present **and** HTTPS discovery **and** `ca_trust_observed=true`. |
| Human laptop click | `human_laptop_click=PASS` only when a laptop CA click was recorded. Notebook-only stays SKIP. |
| Honesty gate | `--verify-report` rejects fake live PASS, fake CA PASS, `g4_close=CLOSED`, and #108/#120/#125/#147/#152/#166 redo flags. |
| Leftover rows | `docs/README.md` keeps both #147 and this honesty leftover. |

This leftover does **not** start `kc.sh` (#108 / #120 own start). Default product `GET /api/v1/auth/authorize-url` still has no PKCE.

## How to run

```bash
./scripts/g4-pkce-spa-verifier-honesty.sh
./scripts/g4-pkce-spa-verifier-honesty.sh --verify-report \
  scripts/g4-pkce-spa-verifier-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-pkce-spa-verifier-honesty.sh --verify-report \
  scripts/g4-pkce-spa-verifier-honesty/fixtures/fake-live-pass.env
./scripts/g4-pkce-spa-verifier-honesty.sh --verify-report \
  scripts/g4-pkce-spa-verifier-honesty/fixtures/fake-ca-pass.env
./scripts/g4-pkce-spa-verifier-honesty.sh --verify-report \
  scripts/g4-pkce-spa-verifier-honesty/fixtures/fake-g4-close.env
```

Always-green Java (does not redo #108 / #120 / #125 / #147 suites):

```bash
mvn -pl nexus-p2-iam -am test \
  -Dtest=G4PkceSpaVerifierHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #147 leftover stays the reproduction command for the original evidence path:

```bash
./scripts/g4-pkce-spa-verifier.sh
```

See [engineering/g4-pkce-spa-verifier-honesty-EVIDENCE.md](./engineering/g4-pkce-spa-verifier-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g4-pkce-spa-verifier-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G4 close | Explicitly `g4_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| #147 SPA verifier leftover | Sibling leftover on `main`. `redo_147=false`. |
| Live Keycloak token exchange on this VM | Discovery unreachable / walk not observed. Honest SKIP. |
| Trusted laptop CA click | Notebook is documented on #147. Not recorded here. |
| #108 / #120 / #125 / #152 / #166 | Sibling leftovers. Not rewritten. |
| Starting Keycloak | #108 / #120 own `kc.sh`. This leftover probes and SKIPs. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## Honest leftovers (not claimed)

- Live Keycloak Authorization Code + PKCE with an SPA-held verifier on a host that actually has native Keycloak 26.5.2 running
- Human laptop click that trusts the loopback CA
- HTTP export wizard / P1 staging `audit-events` / product G4 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
