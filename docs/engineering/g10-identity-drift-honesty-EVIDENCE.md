# G10 leftover — identity drift-fix apply / dry-run honesty evidence

Recorded on this branch after rebase onto `origin/main` `135ba83` (Merge #178 claim-list tip align; also #175 at `607394e`). **Not** a product G10 close. **Not** identity apply. **Not** a redo of #141 / #169. **Not** SAML / LDAP / SMS MFA. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md). Keep **both** leftover rows in [docs/README.md](../README.md): [g10-identity-drift-dry-run.md](../g10-identity-drift-dry-run.md) (#141) and [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Keycloak | not listening on `127.0.0.1:8080` | OIDC discovery not reachable |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g10-identity-drift-honesty.sh
./scripts/g10-identity-drift-honesty.sh --verify-report <out>/report.env
./scripts/g10-identity-drift-honesty.sh --verify-report \
  scripts/g10-identity-drift-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-identity-drift-honesty.sh --verify-report \
  scripts/g10-identity-drift-honesty/fixtures/fake-live-keycloak-pass.env
./scripts/g10-identity-drift-honesty.sh --verify-report \
  scripts/g10-identity-drift-honesty/fixtures/fake-live-apply-pass.env
./scripts/g10-identity-drift-honesty.sh --verify-report \
  scripts/g10-identity-drift-honesty/fixtures/fake-g10-close.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g10-identity-drift-honesty/`. A sanitized copy is [`g10-identity-drift-honesty/agent-vm.env`](./g10-identity-drift-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake apply PASS rejected / fake `g10_close=CLOSED` rejected | `2026-09-22T19:54:16Z` |
| Dry-run default no write | **true** | leftover refuses `NEXUS_G10_APPLY=1` |
| Live Keycloak 8080 dry-run | **SKIP** (expected) | nothing on `:8080`; OIDC discovery not reachable |
| live_dry_run_observed | **false** | honesty gate requires `false` on SKIP |
| Live apply | **SKIP** | product apply not redone |
| #141 / #169 redo | **false** | sibling leftovers |
| SAML / LDAP / SMS MFA | **false** | honesty gate |
| Product G10 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live Keycloak 26.5.2 `:8080` detect + proposed-fix dry-run on a host that actually has that process
- Product identity apply (`POST {"apply":true}`)
- Product G10 close
- SAML / LDAP / AD / SMS MFA / Keycloak Organizations
- File metadata ↔ object store (#169 leftover stays sibling)
