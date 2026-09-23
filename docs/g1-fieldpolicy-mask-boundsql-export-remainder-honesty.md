# G1 leftover — FieldPolicy mask / BoundSql export remainder honesty beyond #238/#215/#184

S0+S1 remainder **after** the #238 FieldPolicy mask / second-auth leftover honesty remainder ([g1-fieldpolicy-mask-second-auth-remainder-honesty.md](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md)), the #215 BoundSql / COUNT / export FieldPolicy drop leftover honesty ([g1-queryast-export-honesty.md](./g1-queryast-export-honesty.md)), and the #184 FieldPolicy mask / second-auth leftover honesty ([g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md)). Rebased onto `origin/main` `6c224e1` (Merge #260 G5 leftover honesty after #256 G7 query/withdraw leftover honesty / #265 G4 leftover honesty / #263 Linux/ARM leftover remainder honesty / #262 G10 file/object leftover honesty / #259 G9 leftover honesty). **G18 stays closed.** This leftover does **not** redo #115 visibility AST, #130 list/detail opt-in, #134 export hidden drop, #142 single-column `IN`, #146 leftover BoundSql / COUNT, #162 leftoverMaskFields / projector harness, #168 SELF∨DEPT OR, #184 FieldPolicy leftover honesty product code, #191 BoundSql / COUNT leftover honesty, #215 BoundSql / COUNT / export leftover honesty product code, or #238 FieldPolicy remainder leftover honesty product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** invent a second tenant context. It does **not** redo P0/M0. It does **not** claim a product G1 close. `g1_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md) (#162), [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g1-queryast-export-honesty.md](./g1-queryast-export-honesty.md) (#215), [g1-queryast-boundsql-guard.md](./g1-queryast-boundsql-guard.md) (#146), [g1-queryast-boundsql-honesty.md](./g1-queryast-boundsql-honesty.md) (#191), [g1-export-fieldpolicy-drop.md](./g1-export-fieldpolicy-drop.md) (#134), [g1-datascope-or-in-ast.md](./g1-datascope-or-in-ast.md) (#142/#168), [g5-publishhook-ddl-leftover-honesty.md](./g5-publishhook-ddl-leftover-honesty.md) (#260), [g7-query-withdraw-leftover-honesty.md](./g7-query-withdraw-leftover-honesty.md) (#256), [g4-hot-purge-staging-pkce-leftover-honesty.md](./g4-hot-purge-staging-pkce-leftover-honesty.md) (#265), [linux-arm-profile-leftover-remainder-honesty.md](./linux-arm-profile-leftover-remainder-honesty.md) (#263), [g10-file-object-reconcile-leftover-honesty.md](./g10-file-object-reconcile-leftover-honesty.md) (#262), [g9-leftover-honesty-remainder.md](./g9-leftover-honesty-remainder.md) (#259), [g17-separation-leftover-honesty-remainder.md](./g17-separation-leftover-honesty-remainder.md) (#249), [g15-h5-pkce-miniprogram-remainder-honesty.md](./g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty.md](./g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty.md](./linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](./g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty.md](./g10-file-object-reconcile-remainder-honesty.md) (#243), [g10-leftover-honesty-remainder.md](./g10-leftover-honesty-remainder.md) (#228), [g10-leftover-honesty.md](./g10-leftover-honesty.md) (#202), [g2-h5-every-page-form-write-remainder-honesty.md](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty.md](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g7-query-expense-travel-honesty-remainder.md](./g7-query-expense-travel-honesty-remainder.md) (#239), [g17-mfa-recovery-vue-otp-remainder-honesty.md](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g5-publishhook-ddl-remainder-honesty.md](./g5-publishhook-ddl-remainder-honesty.md) (#235), [s0-secret-rotate-getenv-leftover-honesty.md](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g4-hot-purge-staging-leftover-honesty.md](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g7-withdraw-browser-evidence-honesty.md](./g7-withdraw-browser-evidence-honesty.md) (#212), [g9-live-multi-jvm-replica-remainder-honesty.md](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty.md](./g3-outbox-consume-idempotent-honesty.md) (#226), [g2-report-physical-dump-honesty.md](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](./g15-wx-dt-native-bridge-remainder.md) (#220), [g17-gm-tls-nginx-handshake-honesty.md](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](./g2-ops-pg-dump-honesty.md) (#188), and every leftover-style sibling already on `origin/main`. Keep this file.

#162 already proved mask + 二次授权 stay on `FieldPolicyProjector` (`leftoverMaskFields`). Compiled Query AST still selects raw `"phone"`. Granted IsolatedExport still writes **raw** cells. #184 already is the leftover honesty gate beyond that. #215 already locked leftover BoundSql / COUNT / export hidden-drop as **PASS** and live product-JAR walks as honest **SKIP**. #238 already is the remainder honesty gate beyond #184/#162/#215. That #238/#215/#184 harness PASS is **not** a live product-JAR FieldPolicy walk, **not** a live IsolatedExport mask walk, **not** a live BoundSql / COUNT / export-drop walk, and **not** a product G1 close. The leftover here is the **honesty gate beyond those three**: those cells stay honest; parent leftover honesty fixtures stay **PASS** and are explicitly **not** a live walk (`harness_238_is_not_live_jar=true`, `harness_238_is_not_live_export_mask=true`, `harness_238_is_not_product_g1=true`, `harness_215_is_not_live_jar=true`). Live product-JAR list/detail mask, IsolatedExport applying masks, live second-auth grant, and live BoundSql / COUNT / export-drop are **PASS** only when a listener is present **and** the walk was observed. Missing `NEXUS_BASE_URL` / issued context / IsolatedExport mask walk is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #162 leftoverMaskFields | Already on `main`. Mask / 二次授权 stay beside Query AST. `leftover_162_cells_honest=PASS`. `leftover_beside_ast=PASS`. Do not redo. |
| #184 honesty cells | Committed leftover beside AST PASS; `mask_on_ast=false`; IsolatedExport still writes raw cells; live JAR / IsolatedExport mask / second-auth SKIP. `leftover_184_cells_honest=PASS`. `leftover_184_honesty_gate=PASS`. Do not redo. |
| #215 BoundSql / export | Committed leftover BoundSql / COUNT / export hidden-drop stay honest. IsolatedExport still does **not** apply masks. `leftover_215_cells_honest=PASS`. `leftover_215_honesty_gate=PASS`. `#215` harness is **not** a live JAR walk. Do not redo. |
| #238 remainder cells | Committed leftover #184/#215 cells PASS; parent leftover honesty fixtures PASS; committed live JAR / IsolatedExport mask / second-auth SKIP; `g1_close=NOT_CLAIMED`. `leftover_238_cells_honest=PASS`. `leftover_238_honesty_gate=PASS`. Do not redo. |
| #238 harness vs live JAR | Always-green `leftover_238_honesty_gate=PASS` **and** `harness_238_is_not_live_jar=true`. `#238` harness is **not** a live product-JAR FieldPolicy walk. |
| #238 harness vs live IsolatedExport mask | `harness_238_is_not_live_export_mask=true`. `#238` harness is **not** a live IsolatedExport mask walk. |
| #238 harness vs product G1 | `harness_238_is_not_product_g1=true`. `#238` PASS is **not** product G1 close. |
| IsolatedExport mask | #162/#184 proved granted export writes raw cells. `isolated_export_applies_mask=false`. Claiming `true` is rejected. |
| BoundSql / COUNT / export drop | Committed leftover BoundSql / COUNT stay on `QueryAstScopedQuery`. Export hidden drop stays on IsolatedExport. `leftover_boundsql_guard=PASS`. `leftover_count_guard=PASS`. `leftover_export_hidden_drop=PASS`. `count_is_ast_node=false`. |
| Live JAR list/detail mask | `live_api_mask=SKIP` unless healthy JAR **and** issued context **and** `live_api_mask_observed=true`. |
| Live IsolatedExport mask | `live_export_mask=SKIP` unless JAR present **and** a mask-applying export walk was observed. |
| Live second-auth grant | `live_second_auth=SKIP` unless JAR present **and** a grant walk was observed. |
| Live BoundSql / COUNT | `live_boundsql_count=SKIP` unless JAR present **and** list + COUNT walks were observed. |
| Live export FieldPolicy drop | `live_export_fieldpolicy_drop=SKIP` unless JAR present **and** an IsolatedExport hidden-drop walk was observed. |
| UI / H5 / report UI | `ui_h5_report=SKIP`. Not walked. |
| Sibling leftover rows | `docs/README.md` keeps #162 / #184 / #215 / #238 and this leftover, plus #260 `g5-publishhook-ddl-leftover-honesty`, #256 `g7-query-withdraw-leftover-honesty`, #265 `g4-hot-purge-staging-pkce-leftover-honesty`, #263 `linux-arm-profile-leftover-remainder-honesty`, #262 `g10-file-object-reconcile-leftover-honesty`, #259 `g9-leftover-honesty-remainder`, #249 `g17-separation-leftover-honesty-remainder`, #240 `g15-h5-pkce-miniprogram-remainder-honesty`, #254 `g2-h5-every-page-form-write-report-remainder-honesty`, #236 `linux-arm-profile-remainder-honesty`, #251 `g4-hot-purge-staging-pkce-remainder-honesty`, #243 `g10-file-object-reconcile-remainder-honesty`, #228 `g10-leftover-honesty-remainder`, #232 `g2-h5-every-page-form-write-remainder-honesty`, #237 `g9-redis-down-invalidate-remainder-honesty`, #239 `g7-query-expense-travel-honesty-remainder`, #233 `g17-mfa-recovery-vue-otp-remainder-honesty`, #235 `g5-publishhook-ddl-remainder-honesty`, #241 `s0-secret-rotate-getenv-leftover-honesty`, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects treating #238 harness as a live JAR / IsolatedExport mask walk, rejects `g1_close=CLOSED`, rejects `mask_on_ast=true`, rejects IsolatedExport-applies-mask, rejects `count_is_ast_node=true`, rejects #184/#215/#238 redo flags, and requires leftover rows kept. |

No Docker. No product-JAR start. `#162` / `#184` / `#215` / `#238` stay the sibling reproduction commands. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G1 close | Explicitly `g1_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. `#238` harness PASS is not product G1 (`harness_238_is_not_product_g1=true`). |
| #238 harness as live JAR | `harness_238_is_not_live_jar=true`. Remainder honesty gate is not a live walk. |
| #238 harness as live IsolatedExport mask | `harness_238_is_not_live_export_mask=true`. |
| Mask / second-auth on Query AST | Still false. Fake `mask_on_ast=true` reports fail. |
| IsolatedExport applying masks | #162 leftover: granted export still writes raw cells. |
| `COUNT(*)` as a Query AST node | Still a wrap. Fake `count_is_ast_node=true` reports fail. |
| #115 / #130 / #134 / #142 / #146 / #162 / #168 / #184 / #215 / #238 | Sibling leftovers on `main`. `redone_184=false`. `redone_215=false`. `redone_238=false`. |
| Live product-JAR FieldPolicy / BoundSql / export walk | Honest SKIP on this agent VM. |
| UI / H5 / report UI | Not re-walked. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product G15 leftover (#240/#220/#198) / product G2 leftover (#254/#232/#221/#204) / product Linux/ARM leftover (#263/#236/#193/#157) / product G4 leftover (#265/#251/#230/#222) / product G7 leftover (#256/#239/#212) / product G5 leftover (#260/#235/#217) / product G10 leftover (#262/#243) / product G9 leftover (#259/#237) / product G7 leftover (#239/#212) / product G17 leftover (#249/#216/#233) / product G5 leftover (#235) / product S0 leftover (#241) | Sibling leftovers. Not rewritten. Not claimed. |

## How to run

```bash
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-api-mask-pass.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-export-mask-pass.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-second-auth-pass.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-boundsql-pass.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-g1-close.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-redone.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-harness-as-live.env fail
```

#184 / #215 / #238 stay the sibling reproduction commands. This leftover does **not** invoke them:

```bash
# do not run for this leftover
# ./scripts/g1-fieldpolicy-mask-second-auth-honesty.sh
# ./scripts/g1-queryast-export-honesty.sh
# ./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh
```

Parent `--verify-report` on already-committed #238 / #215 honesty fixtures is harness evidence.

Java 21 / Boot **4.1.1**. Isolation-kernel compile path is in-process. No Docker. Live product-JAR / IsolatedExport mask / BoundSql walk are **not** required. `#238` harness PASS is still not that walk (`harness_238_is_not_live_jar=true`).

See [engineering/g1-fieldpolicy-mask-boundsql-export-remainder-honesty-EVIDENCE.md](./engineering/g1-fieldpolicy-mask-boundsql-export-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/`.

## Honest leftovers (not claimed)

- Encoding mask / 二次授权 onto Query AST nodes
- IsolatedExport applying `FieldPolicyProjector` masks
- Treating #238 / #215 harness PASS as a live product-JAR / IsolatedExport mask / BoundSql walk
- Live product-JAR list/detail FieldPolicy mask / live IsolatedExport mask / live second-auth grant
- Live leftover BoundSql / COUNT / IsolatedExport hidden-drop walk
- UI / H5 / report UI FieldPolicy walk
- Product-wide G1 close
- Redo of #115 / #130 / #134 / #142 / #146 / #162 / #168 / #184 / #215 / #238
