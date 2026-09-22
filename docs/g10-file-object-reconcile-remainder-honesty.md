# G10 leftover — file-reconcile / object-reconcile PASS/SKIP honesty remainder beyond #228/#202/#169/#179/#141

S0+S1 remainder **after** the #228 leftover honesty remainder ([g10-leftover-honesty-remainder.md](./g10-leftover-honesty-remainder.md)), the #202 leftover honesty leftover ([g10-leftover-honesty.md](./g10-leftover-honesty.md)), the #169 file-reconcile honesty leftover ([g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md)), the #179 identity-drift honesty leftover ([g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md)), and the #141 identity drift-fix dry-run leftover ([g10-identity-drift-dry-run.md](./g10-identity-drift-dry-run.md)). Rebased onto `origin/main` `d4252cc` (Merge #241 SecretPort rotate/getenv leftover honesty after #235 G5 PublishHook/DDL leftover honesty). **G18 stays closed.** This leftover does **not** redo #141 identity drift-fix dry-run, #169 file-reconcile honesty, #179 identity-drift honesty, #202 leftover honesty, or #228 leftover honesty remainder product code. It does **not** rewrite `FileObjectReconciler`, identity apply, or `AuditPort`. It does **not** claim product G10 close. `g10_close=NOT_CLAIMED`. No P0/M0 redo. No SAML / LDAP / SMS MFA.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g10-identity-drift-dry-run](./g10-identity-drift-dry-run.md) (#141), [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md) (#169), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g17-mfa-recovery-vue-otp-remainder-honesty](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g5-publishhook-ddl-remainder-honesty](./g5-publishhook-ddl-remainder-honesty.md) (#235), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md) (#197), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), and every leftover-style sibling already on `origin/main`. Keep this file.

#169 already locked counting preview vs apply + tenant-prefix as **PASS**, and live MinIO as honest **SKIP** when `S3_*` is unset. #179 already locked dry-run default no-write, live apply **SKIP**, and live Keycloak 8080 as honest **SKIP** when the issuer is down. #202 already is the leftover honesty gate beyond those two. #228 already is the remainder honesty gate beyond #202. That #228/#202 harness PASS is **not** a live MinIO walk, **not** a live object-store reconcile, and **not** a product G10 close. The leftover here is the **honesty gate beyond those four**: those cells stay honest; parent #228/#202 leftover honesty fixtures stay **PASS** and are explicitly **not** a live MinIO / object walk (`harness_228_is_not_live_minio=true`, `harness_228_is_not_live_object=true`, `harness_202_is_not_live_minio=true`). Live MinIO preview/apply/prefix and live object-store reconcile are **PASS** only when the endpoint is up **and** that walk was observed. Missing MinIO / Keycloak is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #169 cells still honest | Committed `counting_preview_apply=PASS`, `tenant_prefix_guard=PASS`, `live_minio_reconcile=SKIP`, `g10_close=NOT_CLAIMED`. `leftover_169_cells_honest=PASS`. Do not redo. |
| #179 cells still honest | Committed `dry_run_default_no_write=true`, `live_keycloak_8080=SKIP`, `live_apply=SKIP`, `g10_close=NOT_CLAIMED`. `leftover_179_cells_honest=PASS`. Do not redo. |
| #202 cells still honest | Committed leftover #169/#179 cells PASS; committed live MinIO / Keycloak SKIP; `g10_close=NOT_CLAIMED`. `leftover_202_cells_honest=PASS`. `leftover_202_honesty_gate=PASS`. `#202` harness is **not** a live MinIO walk. Do not redo. |
| #228 cells still honest | Committed leftover #202 cells PASS; parent leftover honesty fixtures PASS; committed live MinIO / Keycloak SKIP; `g10_close=NOT_CLAIMED`. `leftover_228_cells_honest=PASS`. `leftover_228_honesty_gate=PASS`. Do not redo. |
| #228 harness vs live MinIO | Always-green `leftover_228_honesty_gate=PASS` **and** `harness_228_is_not_live_minio=true`. `#228` harness is **not** a live MinIO walk. |
| #228 harness vs live object | `harness_228_is_not_live_object=true`. `#228` harness is **not** a live object-store reconcile. |
| #228 harness vs product G10 | `harness_228_is_not_product_g10=true`. `#228` PASS is **not** product G10 close. |
| Live MinIO / object inventory | Probe `S3_ENDPOINT` + loopback `:19000` / `:9000`. Credentials stay in env / SecretPort. This leftover does not start MinIO. |
| Live Keycloak 8080 | Probe loopback `:8080` + `NEXUS_KEYCLOAK_ISSUER` OIDC discovery. This leftover does not start Keycloak. |
| Honest SKIP | Unset `S3_*`, down MinIO, down Keycloak, or no observed walk stays `SKIP` with a reason. |
| Live apply | Always `SKIP`. Product apply is not redone. |
| Sibling leftover rows | `docs/README.md` keeps #141 / #169 / #179 / #202 / #228 and this leftover, plus #232 `g2-h5-every-page-form-write-remainder-honesty`, #237 `g9-redis-down-invalidate-remainder-honesty`, #239 `g7-query-expense-travel-honesty-remainder`, #238 `g1-fieldpolicy-mask-second-auth-remainder-honesty`, #233 `g17-mfa-recovery-vue-otp-remainder-honesty`, #235 `g5-publishhook-ddl-remainder-honesty`, #241 `s0-secret-rotate-getenv-leftover-honesty`, #212, #230, #229, #226, #225, #223, #222, #216, #220, #215, #210, #217, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects `live_minio_reconcile=PASS` / `live_object_reconcile=PASS` / `live_keycloak_8080=PASS` without present + observed walk, rejects treating #228 harness as a live MinIO / object walk, rejects `live_apply=PASS`, rejects `g10_close=CLOSED`, and rejects #141 / #169 / #179 / #202 / #228 redo flags. |

No Docker. No `#141` / `#169` / `#179` / `#202` / `#228` script re-run. `path` remains `files.reconcile` / object-store reconcile. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G10 close | Explicitly `g10_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. `#228` harness PASS is not product G10 (`harness_228_is_not_product_g10=true`). |
| #228 harness as live MinIO | `harness_228_is_not_live_minio=true`. Remainder honesty gate is not a live walk. |
| #228 harness as live object reconcile | `harness_228_is_not_live_object=true`. |
| Live MinIO preview + apply + tenant-prefix | This VM has `S3_*` unset; nothing on `:19000` / `:9000`. Honesty SKIP. |
| Live Keycloak 8080 dry-run | Nothing on `:8080`. Honesty SKIP. |
| Identity apply product path | `identity_apply_product_redone=false`. Apply stays on [g10-identity-reconcile.md](./g10-identity-reconcile.md). |
| #141 / #169 / #179 / #202 / #228 redo | Sibling leftovers. `identity_drift_141_redone=false`. `file_reconcile_169_redone=false`. `identity_drift_179_redone=false`. `leftover_202_redone=false`. `leftover_228_redone=false`. |
| SAML / LDAP / SMS MFA | Explicitly `*_claimed=false`. Not implemented. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Product G2 remainder leftover (#232) / product G9 Redis-down leftover (#237) / product G7 leftover (#239/#212) / product G1 FieldPolicy leftover (#238) / product G17 MFA/OTP leftover (#233) / product G5 PublishHook leftover (#235) / product S0 SecretPort leftover (#241) / product G4 leftover (#230) / product G9 leftover (#229) / product G3 leftover (#226) | Sibling leftovers. Not rewritten. Not claimed. |
| Docker / Compose | Forbidden. Native MinIO / `kc.sh` only when the operator already started them. |

## How to run

```bash
./scripts/g10-file-object-reconcile-remainder-honesty.sh
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-live-minio-pass.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-live-keycloak-pass.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-live-apply-pass.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-g10-close.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-redone.env fail
./scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-harness-as-live.env fail
```

#141 / #169 / #179 / #202 / #228 stay the sibling reproduction commands. This leftover does **not** invoke their collect paths:

```bash
# do not run for this leftover
# ./scripts/g10-identity-drift-dry-run.sh
# ./scripts/g10-file-reconcile-honesty.sh
# ./scripts/g10-identity-drift-honesty.sh
# ./scripts/g10-leftover-honesty.sh
# ./scripts/g10-leftover-honesty-remainder.sh
# ./nexus-p2-iam/scripts/run-g10-identity-reconcile.sh
```

Parent `--verify-report` on already-committed #228 / #202 honesty fixtures is harness evidence.

`PASS` for live MinIO / object reconcile requires all of: reachable object store, credentials, observed preview (`applied=false`), observed apply, and tenant-prefix guard. `PASS` for live Keycloak 8080 requires listener + OIDC discovery + observed dry-run. This leftover does not start those processes. `#228` harness PASS is still not that walk (`harness_228_is_not_live_minio=true`).

See [engineering/g10-file-object-reconcile-remainder-honesty-EVIDENCE.md](./engineering/g10-file-object-reconcile-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g10-file-object-reconcile-remainder-honesty/`.

## Honest leftovers (not claimed)

- Live MinIO / object-store preview + apply + tenant-prefix walk on a host that actually has that process
- Treating #228 / #202 harness PASS as a live MinIO / object-store walk
- Live Keycloak 26.5.2 `:8080` detect + proposed-fix dry-run on a host that actually has that process
- Product identity apply (`POST {"apply":true}`)
- Product G10 close
- SAML / LDAP / Active Directory / SMS MFA / Keycloak Organizations
- Redo of #141 / #169 / #179 / #202 / #228
