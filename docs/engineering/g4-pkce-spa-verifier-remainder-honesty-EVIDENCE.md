# G4 leftover — live Keycloak CA / SPA PKCE remainder honesty beyond #177/#147 — evidence

Recorded on this branch after rebase onto `origin/main` (`7db8335`, Merge #216 G17 SoD admin-force leftover honesty after #218 S0+S1 claim-list tip-sync / #220 G15 wx-dt/native-bridge remainder leftover honesty / #215 G1 BoundSql/COUNT/export leftover honesty / #210 Tongsuo/Nginx GMTLS handshake leftover honesty / #217 G5 PublishHook/DDL leftover honesty / #209 G9 Sentinel/Cluster remainder leftover honesty / #214 tip-sync / #188). **Not** a product G4 close. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. #108 / #120 / #125 / #147 / #152 / #166 / #177 leftovers were not redone.

See [docs/g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g4-pkce-spa-verifier.md](../g4-pkce-spa-verifier.md) (#147), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), this leftover, [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188). Never drop leftover rows when tip-syncing the claim list.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline; leftover does not boot the JAR |
| Maven | `$HOME/tools/apache-maven-3.8.7` when present | collect may `--skip-tests` |
| Spring Boot parent | **4.1.1** | product BOM |
| Keycloak | **26.5.2** native (not started here) | live PASS only if discovery answers **and** token exchange observed |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g4-pkce-spa-verifier-remainder-honesty.sh
./scripts/g4-pkce-spa-verifier-remainder-honesty.sh --verify-report <out>/report.env
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

## Results (agent VM)

Filled after collect on this VM at `2026-09-22T21:20:36Z` (rebase `7db8335`). Live output is gitignored under `.local/g4-pkce-spa-verifier-remainder-honesty/`. Sanitized copy: [`g4-pkce-spa-verifier-remainder-honesty/agent-vm.env`](./g4-pkce-spa-verifier-remainder-honesty/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake CA PASS rejected / fake `g4_close=CLOSED` rejected | after rebase onto `7db8335` |
| SPA mint / RFC 7636 / #177 harness | **PASS** (gate) | collect `--skip-tests` when `mvn` is not on PATH |
| leftover rows kept | **true** | ALL leftover rows stay, including #147/#177/#216/#220/#218/#215/#210/#217/#209 |
| Live Keycloak SPA-held token exchange | **SKIP** (expected) | discovery unreachable / walk not observed; #147/#177 collect is not a live exchange |
| Live CA trust | **SKIP** (expected) | CA file is not a trusted laptop walk |
| Human laptop click | **SKIP** (expected) | notebook only |
| #108/#120/#125/#147/#152/#166/#177 | not redone | `redo_*=false` |
| Product G4 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live Keycloak Authorization Code + PKCE with an SPA-held verifier on a host that actually has native Keycloak 26.5.2 running
- Human laptop click that trusts the loopback CA
- HTTP export wizard / P1 staging audit-events / product G4 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
