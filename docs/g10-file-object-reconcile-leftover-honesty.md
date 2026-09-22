# G10 leftover — file-reconcile / object-reconcile PASS/SKIP leftover honesty beyond #243/#228/#202/#169/#179/#141

S0+S1 remainder **after** the #243 file/object reconcile remainder honesty leftover ([g10-file-object-reconcile-remainder-honesty.md](./g10-file-object-reconcile-remainder-honesty.md)), the #228 leftover honesty remainder ([g10-leftover-honesty-remainder.md](./g10-leftover-honesty-remainder.md)), the #202 leftover honesty leftover ([g10-leftover-honesty.md](./g10-leftover-honesty.md)), the #169 file-reconcile honesty leftover ([g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md)), the #179 identity-drift honesty leftover ([g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md)), and the #141 identity drift-fix dry-run leftover ([g10-identity-drift-dry-run.md](./g10-identity-drift-dry-run.md)). Rebased onto `origin/main` `e10be1f` (Merge #259 G9 leftover honesty remainder after #249 G17 SoD/admin-force leftover honesty / #240 G15 H5/PKCE leftover honesty / #254 G2 H5/form.write/report leftover honesty / #236 Linux/ARM profile leftover honesty / #251 G4 hot-purge/staging/PKCE leftover honesty / #243 G10 file/object reconcile leftover honesty). **G18 stays closed.** This leftover does **not** redo #141 identity drift-fix dry-run, #169 file-reconcile honesty, #179 identity-drift honesty, #202 leftover honesty, #228 leftover honesty remainder, or #243 file/object remainder honesty product code. It does **not** rewrite `FileObjectReconciler`, identity apply, or `AuditPort`. It does **not** claim product G10 close. `g10_close=NOT_CLAIMED`. No P0/M0 redo. No SAML / LDAP / SMS MFA.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g10-file-object-reconcile-remainder-honesty](./g10-file-object-reconcile-remainder-honesty.md) (#243), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md) (#169), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g10-identity-drift-dry-run](./g10-identity-drift-dry-run.md) (#141), [g2-h5-every-page-form-write-report-remainder-honesty](./g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [g15-h5-pkce-miniprogram-remainder-honesty](./g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g17-separation-leftover-honesty-remainder](./g17-separation-leftover-honesty-remainder.md) (#249), [g9-leftover-honesty-remainder](./g9-leftover-honesty-remainder.md) (#259), [linux-arm-profile-remainder-honesty](./linux-arm-profile-remainder-honesty.md) (#236), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g4-hot-purge-staging-pkce-remainder-honesty](./g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g5-publishhook-ddl-remainder-honesty](./g5-publishhook-ddl-remainder-honesty.md) (#235), [g17-mfa-recovery-vue-otp-remainder-honesty](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), and every leftover-style sibling already on `origin/main`. Keep this file.

#169 already locked counting preview vs apply + tenant-prefix as **PASS**, and live MinIO as honest **SKIP** when `S3_*` is unset. #179 already locked dry-run default no-write, live apply **SKIP**, and live Keycloak 8080 as honest **SKIP** when the issuer is down. #202 already is the leftover honesty gate beyond those two. #228 already is the remainder honesty gate beyond #202. #243 already is the file/object remainder honesty gate beyond #228. Those #243/#228/#202 harness PASS cells are **not** a live MinIO walk, **not** a live object-store reconcile, and **not** a product G10 close. The leftover here is the **honesty gate beyond those five**: those cells stay honest; parent #243/#228/#202 leftover honesty fixtures stay **PASS** and are explicitly **not** a live MinIO / object walk (`harness_243_is_not_live_minio=true`, `harness_243_is_not_live_object=true`, `harness_243_is_not_product_g10=true`, `harness_228_is_not_live_minio=true`, `harness_202_is_not_live_minio=true`). Live MinIO preview/apply/prefix and live object-store reconcile are **PASS** only when the endpoint is up **and** that walk was observed. Missing MinIO / Keycloak is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #169 cells still honest | Committed `counting_preview_apply=PASS`, `tenant_prefix_guard=PASS`, `live_minio_reconcile=SKIP`, `g10_close=NOT_CLAIMED`. `leftover_169_cells_honest=PASS`. Do not redo. |
| #179 cells still honest | Committed `dry_run_default_no_write=true`, `live_keycloak_8080=SKIP`, `live_apply=SKIP`, `g10_close=NOT_CLAIMED`. `leftover_179_cells_honest=PASS`. Do not redo. |
| #202 cells still honest | Committed leftover #169/#179 cells PASS; committed live MinIO / Keycloak SKIP; `g10_close=NOT_CLAIMED`. `leftover_202_cells_honest=PASS`. `#202` harness is **not** a live MinIO walk. Do not redo. |
| #228 cells still honest | Committed leftover #202 cells PASS; parent leftover honesty fixtures PASS; committed live MinIO / Keycloak SKIP; `g10_close=NOT_CLAIMED`. `leftover_228_cells_honest=PASS`. Do not redo. |
| #243 cells still honest | Committed leftover #228 cells PASS; parent remainder honesty fixtures PASS; committed live MinIO / Keycloak SKIP; `g10_close=NOT_CLAIMED`. `leftover_243_cells_honest=PASS`. `leftover_243_honesty_gate=PASS`. Do not redo. |
| #243 harness vs live MinIO | Always-green `leftover_243_honesty_gate=PASS` **and** `harness_243_is_not_live_minio=true`. `#243` harness is **not** a live MinIO walk. |
| #243 harness vs live object | `harness_243_is_not_live_object=true`. `#243` harness is **not** a live object-store reconcile. |
| #243 harness vs product G10 | `harness_243_is_not_product_g10=true`. `#243` PASS is **not** product G10 close. |
| Live MinIO / object inventory | Probe `S3_ENDPOINT` + loopback `:19000` / `:9000`. Credentials stay in env / SecretPort. This leftover does not start MinIO. |
| Live Keycloak 8080 | Probe loopback `:8080` + `NEXUS_KEYCLOAK_ISSUER` OIDC discovery. This leftover does not start Keycloak. |
| Honest SKIP | Unset `S3_*`, down MinIO, down Keycloak, or no observed walk stays `SKIP` with a reason. |
| Live apply | Always `SKIP`. Product apply is not redone. |
| Sibling leftover rows | `docs/README.md` keeps #141 / #169 / #179 / #202 / #228 / #243 and this leftover, plus #254 `g2-h5-every-page-form-write-report-remainder-honesty`, #240 `g15-h5-pkce-miniprogram-remainder-honesty`, #249 `g17-separation-leftover-honesty-remainder`, #259 `g9-leftover-honesty-remainder`, #236 `linux-arm-profile-remainder-honesty`, #251 `g4-hot-purge-staging-pkce-remainder-honesty`, and every leftover-style sibling already on `origin/main`. |
| Honesty gate | `--verify-report` rejects `live_minio_reconcile=PASS` / `live_object_reconcile=PASS` / `live_keycloak_8080=PASS` without present + observed walk, rejects treating #243/#228 harness as a live MinIO / object walk, rejects `live_apply=PASS`, rejects `g10_close=CLOSED`, and rejects #141 / #169 / #179 / #202 / #228 / #243 redo flags. |

No Docker. No `#141` / `#169` / `#179` / `#202` / `#228` / `#243` script re-run. `path` remains `files.reconcile` / object-store reconcile. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G10 close | Explicitly `g10_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. `#243` harness PASS is not product G10 (`harness_243_is_not_product_g10=true`). |
| #243 harness as live MinIO | `harness_243_is_not_live_minio=true`. Remainder honesty gate is not a live walk. |
| #243 harness as live object reconcile | `harness_243_is_not_live_object=true`. |
| Live MinIO preview + apply + tenant-prefix | This VM has `S3_*` unset; nothing on `:19000` / `:9000`. Honesty SKIP. |
| Live Keycloak 8080 dry-run | Nothing on `:8080`. Honesty SKIP. |
| Identity apply product path | `identity_apply_product_redone=false`. Apply stays on [g10-identity-reconcile.md](./g10-identity-reconcile.md). |
| #141 / #169 / #179 / #202 / #228 / #243 redo | Sibling leftovers. `leftover_243_redone=false`. |
| SAML / LDAP / SMS MFA | Explicitly `*_claimed=false`. Not implemented. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product G2 remainder leftover (#254/#232/#221) / product G15 H5/PKCE remainder leftover (#240) / product G17 SoD remainder leftover (#249/#216) / product G9 leftover remainder (#259/#237/#229) / product Linux/ARM Profile leftover (#236) / product G4 leftover (#251/#230) / product S0 leftover (#241) | Sibling leftovers. Not rewritten. Not claimed. |
| Docker / Compose | Forbidden. Native MinIO / `kc.sh` only when the operator already started them. |

## How to run

```bash
./scripts/g10-file-object-reconcile-leftover-honesty.sh
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-live-minio-pass.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-live-keycloak-pass.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-live-apply-pass.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-g10-close.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-redone.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-harness-as-live.env fail
```

#141 / #169 / #179 / #202 / #228 / #243 stay the sibling reproduction commands. This leftover does **not** invoke their collect paths:

```bash
# do not run for this leftover
# ./scripts/g10-identity-drift-dry-run.sh
# ./scripts/g10-file-reconcile-honesty.sh
# ./scripts/g10-identity-drift-honesty.sh
# ./scripts/g10-leftover-honesty.sh
# ./scripts/g10-leftover-honesty-remainder.sh
# ./scripts/g10-file-object-reconcile-remainder-honesty.sh
# ./nexus-p2-iam/scripts/run-g10-identity-reconcile.sh
```

Parent `--verify-report` on already-committed #243 / #228 / #202 honesty fixtures is harness evidence.

`PASS` for live MinIO / object reconcile requires all of: reachable object store, credentials, observed preview (`applied=false`), observed apply, and tenant-prefix guard. `PASS` for live Keycloak 8080 requires listener + OIDC discovery + observed dry-run. This leftover does not start those processes. `#243` harness PASS is still not that walk (`harness_243_is_not_live_minio=true`).

See [engineering/g10-file-object-reconcile-leftover-honesty-EVIDENCE.md](./engineering/g10-file-object-reconcile-leftover-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g10-file-object-reconcile-leftover-honesty/`.

## Honest leftovers (not claimed)

- Live MinIO / object-store preview + apply + tenant-prefix walk on a host that actually has that process
- Treating #243 / #228 / #202 harness PASS as a live MinIO / object-store walk
- Live Keycloak 26.5.2 `:8080` detect + proposed-fix dry-run on a host that actually has that process
- Product identity apply (`POST {"apply":true}`)
- Product G10 close
- SAML / LDAP / Active Directory / SMS MFA / Keycloak Organizations
- Redo of #141 / #169 / #179 / #202 / #228 / #243
