# G1 leftover — BoundSql / COUNT honesty beyond #146

S0+S1 remainder after the #146 leftover ([g1-queryast-boundsql-guard.md](./g1-queryast-boundsql-guard.md)). Rebased onto `origin/main` `eb8a151` (after #193 Linux/ARM Profile leftover honesty; includes #190 P1 staging audit-events leftover honesty / #194 claim-list tip-sync / #186 RecoveryPort dry-run leftover honesty / #185 live Redis/MinIO isolation leftover honesty / #187 G17 SoD leftover honesty / #183 G4 90-day hot-purge leftover honesty / #184 FieldPolicy mask leftover honesty). **G18 stays closed.** This leftover does **not** redo #142 single-column `IN`, #146 leftover BoundSql / COUNT product guard, #162 leftoverMaskFields / projector harness, #168 SELF∨DEPT OR, or #184 FieldPolicy leftover honesty. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** invent a second tenant context. It does **not** claim a product G1 close. `g1_close=NOT_CLAIMED`.

Keep **all** sibling leftover rows in [docs/README.md](./README.md): [g1-queryast-boundsql-guard.md](./g1-queryast-boundsql-guard.md) (#146), [g1-datascope-or-in-ast.md](./g1-datascope-or-in-ast.md) (#142/#168), [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md) (#162), and [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184). Also keep newly landed sibling leftovers [linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md) (#183), [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), and [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171), plus the #194 claim-list tip-sync pointers (`4c26d76` / `#185` / `#186`). This file is the PASS/SKIP honesty leftover beside them.

#146 already forced leftover-bound SQL and `COUNT(*)` wraps through `QueryAstScopedQuery.executed` / `countExecuted` onto `kernel.jdbc()` / `GuardedJdbc`. Raw unquoted `tenant_id` BoundSql is rejected. `COUNT(*)` is still not a Query AST node. The leftover here is the **honesty gate**: in-process leftover BoundSql / COUNT stay **PASS**. Live product-JAR `GuardedLedgerStore` list / count is **PASS** only when a listener is present **and** both walks were observed. Missing `NEXUS_BASE_URL` / issued context is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| Leftover BoundSql | Compile through `QueryAstScopedQuery` (quoted `"tenant_id"` + leftover `AND (…)`). `leftover_boundsql_guard=PASS`. Do not redo #146. |
| Leftover COUNT | Wrap compiled SELECT via `QueryAstScopedQuery.countExecuted`. `leftover_count_guard=PASS`. `count_is_ast_node=false`. |
| Raw BoundSql | Unquoted `tenant_id` / missing compiler tenant is rejected. `raw_boundsql_rejected=true`. |
| Live GuardedLedgerStore | `live_product_walk=SKIP` unless healthy JAR **and** issued context **and** observed list + COUNT walks. |
| UI / H5 / report UI | `ui_h5_report=SKIP`. Not walked. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g1_close=CLOSED`, rejects unguarded BoundSql, and requires sibling leftover rows kept. |

No Docker. No product-JAR start. `#146` tests stay the sibling reproduction.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G1 close | Explicitly `g1_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| `COUNT(*)` as a Query AST node | Still a wrap. Fake `count_is_ast_node=true` reports fail. |
| #142 / #146 / #162 / #168 | Sibling leftovers on `main`. `redone_*=false`. |
| #184 FieldPolicy leftover honesty | Sibling leftover. Keep that README row. |
| Live product-JAR BoundSql / COUNT walk | Honest SKIP on this agent VM. |
| UI / H5 / report UI | Not re-walked. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

```bash
./scripts/g1-queryast-boundsql-honesty.sh
./scripts/g1-queryast-boundsql-honesty.sh --verify-report <out>/report.env
./scripts/g1-queryast-boundsql-honesty.sh --verify-report \
  scripts/g1-queryast-boundsql-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-queryast-boundsql-honesty.sh --verify-report \
  scripts/g1-queryast-boundsql-honesty/fixtures/fake-live-product-pass.env
./scripts/g1-queryast-boundsql-honesty.sh --verify-report \
  scripts/g1-queryast-boundsql-honesty/fixtures/fake-g1-close.env
./scripts/g1-queryast-boundsql-honesty.sh --verify-report \
  scripts/g1-queryast-boundsql-honesty/fixtures/fake-raw-boundsql.env
```

Always-green Java (does **not** rewrite #146 `G1QueryAstBoundSqlGuardTest`):

```bash
mvn -pl nexus-permission-core -am test \
  -Dtest=G1QueryAstBoundSqlHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #146 leftover stays a sibling:

```bash
# do not run for this leftover
# mvn -pl nexus-permission-core,nexus-bootstrap -am test \
#   -Dtest=QueryAstScopedQueryTest,G1QueryAstBoundSqlGuardTest
```

Java 21 / Boot **4.1.1**. Isolation-kernel compile path is in-process. No Docker. Live product-JAR is **not** required.

See [engineering/g1-queryast-boundsql-honesty-EVIDENCE.md](./engineering/g1-queryast-boundsql-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g1-queryast-boundsql-honesty/`.

## Honest leftovers (not claimed)

- Live product-JAR `GuardedLedgerStore` leftover BoundSql list + COUNT walk
- Encoding `COUNT(*)` onto a Query AST node
- UI / H5 / report UI leftover BoundSql / COUNT walk
- Product-wide G1 close
- Redo of #142 / #146 / #162 / #168 / #184
