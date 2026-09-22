# G10 leftover — file-reconcile / identity-drift PASS/SKIP honesty remainder beyond #202/#169/#179/#141

S0+S1 remainder **after** the #202 leftover honesty leftover ([g10-leftover-honesty.md](./g10-leftover-honesty.md)), the #169 file-reconcile honesty leftover ([g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md)), the #179 identity-drift honesty leftover ([g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md)), and the #141 identity drift-fix dry-run leftover ([g10-identity-drift-dry-run.md](./g10-identity-drift-dry-run.md)). Rebased onto `origin/main` `426a78e` (Merge #221 G2 report/physical-dump leftover honesty after #225 S0 rotate/getenv remainder leftover honesty after #222 G4 PKCE SPA-verifier remainder leftover honesty / #216 G17 SoD leftover honesty / #218 claim-list tip-sync / #220 G15 wx-dt/native-bridge remainder leftover honesty / #215 G1 BoundSql/COUNT/export leftover honesty / #210 G17 Tongsuo/Nginx GMTLS handshake leftover honesty / #217 G5 PublishHook/DDL leftover honesty). **G18 stays closed.** This leftover does **not** redo #141 identity drift-fix dry-run, #169 file-reconcile honesty, #179 identity-drift honesty, or #202 leftover honesty product code. It does **not** rewrite `FileObjectReconciler`, identity apply, or `AuditPort`. It does **not** claim product G10 close. `g10_close=NOT_CLAIMED`. No P0/M0 redo. No SAML / LDAP / SMS MFA.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g10-identity-drift-dry-run](./g10-identity-drift-dry-run.md) (#141), [g10-file-reconcile-honesty](./g10-file-reconcile-honesty.md) (#169), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md) (#197), [g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md) (#203), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md) (#180), and [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171). Keep this file. Follow-on leftover honesty remainder beyond this file (does **not** redo this leftover): [g10-file-object-reconcile-remainder-honesty.md](./g10-file-object-reconcile-remainder-honesty.md).

#169 already locked counting preview vs apply + tenant-prefix as **PASS**, and live MinIO as honest **SKIP** when `S3_*` is unset. #179 already locked dry-run default no-write, live apply **SKIP**, and live Keycloak 8080 as honest **SKIP** when the issuer is down. #202 already is the leftover honesty gate beyond those two. The leftover here is the **remainder honesty gate beyond those three**: those cells stay honest; parent #202 leftover honesty fixtures stay **PASS**; live MinIO preview/apply/prefix and live Keycloak 8080 dry-run are **PASS** only when the endpoint is up **and** that walk was observed. Missing MinIO / Keycloak is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #169 cells still honest | Committed `counting_preview_apply=PASS`, `tenant_prefix_guard=PASS`, `live_minio_reconcile=SKIP`, `g10_close=NOT_CLAIMED`. `leftover_169_cells_honest=PASS`. Do not redo. |
| #179 cells still honest | Committed `dry_run_default_no_write=true`, `live_keycloak_8080=SKIP`, `live_apply=SKIP`, `g10_close=NOT_CLAIMED`. `leftover_179_cells_honest=PASS`. Do not redo. |
| #202 cells still honest | Committed leftover #169/#179 cells PASS; committed live MinIO / Keycloak SKIP; `g10_close=NOT_CLAIMED`. Parent `--verify-report` honest SKIP accepted; fake live MinIO / Keycloak / apply PASS and fake `g10_close=CLOSED` rejected. `leftover_202_cells_honest=PASS`. `leftover_202_honesty_gate=PASS`. Do not redo. |
| Live MinIO inventory | Probe `S3_ENDPOINT` + loopback `:19000` / `:9000`. Credentials stay in env / SecretPort. This leftover does not start MinIO. |
| Live Keycloak 8080 | Probe loopback `:8080` + `NEXUS_KEYCLOAK_ISSUER` OIDC discovery. This leftover does not start Keycloak. |
| Honest SKIP | Unset `S3_*`, down MinIO, down Keycloak, or no observed walk stays `SKIP` with a reason. |
| Live apply | Always `SKIP`. Product apply is not redone. |
| Sibling leftover rows | `docs/README.md` keeps #141 / #169 / #179 / #202 and this leftover, plus #221 `g2-report-physical-dump-honesty`, #225 `g6-recovery-port-dry-run-remainder-honesty`, #223 `s0-secret-rotate-getenv-remainder-honesty`, #222 `g4-pkce-spa-verifier-remainder-honesty`, #216 `g17-separation-leftover-honesty`, #220 `g15-wx-dt-native-bridge-remainder`, #215 `g1-queryast-export-honesty`, #210 `g17-gm-tls-nginx-handshake-honesty`, #217 `g5-publishhook-ddl-honesty`, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects `live_minio_reconcile=PASS` / `live_keycloak_8080=PASS` without present + observed walk, rejects `live_apply=PASS`, rejects `g10_close=CLOSED`, and rejects #141 / #169 / #179 / #202 redo flags. |

No Docker. No `#141` / `#169` / `#179` / `#202` script re-run. `path` remains `files.reconcile` / `identity.reconcile`. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G10 close | Explicitly `g10_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| Live MinIO preview + apply + tenant-prefix | This VM has `S3_*` unset; nothing on `:19000` / `:9000`. Honesty SKIP. |
| Live Keycloak 8080 dry-run | Nothing on `:8080`. Honesty SKIP. |
| Identity apply product path | `identity_apply_product_redone=false`. Apply stays on [g10-identity-reconcile.md](./g10-identity-reconcile.md). |
| #141 / #169 / #179 / #202 redo | Sibling leftovers. `identity_drift_141_redone=false`. `file_reconcile_169_redone=false`. `identity_drift_179_redone=false`. `leftover_202_redone=false`. |
| SAML / LDAP / SMS MFA | Explicitly `*_claimed=false`. Not implemented. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |
| Docker / Compose | Forbidden. Native MinIO / `kc.sh` only when the operator already started them. |

## How to run

```bash
./scripts/g10-leftover-honesty-remainder.sh
./scripts/g10-leftover-honesty-remainder.sh --verify-report <out>/report.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-live-minio-pass.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-live-keycloak-pass.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-live-apply-pass.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-g10-close.env
./scripts/g10-leftover-honesty-remainder.sh --verify-report \
  scripts/g10-leftover-honesty-remainder/fixtures/fake-redone.env
```

#141 / #169 / #179 / #202 stay the sibling reproduction commands. This leftover does **not** invoke their collect paths:

```bash
# do not run for this leftover
# ./scripts/g10-identity-drift-dry-run.sh
# ./scripts/g10-file-reconcile-honesty.sh
# ./scripts/g10-identity-drift-honesty.sh
# ./scripts/g10-leftover-honesty.sh
# ./nexus-p2-iam/scripts/run-g10-identity-reconcile.sh
```

Parent `--verify-report` on already-committed #202 honesty fixtures is harness evidence.

`PASS` for live MinIO requires all of: reachable object store, credentials, observed preview (`applied=false`), observed apply, and tenant-prefix guard. `PASS` for live Keycloak 8080 requires listener + OIDC discovery + observed dry-run. This leftover does not start those processes.

See [engineering/g10-leftover-honesty-remainder-EVIDENCE.md](./engineering/g10-leftover-honesty-remainder-EVIDENCE.md). Live `report.env` is gitignored under `.local/g10-leftover-honesty-remainder/`.

## Honest leftovers (not claimed)

- Live MinIO / object-store preview + apply + tenant-prefix walk on a host that actually has that process
- Live Keycloak 26.5.2 `:8080` detect + proposed-fix dry-run on a host that actually has that process
- Product identity apply (`POST {"apply":true}`)
- Product G10 close
- SAML / LDAP / Active Directory / SMS MFA / Keycloak Organizations
- Redo of #141 / #169 / #179 / #202
