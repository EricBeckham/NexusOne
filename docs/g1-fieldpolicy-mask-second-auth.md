# G1 leftover: FieldPolicy mask / second-auth honesty (beside Query AST)

S0+S1 PostgreSQL slice. Not an M0 redo. G18 stays closed. `#115` encoded SELF/DEPT equality + FieldPolicy **visibility**. `#130` opted product list/detail into that encoder. `#134` drops hidden export columns. `#142` encoded single-column `IN`. `#146` guards leftover BoundSql / COUNT. This leftover **does not redo that work**.

It is an **honesty leftover**. Mask merge and 二次授权 still sit **beside** Query AST on `FieldPolicyProjector`. A passing harness means the leftover is still open. It does **not** claim a product-wide G1 close. It does **not** invent a second tenant context. It does **not** change `AuditPort.append` same-TX joining.

Orthogonal to `#153` claim-list tip align, `#159` Vue `todo-withdraw` HTTP reachability ([g7-withdraw-browser.md](./g7-withdraw-browser.md)), `#155` ops `pg_dump` honesty ([g2-ops-pg-dump.md](./g2-ops-pg-dump.md)), and `#143` SecretPort rotate-without-restart ([s0-secret-rotate-without-restart.md](./s0-secret-rotate-without-restart.md)). Keep those leftover rows in `docs/README.md`.

## What this leftover covers

| Surface | What is already encoded | Still beside AST |
|---|---|---|
| List / detail Query AST | SELF/DEPT + hidden drop (`#115` / `#130`) | `leftoverMaskFields` parks `Mask` + `secondAuthorization` |
| Compiled SQL | Quoted `"phone"` still in SELECT | No mask text / `mask()` / `***` in QueryCompiler SQL |
| API / UI row | Hidden dropped after fetch | `FieldPolicyProjector.project` applies FULL without second-auth, PARTIAL with grant |
| Export compile / write | Hidden drop (`#134`); second-auth without grant is `P1.M02.021` | Granted export still writes **raw** cells. Mask is not an IsolatedExport node |

`tenant_id` stays on `TenantInjector` / `AccessContextHolder`. Thin catalog: `FieldPolicyMaskHonesty` (`g1_close=NOT_CLAIMED`).

## What this does not claim

| Item | Status |
|---|---|
| Product-wide G1 | Explicitly `NOT_CLAIMED`. Honesty gate rejects `g1_close=CLOSED`. |
| Mask / second-auth on Query AST | Still false. Fake `mask_on_ast=true` reports fail. |
| `#115` / `#130` / `#134` / `#142` / `#146` | Sibling leftovers on `main`. Not rewritten. |
| Mixed-column OR (SELF∨DEPT) | Encoded on `QueryAst.disjunctions()` ([g1-datascope-or-in-ast.md](./g1-datascope-or-in-ast.md)). Unencodable leftovers stay `PermissionQueryBinder`. |
| `COUNT(*)` as a Query AST node | Still a wrap. |
| UI / H5 / report UI | Not re-walked. |
| `#159` G7 browser withdraw / `#155` ops `pg_dump` / `#143` secret rotate | Orthogonal leftover rows kept. |
| G18 reopen / `AuditPort` | Closed / untouched. |

## How to run

```bash
./scripts/g1-fieldpolicy-mask-second-auth-honesty.sh
./scripts/g1-fieldpolicy-mask-second-auth-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth/fixtures/honest-leftover.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-fieldpolicy-mask-second-auth-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth/fixtures/fake-g1-close.env fail
./scripts/g1-fieldpolicy-mask-second-auth-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth/fixtures/fake-mask-on-ast.env fail
```

Equivalent tests:

```bash
mvn -pl nexus-permission-core,nexus-bootstrap -am test \
  -Dtest=FieldPolicyMaskHonestyTest,G1FieldPolicyMaskSecondAuthHonestyTest,QueryAstPolicyCompilerTest,FieldPolicyProjectorTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Java 21 / Boot **4.1.1**. Isolation-kernel compile path is in-process. No Docker.

See [engineering/g1-fieldpolicy-mask-second-auth-EVIDENCE.md](./engineering/g1-fieldpolicy-mask-second-auth-EVIDENCE.md). Live JAR / IsolatedExport mask / 二次授权 PASS/SKIP honesty beyond this leftover is [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md). Keep both leftover rows (`g1-datascope-or-in-ast` and `g1-fieldpolicy-mask-second-auth`) in [README.md](./README.md).

## Honest leftovers (not claimed)

- Encoding mask / 二次授权 onto Query AST nodes
- IsolatedExport applying `FieldPolicyProjector` masks
- Unencodable DataScope leftovers / `COUNT(*)` as a Query AST node
- `COUNT(*)` as a Query AST node
- Product-wide G1 / UI / H5 / report UI close
