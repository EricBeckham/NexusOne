# G4 leftover — human notebook + live Keycloak CA / SPA verifier — evidence

Recorded on this branch after rebase onto `origin/main` (`e0a409a`, includes #141 G10 identity drift-fix dry-run, #144 G2 tenant_only/catalog, #142 G1 DataScope IN). **Not** a product G4 close. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. #108 / #120 / #125 PKCE leftovers were not redone.

See [docs/g4-pkce-spa-verifier.md](../g4-pkce-spa-verifier.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04 | product baseline |
| Maven | 3.8.7 (or agent PATH) | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Node | 22.x | SPA mint harness (Web Crypto) |
| Keycloak | **26.5.2** native (not started here) | live PASS only if OIDC discovery answers |
| Runtime | local `mvn test` + collect script | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g4-pkce-spa-verifier.sh
./scripts/g4-pkce-spa-verifier.sh --verify-report <out>/report.env
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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g4-pkce-spa-verifier/`. A sanitized copy is [`g4-pkce-spa-verifier/agent-vm.env`](./g4-pkce-spa-verifier/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| SPA S256 mint / RFC 7636 appendix B | pending record | same helper as H5 `createS256` |
| Client-held authorize-url (no BFF verifier) | pending record | `AuthPkceApiTest` + `SessionSpaHeldPkceLoginAuditTest` |
| Live Keycloak SPA-held token exchange | **SKIP** (expected on this VM unless discovery is up) | this leftover does not start `kc.sh` |
| Loopback CA present | inventory | `#120` `.local/g4-https/server.crt` |
| Product G4 / G18 / AuditPort | **NOT_CLAIMED** / closed_unchanged / untouched | honesty gate |

## Honest leftovers

- Live Keycloak Authorization Code + PKCE with an SPA-held verifier on a host that actually has native Keycloak 26.5.2 running
- Human laptop click that trusts the loopback CA (notebook only)
- HTTP export wizard / P1 staging audit-events / product G4 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
