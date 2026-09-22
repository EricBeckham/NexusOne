# G1 leftover — FieldPolicy mask / second-auth honesty beyond #162

S0+S1 remainder after the #162 leftover ([g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md)). Rebased onto `origin/main` `5ff0472` (after #179 G10 identity drift-fix leftover honesty / #181 G15 native-bridge leftover honesty / #177 G4 SPA PKCE leftover honesty / #180 SecretPort getenv snapshot honesty / #171 G5 browser failed-publish leftover). **G18 stays closed.** This leftover does **not** redo #115 visibility AST, #130 list/detail opt-in, #134 export hidden drop, #142 single-column `IN`, #146 BoundSql COUNT, #162 leftoverMaskFields / projector harness, or #168 SELF∨DEPT OR. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** invent a second tenant context. It does **not** claim a product G1 close. `g1_close=NOT_CLAIMED`.

Keep **all** sibling leftover rows in [docs/README.md](./README.md): [g1-datascope-or-in-ast.md](./g1-datascope-or-in-ast.md) (#142/#168) and [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md) (#162). Also keep newly landed sibling leftovers [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), and [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171). This file is the PASS/SKIP honesty leftover beside them.

#162 already proved mask + 二次授权 stay on `FieldPolicyProjector` (`leftoverMaskFields`). Compiled Query AST still selects raw `"phone"`. Granted IsolatedExport still writes **raw** cells. The leftover here is the **honesty gate** for live walks: product-JAR list/detail mask, IsolatedExport applying masks, and a live second-auth grant are **PASS** only when a listener is present **and** the walk was observed. Missing `NEXUS_BASE_URL` / issued context is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #162 leftoverMaskFields | Already on `main`. Mask / 二次授权 stay beside Query AST. `leftover_beside_ast=PASS`. Do not redo. |
| IsolatedExport mask | #162 proved granted export writes raw cells. `isolated_export_applies_mask=false`. Claiming `true` is rejected. |
| Live JAR list/detail mask | `live_api_mask=SKIP` unless healthy JAR **and** issued context **and** `NEXUS_G1_LIVE_API_MASK_OBSERVED=true`. |
| Live IsolatedExport mask | `live_export_mask=SKIP` unless JAR present **and** a mask-applying export walk was observed. |
| Live second-auth grant | `live_second_auth=SKIP` unless JAR present **and** a grant walk was observed. |
| UI / H5 / report UI | `ui_h5_report=SKIP`. Not walked. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g1_close=CLOSED`, rejects `mask_on_ast=true`, and requires both leftover rows kept. |

No Docker. No product-JAR start. `#162` script stays the sibling reproduction command.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G1 close | Explicitly `g1_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Mask / second-auth on Query AST | Still false. Fake `mask_on_ast=true` reports fail. |
| IsolatedExport applying masks | #162 leftover: granted export still writes raw cells. |
| #115 / #130 / #134 / #142 / #146 / #162 / #168 | Sibling leftovers on `main`. `redone_*=false`. |
| Live product-JAR FieldPolicy walk | Honest SKIP on this agent VM. |
| UI / H5 / report UI | Not re-walked. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

```bash
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

Always-green Java (does **not** rerun #162 `G1FieldPolicyMaskSecondAuthHonestyTest`):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=FieldPolicyMaskLeftoverHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #162 leftover stays a sibling:

```bash
./scripts/g1-fieldpolicy-mask-second-auth-honesty.sh
```

Do **not** treat that script as this leftover. Java 21 / Boot **4.1.1**. No Docker. Live product-JAR is **not** required.

See [engineering/g1-fieldpolicy-mask-second-auth-honesty-EVIDENCE.md](./engineering/g1-fieldpolicy-mask-second-auth-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g1-fieldpolicy-mask-second-auth-honesty/`.

## Honest leftovers (not claimed)

- Encoding mask / 二次授权 onto Query AST nodes
- IsolatedExport applying `FieldPolicyProjector` masks
- Live product-JAR list/detail FieldPolicy mask / live second-auth grant
- UI / H5 / report UI FieldPolicy walk
- Product-wide G1 close
- Redo of #115 / #130 / #134 / #142 / #146 / #162 / #168

Remainder honesty beyond this leftover (and #162 / #215) is [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md). Keep this leftover row.
