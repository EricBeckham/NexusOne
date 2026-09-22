# G1 leftover — FieldPolicy mask / second-auth remainder honesty beyond #184/#162/#215

S0+S1 remainder **after** the #184 FieldPolicy mask / second-auth leftover honesty ([g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md)), the #162 leftoverMaskFields harness ([g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md)), and the #215 BoundSql / COUNT / export FieldPolicy drop leftover honesty ([g1-queryast-export-honesty.md](./g1-queryast-export-honesty.md)). Rebased onto `origin/main` `c800294` (Merge #239 G7 expense/travel query leftover honesty remainder after #237 G9 Redis-down/invalidate leftover honesty remainder / #232 G2 H5 every-page / form.write leftover honesty remainder / #212 G7 withdraw browser-evidence leftover honesty / #230 G4 hot-purge/P1 staging leftover honesty / #231 S0+S1 claim-list tip-sync / #229 G9 live multi-JVM / replica remainder leftover honesty / #226 G3 Outbox/claim-crash leftover honesty / #228 G10 leftover honesty remainder / #221 G2 report/physical-dump leftover honesty / #225 G6 RecoveryPort leftover honesty / #224 S0+S1 claim-list tip-sync / #223 S0 rotate/getenv remainder leftover honesty / #222 G4 live Keycloak CA / SPA PKCE remainder leftover honesty / #216 G17 SoD leftover honesty / #218 S0+S1 claim-list tip-sync / #220 G15 WeChat/DingTalk native-bridge remainder leftover / #215 G1 BoundSql/COUNT/export leftover honesty). **G18 stays closed.** This leftover does **not** redo #115 visibility AST, #130 list/detail opt-in, #134 export hidden drop, #142 single-column `IN`, #146 leftover BoundSql / COUNT, #162 leftoverMaskFields / projector harness, #168 SELF∨DEPT OR, #184 FieldPolicy leftover honesty product code, or #215 BoundSql / COUNT / export leftover honesty product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** invent a second tenant context. It does **not** redo P0/M0. It does **not** claim a product G1 close. `g1_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md) (#162), [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g1-queryast-export-honesty.md](./g1-queryast-export-honesty.md) (#215), [g1-queryast-boundsql-guard.md](./g1-queryast-boundsql-guard.md) (#146), [g1-queryast-boundsql-honesty.md](./g1-queryast-boundsql-honesty.md) (#191), [g1-export-fieldpolicy-drop.md](./g1-export-fieldpolicy-drop.md) (#134), [g1-datascope-or-in-ast.md](./g1-datascope-or-in-ast.md) (#142/#168), [g9-live-multi-jvm-replica-remainder-honesty.md](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g4-hot-purge-staging-leftover-honesty.md](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g7-withdraw-browser-evidence-honesty.md](./g7-withdraw-browser-evidence-honesty.md) (#212), [g2-h5-every-page-form-write-remainder-honesty.md](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty.md](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g7-query-expense-travel-honesty-remainder.md](./g7-query-expense-travel-honesty-remainder.md) (#239), [g3-outbox-consume-idempotent-honesty.md](./g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder.md](./g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](./g15-wx-dt-native-bridge-remainder.md) (#220), [g17-gm-tls-nginx-handshake-honesty.md](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](./g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g9-redis-down-invalidate-remainder.md](./g9-redis-down-invalidate-remainder.md) (#197), [g2-h5-every-page-form-write-honesty.md](./g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty.md](./g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](./g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](./g3-outbox-consume-crash-honesty.md) (#195), [g15-wx-dt-native-bridge-honesty.md](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md) (#183), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) (#175), [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) (#169), and [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179). Keep this file.

#162 already proved mask + 二次授权 stay on `FieldPolicyProjector` (`leftoverMaskFields`). Compiled Query AST still selects raw `"phone"`. Granted IsolatedExport still writes **raw** cells. #184 already is the leftover honesty gate beyond that: parent leftoverMaskFields stay **PASS**; live JAR / IsolatedExport mask / second-auth stay honest **SKIP** when unobserved. #215 already locked leftover BoundSql / COUNT / export hidden-drop as **PASS** and live product-JAR walks as honest **SKIP**. The leftover here is the **honesty remainder beyond those three**: those cells stay honest; parent leftover honesty fixtures stay **PASS**. Live product-JAR list/detail mask, IsolatedExport applying masks, and a live second-auth grant are **PASS** only when a listener is present **and** the walk was observed. Missing `NEXUS_BASE_URL` / issued context / IsolatedExport mask walk is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #162 leftoverMaskFields | Already on `main`. Mask / 二次授权 stay beside Query AST. `leftover_162_cells_honest=PASS`. `leftover_beside_ast=PASS`. Do not redo. |
| #184 honesty cells | Committed leftover beside AST PASS; `mask_on_ast=false`; IsolatedExport still writes raw cells; live JAR / IsolatedExport mask / second-auth SKIP. Parent `--verify-report` honest SKIP accepted; fake live PASS / fake `g1_close=CLOSED` / fake `mask_on_ast=true` rejected. `leftover_184_cells_honest=PASS`. `leftover_184_honesty_gate=PASS`. Do not redo. |
| #215 BoundSql / export | Committed leftover BoundSql / COUNT / export hidden-drop stay honest. IsolatedExport still does **not** apply masks. `leftover_215_cells_honest=PASS`. Do not redo. |
| IsolatedExport mask | #162/#184 proved granted export writes raw cells. `isolated_export_applies_mask=false`. Claiming `true` is rejected. |
| Live JAR list/detail mask | `live_api_mask=SKIP` unless healthy JAR **and** issued context **and** `live_api_mask_observed=true`. |
| Live IsolatedExport mask | `live_export_mask=SKIP` unless JAR present **and** a mask-applying export walk was observed. |
| Live second-auth grant | `live_second_auth=SKIP` unless JAR present **and** a grant walk was observed. |
| UI / H5 / report UI | `ui_h5_report=SKIP`. Not walked. |
| Sibling leftover rows | `docs/README.md` keeps #162 / #184 / #215 and this leftover, plus #229 `g9-live-multi-jvm-replica-remainder-honesty`, #230 `g4-hot-purge-staging-leftover-honesty`, #212 `g7-withdraw-browser-evidence-honesty`, #232 `g2-h5-every-page-form-write-remainder-honesty`, #237 `g9-redis-down-invalidate-remainder-honesty`, #239 `g7-query-expense-travel-honesty-remainder`, #226 `g3-outbox-consume-idempotent-honesty`, #228 `g10-leftover-honesty-remainder`, #221 `g2-report-physical-dump-honesty`, #225 `g6-recovery-port-dry-run-remainder-honesty`, #223 `s0-secret-rotate-getenv-remainder-honesty`, #222 `g4-pkce-spa-verifier-remainder-honesty`, #216 `g17-separation-leftover-honesty`, #220 `g15-wx-dt-native-bridge-remainder`, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g1_close=CLOSED`, rejects `mask_on_ast=true`, rejects IsolatedExport-applies-mask, rejects #162/#184/#215 redo flags, and requires leftover rows kept. |

No Docker. No product-JAR start. `#162` / `#184` / `#215` stay the sibling reproduction commands. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G1 close | Explicitly `g1_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Mask / second-auth on Query AST | Still false. Fake `mask_on_ast=true` reports fail. |
| IsolatedExport applying masks | #162 leftover: granted export still writes raw cells. |
| #115 / #130 / #134 / #142 / #146 / #162 / #168 / #184 / #215 | Sibling leftovers on `main`. `redone_162=false`. `redone_184=false`. `redone_215=false`. |
| Live product-JAR FieldPolicy walk | Honest SKIP on this agent VM. |
| UI / H5 / report UI | Not re-walked. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

```bash
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-live-api-mask-pass.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-live-export-mask-pass.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-live-second-auth-pass.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-g1-close.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-mask-on-ast.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-redone.env
```

#162 / #184 / #215 stay the sibling reproduction commands. This leftover does **not** invoke them:

```bash
# do not run for this leftover
# ./scripts/g1-fieldpolicy-mask-second-auth-honesty.sh
# ./scripts/g1-fieldpolicy-mask-leftover-honesty.sh
# ./scripts/g1-queryast-export-honesty.sh
```

Parent `--verify-report` on already-committed #184 / #215 honesty fixtures is harness evidence.

Java 21 / Boot **4.1.1**. Isolation-kernel compile path is in-process. No Docker. Live product-JAR / IsolatedExport mask are **not** required.

See [engineering/g1-fieldpolicy-mask-second-auth-remainder-honesty-EVIDENCE.md](./engineering/g1-fieldpolicy-mask-second-auth-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g1-fieldpolicy-mask-second-auth-remainder-honesty/`.

## Honest leftovers (not claimed)

- Encoding mask / 二次授权 onto Query AST nodes
- IsolatedExport applying `FieldPolicyProjector` masks
- Live product-JAR list/detail FieldPolicy mask / live IsolatedExport mask / live second-auth grant
- UI / H5 / report UI FieldPolicy walk
- Product-wide G1 close
- Redo of #115 / #130 / #134 / #142 / #146 / #162 / #168 / #184 / #215
