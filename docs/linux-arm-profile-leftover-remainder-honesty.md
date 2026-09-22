# Linux / ARM Profile leftover remainder honesty beyond #236/#193/#157

S0+S1 remainder **after** the #236 platform-matrix remainder honesty leftover ([linux-arm-profile-remainder-honesty.md](./linux-arm-profile-remainder-honesty.md)), the #193 leftover honesty leftover ([linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md)), and the #157 honesty leftover ([linux-arm-profile-honesty.md](./linux-arm-profile-honesty.md)). Rebased onto `origin/main` `f53e062` (Merge #262 G10 file/object leftover honesty after #259 G9 leftover honesty remainder after #249 G17 SoD leftover after #240 / #254 / #236). **Product target Linux/ARM Profile is NOT accepted.** This leftover does **not** redo #105 collect/verify, #127 lifecycle, #157 honesty, #193 leftover honesty, or #236 remainder honesty product code. It does **not** redo #236. It does **not** redo #193. It does **not** redo #157. It does **not** rewrite P0-10 / M0-01～M0-12. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** boot a product JAR. **G18 stays closed.** `profile_acceptance=NOT_CLAIMED`.

#236 already inventories the remainder honesty gate beyond #193/#157 as always-green **PASS** and is explicitly **not** a live ARM/Linux product-JAR walk / product Profile close (`honesty_236_is_not_live_arm=true`). #193 already is the leftover honesty gate beyond #157. #157 already locked `TARGET-ARM-01` as `not-tested` and `supply-chain-evidence.csv` as `not-collected`. Those harness PASS cells are **not** a live ARM walk and **not** a product Profile close. Combining #236+#193+#157 harness PASS is **not** product Linux/ARM Profile (`combined_236_193_157_is_not_profile_close=true`). The leftover here is the **honesty gate beyond those three**: parent leftover harness stays **PASS**. A live ARM/Linux (or other non-x86) **product-JAR** walk is **PASS** only on a native non-x86 host **and** when that walk was observed. This agent VM is `uname -m=x86_64`. An x86 leftover note, QEMU user-mode, or Docker “ARM” is **not** a live ARM/Linux product-JAR `PASS`. Missing native ARM / non-x86 hardware is honest **SKIP**. It never fabricates `PASS`. `live_arm_linux_product_jar=SKIP`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [linux-arm-profile-honesty](./linux-arm-profile-honesty.md) (#157), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [linux-arm-profile-remainder-honesty](./linux-arm-profile-remainder-honesty.md) (#236), [g17-separation-leftover-honesty-remainder](./g17-separation-leftover-honesty-remainder.md) (#249), [g9-leftover-honesty-remainder](./g9-leftover-honesty-remainder.md) (#259), [g10-file-object-reconcile-leftover-honesty](./g10-file-object-reconcile-leftover-honesty.md) (#262), [g15-h5-pkce-miniprogram-remainder-honesty](./g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty](./g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [g4-hot-purge-staging-pkce-remainder-honesty](./g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty](./g10-file-object-reconcile-remainder-honesty.md) (#243), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g5-publishhook-ddl-remainder-honesty](./g5-publishhook-ddl-remainder-honesty.md) (#235), [g17-mfa-recovery-vue-otp-remainder-honesty](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), and every other leftover row already listed. Keep this file.

## What this leftover covers

| Path | Behavior |
|---|---|
| #157 / #193 / #236 leftover harness | Already on `main`. Parent `--verify-report` honest SKIP fixtures stay **PASS**. `leftover_157_harness=PASS`. `leftover_193_harness=PASS`. `leftover_236_harness=PASS`. Do **not** redo. |
| #236 remainder vs live | `honesty_236_is_not_live_arm=true`, `honesty_236_is_not_live_non_x86=true`, `honesty_236_is_not_profile_close=true`. |
| #193 / #157 harness vs live | `honesty_193_is_not_live_arm=true`, `honesty_157_is_not_live_arm=true`. |
| Combined harness vs product Profile | `combined_236_193_157_is_not_profile_close=true`. |
| Platform-matrix inventory | Read `poc/p0-10/deployment-matrix.csv`. All three rows stay inventoried. `TARGET-ARM-01` and `TARGET-X86-01` stay `not-tested`. |
| Supply-chain csv | Every component stays `not-collected`. Do not flip the board. |
| Live ARM/Linux product-JAR walk | `live_arm_linux_product_jar=SKIP` unless this host is native aarch64 **and** that walk was observed. This leftover does **not** boot a product JAR. |
| This-host x86 leftover | Quote committed #127 / #157 / #193 / #236 x86 artifacts. `x86_note_extrapolated=false`. QEMU / Docker is not ARM. |
| Sibling leftover rows | `docs/README.md` keeps #157 / #193 / #236 and this leftover, plus #262 G10 leftover / #259 G9 leftover remainder / #249 G17 SoD remainder / #240 G15 remainder / #254 G2 remainder and **ALL** other leftover rows. |
| Honesty gate | `--verify-report` rejects fake live ARM/Linux product-JAR PASS, fake non-x86 PASS, QEMU/Docker-as-ARM, x86-as-ARM, treating #236 as live ARM, treating combined harness as Profile close, `profile_acceptance=ACCEPTED` / `CLOSED`, and #105/#127/#157/#193/#236 redo flags. |

No Docker. No QEMU user-mode “ARM”. No `lifecycle.sh` / `collect-and-verify.sh` / `linux-arm-profile-honesty.sh` / `linux-arm-profile-leftover-honesty.sh` / `linux-arm-profile-remainder-honesty.sh` product re-run. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Product Linux/ARM Profile close | Explicitly `profile_acceptance=NOT_CLAIMED`. Honesty gate rejects `ACCEPTED` / `CLOSED`. Combining #236+#193+#157 harness PASS is not Profile close. |
| Live ARM/Linux product-JAR walk | This VM is `uname -m=x86_64`. Walk not observed. Honest SKIP. |
| Changing `TARGET-ARM-01` / `TARGET-X86-01` from `not-tested` | No approved Linux + CPU + JDK 21 + PostgreSQL 15.x delivery row. |
| QEMU / Docker as ARM | Forbidden. `qemu_is_not_arm=true`, `docker_is_not_arm=true`. |
| Redo of #105 / #127 / #157 / #193 / #236 | Those packages stay as committed. This leftover only reads them. |
| P0-10 / M0-01 redo | Inventory only. |
| G18 reopen / `AuditPort` | Closed / untouched. |

## How to run

```bash
./scripts/linux-arm-profile-leftover-remainder-honesty.sh
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report <out>/report.env
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-live-arm-jar-pass.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-live-non-x86-pass.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-qemu-as-arm.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-x86-as-arm.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-profile-close.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-redone.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-236-as-live.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-236-as-profile-close.env fail
```

#105 / #127 / #157 / #193 / #236 stay the sibling reproduction commands. This leftover does not invoke them as a product redo:

```bash
# do not run as this leftover
# ./scripts/linux-arm-profile/collect-and-verify.sh
# ./scripts/linux-arm-profile/lifecycle.sh
# ./scripts/linux-arm-profile-honesty.sh
# ./scripts/linux-arm-profile-leftover-honesty.sh
# ./scripts/linux-arm-profile-remainder-honesty.sh
```

Java **21** / Boot **4.1.1**. No Docker. No live PostgreSQL required. No product JAR boot.

See [engineering/linux-arm-profile-leftover-remainder-honesty-EVIDENCE.md](./engineering/linux-arm-profile-leftover-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/linux-arm-profile-leftover-remainder-honesty/`.

## Honest leftovers (not claimed)

- Freeze an approved Linux + **aarch64** (or other non-x86) CPU + JDK 21 + PostgreSQL 15.x delivery row
- Native ARM/Linux or non-x86 **product-JAR** walk on **that** row
- Native aarch64 install / upgrade / rollback / official offline media
- Product SBOM + license + CVE + official digest recorded in `supply-chain-evidence.csv`
- Changing `TARGET-ARM-01` / `TARGET-X86-01` from `not-tested`
- Product Profile acceptance
- Treating #236 / #193 / #157 harness PASS as a live ARM walk
- Redo of #105 / #127 / #157 / #193 / #236
