# G17 leftover — 三权分立 admin-force vs dual-control honesty remainder beyond #216/#187/#99

S0+S1 remainder **after** the #216 admin-force vs dual-control leftover honesty ([g17-separation-leftover-honesty.md](./g17-separation-leftover-honesty.md)), the #187 SoD leftover honesty ([g17-separation-honesty.md](./g17-separation-honesty.md)), and the #99 three-power slice ([g17-separation-of-duties.md](./g17-separation-of-duties.md)). Rebased onto `origin/main` `1d499ab` (Merge #254 G2 H5/form.write/report leftover honesty after #236 Linux/ARM remainder leftover honesty / #251 G4 hot-purge/staging/PKCE leftover honesty after #243 G10 file/object reconcile leftover honesty / #241 SecretPort rotate/getenv leftover honesty / #235 G5 PublishHook/DDL remainder leftover honesty / #233 G17 MFA recovery / Vue OTP remainder leftover honesty / #238 G1 FieldPolicy mask leftover honesty / #239 G7 expense/travel query leftover honesty / #237 G9 Redis-down/invalidate leftover honesty / #232 G2 H5 every-page / form.write leftover honesty). **G18 stays closed.** This leftover does **not** redo #96 `AdminMfaPolicy`, #99 `PlatformDutyCatalog` / assign / evaluate / audit-admin ITs, #187 leftover product code, or #216 leftover product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0 / M0. It does **not** claim 等保 / 密评 / 完整 G17 / product SoD / product dual-control (maker-checker) close. `g17_close=NOT_CLAIMED`. `g17_sod_close=NOT_CLAIMED`. `product_dual_control_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g17-separation-of-duties](./g17-separation-of-duties.md) (#99), [g17-separation-honesty](./g17-separation-honesty.md) (#187), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), this file, [g2-h5-every-page-form-write-report-remainder-honesty](./g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty](./linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty](./g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty](./g10-file-object-reconcile-remainder-honesty.md) (#243), [g17-mfa-recovery-vue-otp-remainder-honesty](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g5-publishhook-ddl-remainder-honesty](./g5-publishhook-ddl-remainder-honesty.md) (#235), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md) (#197), and the other leftover rows already on `main`. Keep this file.

#216 already inventories G2 SurfaceGuard / G4 deny-on-chain as **PASS** and live admin-force / dual-control as honest **SKIP**. Parent leftover `--verify-report` fixtures stay **PASS**. That #216 harness PASS is **not** a live admin-force / dual-control walk. The leftover here is the **honesty remainder beyond those three**: those cells stay honest; parent leftover honesty fixtures stay **PASS**. Live admin-force and live dual-control (maker-checker) are **PASS** only when a listener is present **and** the walk was observed. Missing `NEXUS_BASE_URL` / issued context / live IdP is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #99 catalog exclusive pairs / assign reject / `duty_conflict` | Always-green **PASS**. `leftover_99_cells_honest=PASS`. Do not redo. |
| #187 leftover cells | Committed catalog **PASS**, live SoD **SKIP**, `g17_sod_close=NOT_CLAIMED`. `leftover_187_cells_honest=PASS`. `leftover_187_honesty_gate=PASS`. Do not redo. |
| #216 leftover cells | Committed SurfaceGuard / G4 deny-on-chain **PASS**; live admin-force / dual-control **SKIP**. `leftover_216_cells_honest=PASS`. `leftover_216_honesty_gate=PASS`. `#216 harness PASS` is not a live walk. Do not redo. |
| G2 SurfaceGuard still required | Admin still goes through `SurfaceGuard` / `PermissionEngine`. **PASS**. No admin-force bypass API. |
| G4 deny-on-chain | `appendNewTransaction` permission + guard-reject pair unchanged. **PASS**. Same-TX `append` untouched. |
| Live admin force HTTP | `live_admin_force_walk=SKIP` unless live JAR + IdP + `live_admin_force_observed=true`. |
| Live dual-control (maker + checker) | `live_dual_control_walk=SKIP` unless live JAR + IdP + `live_dual_control_observed=true`. |
| Product dual-control UI / P1 `ALL_DEMO` / product duty-assign UI | Still **OPEN**. |
| Sibling leftover rows | `docs/README.md` keeps #99 / #187 / #216 and this leftover, plus #233 `g17-mfa-recovery-vue-otp-remainder-honesty`, #241 `s0-secret-rotate-getenv-leftover-honesty`, #235 `g5-publishhook-ddl-remainder-honesty`, #238 `g1-fieldpolicy-mask-second-auth-remainder-honesty`, #239 `g7-query-expense-travel-honesty-remainder`, #237 `g9-redis-down-invalidate-remainder-honesty`, #232 `g2-h5-every-page-form-write-remainder-honesty`, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g17_close=CLOSED` / `g17_sod_close=CLOSED` / `product_dual_control_close=CLOSED`, rejects #96/#99/#187/#216 redo flags, rejects treating #216 harness PASS as a live walk, and requires leftover rows kept. |

No Docker. No product-JAR start. `#99` / `#187` / `#216` stay the sibling reproduction commands. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Full G17 / 等保 / 密评 | Explicitly `g17_close=NOT_CLAIMED`, `dengbao_claimed=false`, `mipin_claimed=false`. |
| Product SoD close | Explicitly `g17_sod_close=NOT_CLAIMED`. |
| Product dual-control / maker-checker close | Explicitly `product_dual_control_close=NOT_CLAIMED`. |
| Live admin-force / dual-control walk | Honest SKIP on this agent VM. `#216 harness PASS` is not a live walk. |
| #96 / #99 / #187 / #216 | Sibling leftovers on `main`. `redo_99=false`. `redo_187=false`. `leftover_216_redone=false`. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

```bash
./scripts/g17-separation-leftover-honesty-remainder.sh
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report <out>/report.env
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-live-admin-force-pass.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-live-dual-control-pass.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-g17-close.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-dual-control-close.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-redo.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-harness-as-live.env fail
```

#99 / #187 / #216 stay the sibling reproduction commands. This leftover does **not** invoke them as collect:

```bash
# do not run for this leftover
# ./scripts/g17-separation-leftover-honesty.sh
# ./scripts/g17-separation-honesty.sh
```

Parent `--verify-report` on already-committed #216 / #187 honesty fixtures is harness evidence.

Java 21 / Boot **4.1.1**. Isolation-kernel compile path is in-process. No Docker. Live product-JAR / IdP walks are **not** required.

See [engineering/g17-separation-leftover-honesty-remainder-EVIDENCE.md](./engineering/g17-separation-leftover-honesty-remainder-EVIDENCE.md). Live `report.env` is gitignored under `.local/g17-separation-leftover-honesty-remainder/`. Walk JSON: [g17-separation-leftover-honesty-remainder/walk.json](./g17-separation-leftover-honesty-remainder/walk.json).

## Honest leftovers (not claimed)

- Live admin-force HTTP walk
- Live dual-control (maker + checker) walk
- Product SoD / dual-control / 等保 / 密评 / 完整 G17 close
- Redo of #96 / #99 / #187 / #216
