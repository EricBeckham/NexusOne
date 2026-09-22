# G1 leftover — FieldPolicy mask / second-auth honesty beyond #162

Recorded on this branch after rebase onto `origin/main` `5ff0472` (Merge #179 G10 identity drift-fix leftover honesty; includes #181 G15 native-bridge leftover honesty / #177 G4 SPA PKCE leftover honesty / #180 SecretPort getenv snapshot honesty / #171 G5 browser failed-publish leftover). **Not** a product G1 close. **Not** a redo of #115 / #130 / #134 / #142 / #146 / #162 / #168. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. Mask / 二次授权 stay on `FieldPolicyProjector`.

See [docs/g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md). Keep **all** sibling leftover rows in [docs/README.md](../README.md): [g1-datascope-or-in-ast.md](../g1-datascope-or-in-ast.md) (#142/#168) and [g1-fieldpolicy-mask-second-auth.md](../g1-fieldpolicy-mask-second-auth.md) (#162), plus [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177) and [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) | product baseline; leftover does not boot the JAR |
| Maven | `$HOME/tools/apache-maven-3.8.7` when present | collect may `--skip-tests` |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g1-fieldpolicy-mask-leftover-honesty.sh
./scripts/g1-fieldpolicy-mask-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g1-fieldpolicy-mask-leftover-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-fieldpolicy-mask-leftover-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-honesty/fixtures/fake-live-api-mask-pass.env
./scripts/g1-fieldpolicy-mask-leftover-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-honesty/fixtures/fake-g1-close.env
./scripts/g1-fieldpolicy-mask-leftover-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-honesty/fixtures/fake-mask-on-ast.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g1-fieldpolicy-mask-second-auth-honesty/`. A sanitized copy is [`g1-fieldpolicy-mask-second-auth-honesty/agent-vm.env`](./g1-fieldpolicy-mask-second-auth-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | pending | leftover beside AST + fake close / fake mask-on-AST / fake live PASS |
| leftover beside AST | pending PASS | #162 leftoverMaskFields still parks mask + second-auth |
| IsolatedExport applies mask | **false** | #162 leftover; granted export writes raw cells |
| Live JAR list/detail mask | pending SKIP | `NEXUS_BASE_URL` unset |
| Live IsolatedExport mask | pending SKIP | not observed |
| Live second-auth grant | pending SKIP | not observed |
| Both leftover rows | kept | `g1-datascope-or-in-ast` + `g1-fieldpolicy-mask-second-auth` |
| #115/#130/#134/#142/#146/#162/#168 redo | **false** | sibling leftovers |
| Product G1 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Encoding mask / 二次授权 onto Query AST
- IsolatedExport applying masks
- Live product-JAR list/detail FieldPolicy mask / live second-auth grant
- UI / H5 / report UI / product-wide G1 close
