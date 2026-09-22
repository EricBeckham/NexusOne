# G4 leftover — live Keycloak CA / SPA PKCE honesty beyond #147 — evidence

Recorded on this branch after rebase onto `origin/main` (`607394e`, Merge #175 G7 withdraw-browser honesty; also #176 at `4457ec2`). **Not** a product G4 close. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. #108 / #120 / #125 / #147 / #152 / #166 leftovers were not redone.

See [docs/g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md). Keep **both** leftover rows in [docs/README.md](../README.md): [g4-pkce-spa-verifier.md](../g4-pkce-spa-verifier.md) (#147) and this leftover.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Keycloak | **26.5.2** native (not started here) | live PASS only if discovery answers **and** token exchange observed |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g4-pkce-spa-verifier-honesty.sh
./scripts/g4-pkce-spa-verifier-honesty.sh --verify-report <out>/report.env
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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g4-pkce-spa-verifier-honesty/`. A sanitized copy is [`g4-pkce-spa-verifier-honesty/agent-vm.env`](./g4-pkce-spa-verifier-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | pending record | fake live PASS / fake CA PASS / fake `g4_close=CLOSED` rejected |
| SPA mint / RFC 7636 / client-held | **PASS** (gate) | same `Pkce` helper; no BFF verifier |
| leftover rows kept | **true** | both README leftover rows stay |
| Live Keycloak SPA-held token exchange | **SKIP** (expected) | discovery unreachable / walk not observed; #147 probe is not a live exchange |
| Live CA trust | **SKIP** (expected) | CA file is not a trusted laptop walk |
| Human laptop click | **SKIP** (expected) | notebook only |
| #108/#120/#125/#147/#152/#166 | not redone | `redo_*=false` |
| Product G4 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live Keycloak Authorization Code + PKCE with an SPA-held verifier on a host that actually has native Keycloak 26.5.2 running
- Human laptop click that trusts the loopback CA
- HTTP export wizard / P1 staging audit-events / product G4 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
