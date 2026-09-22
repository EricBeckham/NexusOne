# G17 leftover — 三权分立 admin-force vs dual-control honesty beyond #99/#187

S0+S1 remainder **after** the #99 three-power slice ([g17-separation-of-duties.md](./g17-separation-of-duties.md)) and the #187 SoD leftover honesty ([g17-separation-honesty.md](./g17-separation-honesty.md)). Rebased onto `origin/main` `e695290` (Merge #218 S0+S1 claim-list tip-sync after #220 G15 wx-dt/native-bridge remainder leftover honesty; includes #215 G1 BoundSql/COUNT/export FieldPolicy leftover honesty, #210 G17 Tongsuo/Nginx GMTLS handshake leftover honesty, #217 G5 PublishHook/DDL leftover honesty, #209 G9 Sentinel/Cluster remainder leftover honesty, #214 tip-sync, #188 G2 ops pg_dump leftover honesty, #205 MFA recovery/OTP leftover honesty, #197 G9 Redis-down invalidate remainder honesty, and #204 G2 H5/form.write leftover honesty). **G18 stays closed.** This leftover does **not** redo #96 `AdminMfaPolicy`, #99 `PlatformDutyCatalog` / assign / evaluate / audit-admin ITs, or #187 leftover product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0 / M0. It does **not** claim 等保 / 密评 / 完整 G17 / product SoD / product dual-control (maker-checker) close. `g17_close=NOT_CLAIMED`. `g17_sod_close=NOT_CLAIMED`. `product_dual_control_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g17-separation-of-duties](./g17-separation-of-duties.md) (#99), [g17-separation-honesty](./g17-separation-honesty.md) (#187), this file, g17-* MFA/TOTP/Tongsuo siblings, [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md) (#188), [g2-ops-pg-dump](./g2-ops-pg-dump.md) (#155), [g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g2-h5-every-page](./g2-h5-every-page.md) (#163), [g2-form-write-honesty](./g2-form-write-honesty.md) (#173), [g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty](./g10-leftover-honesty.md) (#202), [g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md) (#197), [g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md) (#174), [g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish](./g5-browser-failed-publish.md) (#171), and [g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md) (#175). Keep this file.

#99 already catalogues three mutually exclusive platform duties and rejects a second duty (`P2.PERM.0014` / `duty_conflict`). #187 already inventories live admin/auditor/operator JAR+IdP HTTP as honest **SKIP**. After G2 SurfaceGuard and G4 deny-on-chain landed, the leftover here is the **honesty inventory for admin force paths vs dual-control (maker-checker)**:

| Remaining path | Honesty |
|---|---|
| #99 catalog exclusive pairs / assign reject / `duty_conflict` | Always-green **PASS**. Not a #99 IT redo. |
| #187 leftover cells | Committed catalog **PASS**, live SoD **SKIP**, `g17_sod_close=NOT_CLAIMED`. `leftover_187_cells_honest=PASS`. |
| G2 SurfaceGuard still required | Admin still goes through `SurfaceGuard` / `PermissionEngine`. **PASS**. No admin-force bypass API. |
| G4 deny-on-chain | `appendNewTransaction` permission + guard-reject pair unchanged. **PASS**. Same-TX `append` untouched. |
| Product admin-force bypass API | Source scan: no `forceApprove` / `forceOverride` / `dualControl` / `makerChecker` product path. `no_admin_force_bypass_api=true`. |
| Live admin force HTTP | **PASS** only when live JAR + IdP + observed walk. Missing live path is honest **SKIP**. |
| Live dual-control (maker + checker) | **PASS** only when live JAR + IdP + observed two-person walk. Missing live path is honest **SKIP**. |
| Product dual-control UI | Still absent. **OPEN**. |
| P1 `ALL_DEMO` | Still stamps every duty. **OPEN**. |
| Product duty-assign UI | Still absent. **OPEN**. |

Missing live IdP / JAR is an honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Surface | Behavior |
|---|---|
| #99 catalog inventory | Three exclusive pairs + disjoint GRANT sets stay in `PlatformDutyCatalog`. Not a #99 IT redo. |
| #187 leftover cells | Live admin/auditor/operator HTTP stays SKIP on the committed artifact. Do not redo. |
| Admin force vs dual-control | Inventory: no product force-approve / force-override API that bypasses SurfaceGuard or maker-checker. Dual-control product path is absent. |
| Live walks | `live_admin_force_walk` / `live_dual_control_walk` are **PASS** only with reachable JAR + IdP + observed walk. |
| Honesty gate | `--verify-report` rejects fake live PASS, `g17_close=CLOSED` / `g17_sod_close=CLOSED` / `product_dual_control_close=CLOSED`, and #96/#99/#187 redo flags. Requires leftover rows kept (including #197 / #204 / #205 / #188 / #209 / #217 / #210 / #215 / #220). |

`admin` = `platform_system_admin`. Dual-control = maker-checker (two different people). This leftover does not start the JAR or IdP.

## How to run

```bash
./scripts/g17-separation-leftover-honesty.sh
./scripts/g17-separation-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-live-admin-force-pass.env fail
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-live-dual-control-pass.env fail
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-g17-close.env fail
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-dual-control-close.env fail
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-redo.env fail
```

#99 / #187 stay the sibling reproduction commands:

```bash
# do not run for this leftover
# ./scripts/g17-separation-honesty.sh
# mvn -pl nexus-permission-core,nexus-bootstrap -am test -Dtest=G17SeparationEngineTest,...
```

See [engineering/g17-separation-leftover-honesty-EVIDENCE.md](./engineering/g17-separation-leftover-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g17-separation-leftover-honesty/`. Walk JSON: [g17-separation-leftover-honesty/walk.json](./g17-separation-leftover-honesty/walk.json).

## What this does not claim

| Leftover | Why |
|---|---|
| Full G17 / 等保 / 密评 | Explicitly `g17_close=NOT_CLAIMED`, `dengbao_claimed=false`, `mipin_claimed=false`. |
| Product SoD close | Explicitly `g17_sod_close=NOT_CLAIMED`. |
| Product dual-control / maker-checker close | Explicitly `product_dual_control_close=NOT_CLAIMED`. Live walks stay SKIP here. |
| #99 catalog / assign / audit-admin ITs | Sibling leftover. `redo_99=false`. |
| #187 SoD leftover honesty | Sibling leftover. `redo_187=false`. |
| P1 tenant APIs on P2 duty roles | `ALL_DEMO` still grants every duty. |
| Product UI to assign duties or dual-control | Still absent. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |
| Product G2 (#204 / #188) / G4 / G9 (#197) / G17 MFA (#205) close | Sibling leftovers. Not rewritten. Not claimed. |
