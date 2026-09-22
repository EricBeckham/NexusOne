# G4 leftover — live Keycloak CA / SPA PKCE remainder honesty beyond #177/#147

S0+S1 remainder after the #177 live Keycloak CA / SPA PKCE honesty leftover ([g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md)) and the #147 evidence path ([g4-pkce-spa-verifier.md](./g4-pkce-spa-verifier.md)). Rebased onto `origin/main` `7db8335` (Merge #216 G17 SoD admin-force leftover honesty after #218 S0+S1 claim-list tip-sync / #220 G15 wx-dt/native-bridge remainder leftover honesty / #215 G1 BoundSql/COUNT/export leftover honesty / #210 Tongsuo/Nginx GMTLS handshake leftover honesty / #217 G5 PublishHook/DDL leftover honesty / #209 G9 Sentinel/Cluster remainder leftover honesty / #214 tip-sync / #188). **G18 stays closed.** This leftover does **not** redo #108 HTTP-driver PKCE. It does **not** redo #120 HTTPS loopback. It does **not** redo #125 desktop stub click. It does **not** redo #147 SPA verifier leftover. It does **not** redo #152 export wizard. It does **not** redo #166 complete-deny. It does **not** redo #177 honesty leftover. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** start `kc.sh`. It does **not** claim product G4 close. `g4_close=NOT_CLAIMED`. P0/M0 are **not** redone.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md): [g4-pkce-spa-verifier.md](./g4-pkce-spa-verifier.md) (#147), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), this file, [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md) (#183), [g4-export-wizard-deny-honesty.md](./g4-export-wizard-deny-honesty.md) (#203), [g4-p1-staging-audit-events-honesty.md](./g4-p1-staging-audit-events-honesty.md) (#190), [g9-sentinel-cluster-remainder-honesty.md](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md) (#148), [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) (#161), [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) (#174), [g9-redis-down-invalidate-remainder.md](./g9-redis-down-invalidate-remainder.md) (#197), [g3-outbox-consume-crash-honesty.md](./g3-outbox-consume-crash-honesty.md) (#195), [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md) (#196), [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md) (#217), [g10-leftover-honesty.md](./g10-leftover-honesty.md) (#202), [g2-h5-every-page-form-write-honesty.md](./g2-h5-every-page-form-write-honesty.md) (#204), [g2-ops-pg-dump-honesty.md](./g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g17-gm-tls-nginx-handshake-honesty.md](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g15-wx-dt-native-bridge-honesty.md](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](./s0-secret-rotate-without-restart-honesty.md) (#200), [s0s1-live-redis-minio-isolation-honesty.md](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md) (#186), [linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md) (#193), [g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md) (#192), and [g1-queryast-boundsql-honesty.md](./g1-queryast-boundsql-honesty.md) (#191), and [g1-queryast-export-honesty.md](./g1-queryast-export-honesty.md) (#215), and [g15-wx-dt-native-bridge-remainder.md](./g15-wx-dt-native-bridge-remainder.md) (#220), and [g17-separation-leftover-honesty.md](./g17-separation-leftover-honesty.md) (#216). Never drop leftover rows when tip-syncing the claim list.

#147 already mints SPA S256, echoes a client-held `code_challenge`, and SKIPs live Keycloak when OIDC discovery is down. #177 already gates live Keycloak CA / SPA-held token exchange as **PASS** only when discovery answers **and** the walk was observed. The leftover here is the **honesty gate** beyond both: #147 harness discovery and #177 honesty collect stay **PASS** and are **not** a live token exchange. Live Keycloak CA / SPA-held token exchange is **PASS** only when discovery answers **and** the walk was actually observed. A CA file on disk is **not** a trusted laptop click. Missing Keycloak is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #147 SPA mint | RFC 7636 appendix B + `Pkce.clientHeld` (no BFF verifier) stay always-green. Not rewritten. |
| #177 honesty harness | Honesty fixtures / leftover-row gate stay always-green. Not rewritten. `#177` collect is **not** a live token exchange. |
| Live Keycloak SPA PKCE | `live_keycloak_pkce=PASS` only with OIDC discovery **and** `token_exchange_observed=true`. Discovery-only / #177 collect is SKIP. |
| Live CA trust | `live_ca_trust=PASS` only with `#120` CA present **and** HTTPS discovery **and** `ca_trust_observed=true`. |
| Human laptop click | `human_laptop_click=PASS` only when a laptop CA click was recorded. Notebook-only stays SKIP. |
| Honesty gate | `--verify-report` rejects fake live PASS, fake CA PASS, `g4_close=CLOSED`, #108/#120/#125/#147/#152/#166/#177 redo flags, and dropped leftover rows. |
| Leftover rows | `docs/README.md` keeps **ALL** leftover rows including #147, #177, this remainder, #216, #220, #218, #215, #210, #217, and #209. |

This leftover does **not** start `kc.sh` (#108 / #120 own start). Default product `GET /api/v1/auth/authorize-url` still has no PKCE.

## How to run

```bash
./scripts/g4-pkce-spa-verifier-remainder-honesty.sh
./scripts/g4-pkce-spa-verifier-remainder-honesty.sh --verify-report \
  scripts/g4-pkce-spa-verifier-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-pkce-spa-verifier-remainder-honesty.sh --verify-report \
  scripts/g4-pkce-spa-verifier-remainder-honesty/fixtures/fake-live-pass.env
./scripts/g4-pkce-spa-verifier-remainder-honesty.sh --verify-report \
  scripts/g4-pkce-spa-verifier-remainder-honesty/fixtures/fake-ca-pass.env
./scripts/g4-pkce-spa-verifier-remainder-honesty.sh --verify-report \
  scripts/g4-pkce-spa-verifier-remainder-honesty/fixtures/fake-g4-close.env
```

Always-green Java (does **not** redo #108 / #120 / #125 / #147 / #177 suites):

```bash
mvn -pl nexus-p2-iam -am test \
  -Dtest=G4PkceSpaVerifierRemainderHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Sibling leftovers stay siblings:

```bash
./scripts/g4-pkce-spa-verifier.sh
./scripts/g4-pkce-spa-verifier-honesty.sh
```

Do **not** treat those scripts as this leftover. Java 21 / Boot **4.1.1**. No Docker. Live Keycloak is **not** required.

See [engineering/g4-pkce-spa-verifier-remainder-honesty-EVIDENCE.md](./engineering/g4-pkce-spa-verifier-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g4-pkce-spa-verifier-remainder-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G4 close | Explicitly `g4_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| #147 / #177 leftover | Sibling leftovers on `main`. `redo_147=false` `redo_177=false`. |
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
- Redo of #108 / #120 / #125 / #147 / #152 / #166 / #177
