# G1 leftover — BoundSql / COUNT / export FieldPolicy drop honesty beyond #191/#146

S0+S1 remainder **after** the #191 BoundSql / COUNT leftover honesty ([g1-queryast-boundsql-honesty.md](./g1-queryast-boundsql-honesty.md)), the #146 leftover BoundSql / COUNT product guard ([g1-queryast-boundsql-guard.md](./g1-queryast-boundsql-guard.md)), and the #134 export FieldPolicy hidden-drop leftover ([g1-export-fieldpolicy-drop.md](./g1-export-fieldpolicy-drop.md)). Rebased onto `origin/main` `c1d3150` (Merge #210 G17 Tongsuo/Nginx GMTLS handshake leftover honesty after #217; also #217 at `9be1f68`). **G18 stays closed.** This leftover does **not** redo #134 export drop, #142 single-column `IN`, #146 leftover BoundSql / COUNT product guard, #162 leftoverMaskFields, #168 SELF∨DEPT OR, #184 FieldPolicy leftover honesty, or #191 BoundSql / COUNT leftover honesty product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** invent a second tenant context. It does **not** claim a product G1 close. `g1_close=NOT_CLAIMED`. No P0/M0 redo.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g1-queryast-boundsql-guard.md](./g1-queryast-boundsql-guard.md) (#146), [g1-queryast-boundsql-honesty.md](./g1-queryast-boundsql-honesty.md) (#191), [g1-export-fieldpolicy-drop.md](./g1-export-fieldpolicy-drop.md) (#134), [g1-datascope-or-in-ast.md](./g1-datascope-or-in-ast.md) (#142/#168), [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md) (#162), [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g2-h5-every-page-form-write-honesty.md](./g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](./g9-redis-down-invalidate-remainder.md) (#197), [g17-mfa-recovery-vue-otp-honesty.md](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-ops-pg-dump-honesty.md](./g2-ops-pg-dump-honesty.md) (#188), [g9-sentinel-cluster-remainder-honesty.md](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md) (#217), [g17-gm-tls-nginx-handshake-honesty.md](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) (#174), [g2-h5-every-page.md](./g2-h5-every-page.md) (#163), [g2-form-write-honesty.md](./g2-form-write-honesty.md) (#173), [g4-export-wizard-deny-honesty.md](./g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](./g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](./g3-outbox-consume-crash-honesty.md) (#195), [g15-wx-dt-native-bridge-honesty.md](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md) (#183), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) (#175), [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md) (#174), [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) (#169), and [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179). Keep this file.

#191 already locked leftover BoundSql / COUNT as **PASS** on `QueryAstScopedQuery` → `GuardedJdbc`, and live product-JAR `GuardedLedgerStore` as honest **SKIP** when `NEXUS_BASE_URL` / issued context is missing. #146 already forced leftover-bound SQL and `COUNT(*)` wraps through `executed` / `countExecuted`. #134 already drops hidden export columns instead of only rejecting them; mask / 二次授权 stay on `FieldPolicyProjector`. The leftover here is the **honesty gate beyond those three**: those cells stay honest; live BoundSql / COUNT and live IsolatedExport hidden-drop are **PASS** only when a listener is present **and** that walk was observed. Missing live JAR is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #146 / #191 BoundSql / COUNT | Committed leftover BoundSql / COUNT stay on `QueryAstScopedQuery` → `GuardedJdbc`. `leftover_boundsql_guard=PASS`. `leftover_count_guard=PASS`. `raw_boundsql_rejected=true`. `count_is_ast_node=false`. Do not redo. |
| #134 export FieldPolicy drop | Committed export compile / `writeProjected` drop hidden columns. `leftover_export_hidden_drop=PASS`. Second-auth without grant still rejects. IsolatedExport still does **not** apply masks. Do not redo. |
| Live BoundSql / COUNT | `live_boundsql_count=PASS` only with healthy JAR **and** issued context **and** observed list + COUNT walks. Otherwise honest **SKIP**. |
| Live export FieldPolicy drop | `live_export_fieldpolicy_drop=PASS` only with healthy JAR **and** an observed IsolatedExport hidden-drop walk. Otherwise honest **SKIP**. |
| UI / H5 / report UI | `ui_h5_report=SKIP`. Not walked. |
| Sibling leftover rows | `docs/README.md` keeps #134 / #146 / #191 and this leftover, plus #204 `g2-h5-every-page-form-write-honesty`, #197 `g9-redis-down-invalidate-remainder`, #205 `g17-mfa-recovery-vue-otp-honesty`, #188 `g2-ops-pg-dump-honesty`, #209 `g9-sentinel-cluster-remainder-honesty`, #217 `g5-publishhook-ddl-honesty`, #210 `g17-gm-tls-nginx-handshake-honesty`, #211/#214 tip-sync, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g1_close=CLOSED`, rejects unguarded raw BoundSql, rejects `count_is_ast_node=true`, and rejects #134 / #146 / #191 redo flags. |

No Docker. No product-JAR start. `#191` / `#146` / `#134` stay the sibling reproduction commands.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G1 close | Explicitly `g1_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| `COUNT(*)` as a Query AST node | Still a wrap. Fake `count_is_ast_node=true` reports fail. |
| IsolatedExport applying masks | Still `false`. Mask / 二次授权 stay on `FieldPolicyProjector`. |
| #134 / #146 / #191 redo | Sibling leftovers on `main`. `redone_*=false`. |
| #142 / #162 / #168 / #184 | Sibling leftovers. Not rewritten. |
| Live product-JAR BoundSql / COUNT / export drop | Honest SKIP on this agent VM. |
| UI / H5 / report UI | Not re-walked. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

```bash
./scripts/g1-queryast-export-honesty.sh
./scripts/g1-queryast-export-honesty.sh --verify-report <out>/report.env
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/fake-live-boundsql-pass.env
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/fake-live-export-drop-pass.env
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/fake-g1-close.env
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/fake-raw-boundsql.env
```

#134 / #146 / #191 stay the sibling reproduction commands. This leftover does **not** invoke them:

```bash
# do not run for this leftover
# ./scripts/g1-queryast-boundsql-honesty.sh
# mvn -pl nexus-permission-core,nexus-bootstrap -am test \
#   -Dtest=QueryAstScopedQueryTest,G1QueryAstBoundSqlGuardTest,G1ExportFieldPolicyDropTest
```

Java 21 / Boot **4.1.1**. Isolation-kernel compile path is in-process. No Docker. Live product-JAR is **not** required.

See [engineering/g1-queryast-export-honesty-EVIDENCE.md](./engineering/g1-queryast-export-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g1-queryast-export-honesty/`.

## Honest leftovers (not claimed)

- Live product-JAR `GuardedLedgerStore` leftover BoundSql list + COUNT walk
- Live IsolatedExport hidden-drop walk on a host that actually has that JAR
- Encoding `COUNT(*)` onto a Query AST node
- IsolatedExport applying `FieldPolicyProjector` masks
- UI / H5 / report UI leftover BoundSql / COUNT / export FieldPolicy walk
- Product-wide G1 close
- Redo of #134 / #146 / #191 / #142 / #162 / #168 / #184

FieldPolicy mask / second-auth remainder honesty beyond #184/#162/#215 is [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md). Keep this leftover row.
